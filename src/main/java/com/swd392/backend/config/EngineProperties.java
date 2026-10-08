package com.swd392.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.engine")
public record EngineProperties(String stockfishPath, int poolSize, Duration acquireTimeout) {}
