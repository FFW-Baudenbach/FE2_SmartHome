package com.odin568.connection;

import com.github.kaklakariada.fritzbox.FritzBoxException;
import com.github.kaklakariada.fritzbox.HomeAutomation;
import com.odin568.helper.SwitchState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

@Component
public class FritzBoxSession
{
    private static final Logger LOG = LoggerFactory.getLogger(FritzBoxSession.class);
    private final String url;
    private final String username;
    private final String password;

    private HomeAutomation cachedHomeAutomation;

    public FritzBoxSession(@Value("${fritzbox.url}") String url,
                           @Value("${fritzbox.username}") String username,
                           @Value("${fritzbox.password}") String password)
    {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @PostConstruct
    private void checkConfiguration() {
        if (!url.matches("^http(s)?://.*"))
            throw new IllegalArgumentException("Invalid FritzBox url. Needs to start with protocol (http, https)");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Invalid FritzBox username");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Invalid FritzBox password");
        }
        connect();
    }

    private synchronized void connect()
    {
        if (cachedHomeAutomation == null) {
            try {
                LOG.debug("Logging in...");
                cachedHomeAutomation = HomeAutomation.connect(url, username, password);
            }
            catch (Exception ex) {
                LOG.error("Unable to create connection", ex);
            }
        }
    }

    @Scheduled(fixedDelay = 10, initialDelay = 10, timeUnit = TimeUnit.MINUTES)
    private synchronized void reconnect() {
        if (cachedHomeAutomation != null) {
            try {
                LOG.debug("Logging out...");
                cachedHomeAutomation.logout();
            }
            catch (Exception ex) {
                LOG.error("Unable to close connection", ex);
            }
            cachedHomeAutomation = null;
        }
        connect();
    }

    public synchronized void validateSwitchDevice(final String switchId) {
        if (!cachedHomeAutomation.getSwitchList().contains(switchId)) {
            throw new FritzBoxException("Switch not found");
        }
        if (!cachedHomeAutomation.getSwitchPresent(switchId)) {
            throw new FritzBoxException("Switch currently not present");
        }
    }

    public synchronized void switchDevice(final String switchId, final SwitchState targetState)
    {
        switch(targetState) {
            case ON -> cachedHomeAutomation.switchPowerState(switchId, true);
            case OFF -> cachedHomeAutomation.switchPowerState(switchId, false);
            case TOGGLE -> cachedHomeAutomation.togglePowerState(switchId);
        }
    }

    public synchronized SwitchState getDeviceState(final String switchId)
    {
        return cachedHomeAutomation.getSwitchState(switchId) ? SwitchState.ON : SwitchState.OFF;
    }

    /**
     * Try to find any motion detector. If found, return the latest motion detected time.
     * @return LocalDateTime with last motion detected or empty
     */
    public synchronized Optional<LocalDateTime> getLastMotionFromMotionDetectors()
    {
        long lastMotionDetected = 0;
        for(var device : cachedHomeAutomation.getDeviceListInfos().getDevices()) {
            if (!device.isPresent() || device.getEtsiUnitInfo() == null || device.getAlert() == null) {
                continue;
            }
            if (device.getEtsiUnitInfo().getUnittype() == 515) {
                lastMotionDetected = Math.max(lastMotionDetected, device.getAlert().getLastAlertChgTimestamp());
            }
        }
        if (lastMotionDetected > 0) {
            return Optional.of(
                    LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(lastMotionDetected * 1000), TimeZone.getDefault().toZoneId()));
        }
        return Optional.empty();
    }
}
