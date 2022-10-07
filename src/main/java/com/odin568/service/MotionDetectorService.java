package com.odin568.service;

import com.odin568.connection.FritzBoxSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class MotionDetectorService implements HealthIndicator {

    private final FritzBoxSession fritzBoxSession;

    @Autowired
    public MotionDetectorService(final FritzBoxSession fritzBoxSession)
    {
        this.fritzBoxSession = fritzBoxSession;
    }

    public Optional<LocalDateTime> getLastMotionFromMotionDetectors()
    {
        return fritzBoxSession.getLastMotionFromMotionDetectors();
    }

    @Override
    public Health health() {
        var entry = getLastMotionFromMotionDetectors();
        return Health.up().withDetail("lastMotionDetected", entry.map(LocalDateTime::toString).orElse("none")).build();
    }
}
