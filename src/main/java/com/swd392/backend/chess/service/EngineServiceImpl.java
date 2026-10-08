package com.swd392.backend.chess.service;

import com.swd392.backend.chess.engine.EngineFailureException;
import com.swd392.backend.chess.engine.UciEngineProcess;
import com.swd392.backend.chess.model.EngineSettings;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.config.EngineProperties;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EngineServiceImpl implements EngineService {

    private final EngineProperties properties;
    private final Semaphore permits;
    private final ConcurrentLinkedQueue<UciEngineProcess> idleEngines = new ConcurrentLinkedQueue<>();

    public EngineServiceImpl(EngineProperties properties) {
        this.properties = properties;
        this.permits = new Semaphore(properties.poolSize(), true);
    }

    @Override
    public String findBestMove(List<String> uciMoves, EngineSettings settings) {
        acquirePermit();
        try {
            return searchWithPooledEngine(uciMoves, settings);
        } finally {
            permits.release();
        }
    }

    @PreDestroy
    public void shutdown() {
        UciEngineProcess engine = idleEngines.poll();
        while (engine != null) {
            engine.close();
            engine = idleEngines.poll();
        }
    }

    private String searchWithPooledEngine(List<String> uciMoves, EngineSettings settings) {
        UciEngineProcess engine = borrowEngine();
        try {
            String bestMove = engine.findBestMove(uciMoves, settings);
            idleEngines.offer(engine);

            return bestMove;
        } catch (EngineFailureException exception) {
            engine.close();
            throw new AppException(AppErrorMessage.ENGINE_UNAVAILABLE, exception);
        }
    }

    private UciEngineProcess borrowEngine() {
        UciEngineProcess engine = idleEngines.poll();
        while (engine != null && !engine.isAlive()) {
            engine.close();
            engine = idleEngines.poll();
        }
        if (engine != null) {
            return engine;
        }

        return startEngine();
    }

    private UciEngineProcess startEngine() {
        Path executable = resolveExecutable();
        try {
            UciEngineProcess engine = UciEngineProcess.start(executable);
            log.info("[ENGINE] - ACTION: startEngine: executable: {}", executable.getFileName());

            return engine;
        } catch (EngineFailureException exception) {
            throw new AppException(AppErrorMessage.ENGINE_UNAVAILABLE, exception);
        }
    }

    private Path resolveExecutable() {
        String configuredPath = properties.stockfishPath();
        boolean pathMissing = configuredPath == null || configuredPath.isBlank();
        if (pathMissing) {
            throw new AppException(
                    AppErrorMessage.ENGINE_UNAVAILABLE,
                    new EngineFailureException("app.engine.stockfish-path is not set"));
        }
        Path executable = Path.of(configuredPath);
        if (!Files.isRegularFile(executable)) {
            throw new AppException(
                    AppErrorMessage.ENGINE_UNAVAILABLE,
                    new EngineFailureException("Engine executable not found: " + executable));
        }

        return executable;
    }

    private void acquirePermit() {
        try {
            boolean acquired = permits.tryAcquire(properties.acquireTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new AppException(
                        AppErrorMessage.ENGINE_UNAVAILABLE,
                        new EngineFailureException("All engine instances are busy"));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException(AppErrorMessage.ENGINE_UNAVAILABLE, exception);
        }
    }
}
