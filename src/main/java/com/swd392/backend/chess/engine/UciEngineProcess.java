package com.swd392.backend.chess.engine;

import com.swd392.backend.chess.model.EngineSettings;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class UciEngineProcess implements AutoCloseable {

    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SEARCH_GRACE = Duration.ofSeconds(5);
    private static final Duration QUIT_TIMEOUT = Duration.ofSeconds(1);
    private static final String NO_MOVE = "(none)";
    private static final String BEST_MOVE_PREFIX = "bestmove";

    private final Process process;
    private final BufferedWriter input;
    private final BlockingQueue<String> output = new LinkedBlockingQueue<>();

    private UciEngineProcess(Process process) {
        this.process = process;
        this.input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.US_ASCII));
        Thread reader = new Thread(this::readOutput, "uci-engine-reader-" + process.pid());
        reader.setDaemon(true);
        reader.start();
    }

    public static UciEngineProcess start(Path executable) {
        Process process;
        try {
            process = new ProcessBuilder(executable.toString())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException exception) {
            throw new EngineFailureException("Unable to start engine process", exception);
        }
        UciEngineProcess engine = new UciEngineProcess(process);
        try {
            engine.send("uci");
            engine.awaitLineStartingWith("uciok", HANDSHAKE_TIMEOUT);
            engine.send("setoption name Threads value 1");
            engine.awaitReady();
        } catch (RuntimeException exception) {
            engine.close();
            throw exception;
        }

        return engine;
    }

    public String findBestMove(List<String> uciMoves, EngineSettings settings) {
        output.clear();
        send("ucinewgame");
        send("setoption name Skill Level value " + settings.getSkillLevel());
        awaitReady();
        send(positionCommand(uciMoves));
        send("go movetime " + settings.getMoveTime().toMillis());
        String bestMoveLine =
                awaitLineStartingWith(BEST_MOVE_PREFIX, settings.getMoveTime().plus(SEARCH_GRACE));
        String[] tokens = bestMoveLine.split("\\s+");
        boolean noMove = tokens.length < 2 || NO_MOVE.equals(tokens[1]);
        if (noMove) {
            throw new EngineFailureException("Engine returned no move for an active position");
        }

        return tokens[1];
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public void close() {
        boolean quitSent = process.isAlive() && trySend("quit");
        try {
            if (quitSent) {
                process.waitFor(QUIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            process.destroyForcibly();
        }
    }

    private String positionCommand(List<String> uciMoves) {
        if (uciMoves.isEmpty()) {
            return "position startpos";
        }

        return "position startpos moves " + String.join(" ", uciMoves);
    }

    private void awaitReady() {
        send("isready");
        awaitLineStartingWith("readyok", HANDSHAKE_TIMEOUT);
    }

    private void send(String command) {
        if (!trySend(command)) {
            throw new EngineFailureException("Unable to write to engine process");
        }
    }

    private boolean trySend(String command) {
        try {
            input.write(command);
            input.newLine();
            input.flush();

            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private String awaitLineStartingWith(String prefix, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new EngineFailureException("Timed out waiting for engine response: " + prefix);
            }
            String line = pollLine(remaining);
            if (line != null && line.startsWith(prefix)) {
                return line;
            }
            if (line == null && !process.isAlive()) {
                throw new EngineFailureException("Engine process exited unexpectedly");
            }
        }
    }

    private String pollLine(long timeoutNanos) {
        try {
            return output.poll(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EngineFailureException("Interrupted while waiting for engine", exception);
        }
    }

    private void readOutput() {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.US_ASCII))) {
            reader.lines().forEach(output::offer);
        } catch (IOException | UncheckedIOException exception) {
            process.destroyForcibly();
        }
    }
}
