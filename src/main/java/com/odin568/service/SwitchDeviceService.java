package com.odin568.service;

import com.github.kaklakariada.fritzbox.FritzBoxException;
import com.odin568.connection.FritzBoxSession;
import com.odin568.helper.SwitchState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
public class SwitchDeviceService implements HealthIndicator
{
    private static final Logger LOG = LoggerFactory.getLogger(SwitchDeviceService.class);
    private final String switchId;

    private final FritzBoxSession fritzBoxSession;

    @Autowired
    public SwitchDeviceService(final FritzBoxSession fritzBoxSession,
                               @Value("${fritzbox.switchid}") Long switchId)
    {
        this.fritzBoxSession = fritzBoxSession;
        this.switchId = String.valueOf(switchId);
    }

    @PostConstruct
    private void checkConfiguration() {
        if (switchId == null || switchId.isBlank()) {
            throw new IllegalStateException("Invalid SwitchId configured");
        }
    }

    public SwitchState GetSwitchPowerState()
    {
        try {
            fritzBoxSession.validateSwitchDevice(switchId);

            return fritzBoxSession.getDeviceState(switchId);
        }
        catch (Exception ex) {
            LOG.error("Failed getting power state", ex);
            throw ex;
        }
    }

    public SwitchState SwitchPowerState(final SwitchState targetState)
    {
        LOG.info("Started switching to mode " + targetState);

        try {
            fritzBoxSession.validateSwitchDevice(switchId);

            fritzBoxSession.switchDevice(switchId, targetState);

            // It can take some time until state is reflected properly
            Thread.sleep(1000);

            SwitchState newState = fritzBoxSession.getDeviceState(switchId);

            if (targetState != SwitchState.TOGGLE && newState != targetState)
                throw new FritzBoxException("Switching power state " + targetState + " failed");

            LOG.info("Finished switching to mode " + targetState);
            return newState;
        }
        catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        catch (RuntimeException ex) {
            LOG.error("Failed switching power state", ex);
            throw ex;
        }
    }

    @Override
    public Health health() {
        try {
            if (switchId.isBlank()) {
                throw new FritzBoxException("No SwitchId configured");
            }

            fritzBoxSession.validateSwitchDevice(switchId);

            return Health.up().withDetail("switchState", fritzBoxSession.getDeviceState(switchId).toString()).build();
        }
        catch (RuntimeException e) {
            LOG.error("Unable to determine health", e);
            return Health.down().withDetail("exception", e.getMessage()).build();
        }
    }
}
