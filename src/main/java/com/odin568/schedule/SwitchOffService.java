package com.odin568.schedule;

import com.github.kaklakariada.fritzbox.FritzBoxException;
import com.github.kaklakariada.fritzbox.HomeAutomation;
import com.odin568.service.SwitchDeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

@Service
public class SwitchOffService
{
    private static final Logger LOG = LoggerFactory.getLogger(SwitchOffService.class);

    private final String url;
    private final String username;
    private final String password;
    private final String switchId;
    private final long defaultSwitchOnMinutes;
    private LocalDateTime detectedSwitchOnTimestamp = null;

    public SwitchOffService(@Value("${fritzbox.url}") String url,
                               @Value("${fritzbox.username}") String username,
                               @Value("${fritzbox.password}") String password,
                               @Value("${fritzbox.switchid}") Long switchId,
                               @Value("${schedule.switchoff.defaultSwitchOnMinutes:30}") long defaultSwitchOnMinutes)
    {
        this.url = url;
        this.username = username;
        this.password = password;
        this.switchId = String.valueOf(switchId);
        this.defaultSwitchOnMinutes = defaultSwitchOnMinutes;
        if (defaultSwitchOnMinutes <= 0) {
            throw new IllegalArgumentException("defaultSwitchOnMinutes is negative");
        }
    }

    @Scheduled(fixedDelayString = "${schedule.switchoff.fixedDelayMinutes:}", timeUnit = TimeUnit.MINUTES)
    private void ScheduledSwitchOff()
    {
        LOG.debug("Started ScheduledSwitchOff");

        // Wait the minimum time after
        if (detectedSwitchOnTimestamp != null) {
            LocalDateTime calculatedSwitchOffTimestamp = detectedSwitchOnTimestamp.plusMinutes(defaultSwitchOnMinutes);
            if (LocalDateTime.now().isBefore(calculatedSwitchOffTimestamp)) {
                LOG.info("Did not reach calculated switch off time yet");
                return;
            }
        }

        HomeAutomation homeAutomation = null;
        try {
            homeAutomation = HomeAutomation.connect(url, username, password);
            validateSwitchDevice(homeAutomation);

            // Grab current switch state.
            SwitchDeviceService.SwitchState currentState = homeAutomation.getSwitchState(switchId) ? SwitchDeviceService.SwitchState.ON : SwitchDeviceService.SwitchState.OFF;

            // Already off - reset for next round
            if (currentState == SwitchDeviceService.SwitchState.OFF) {
                detectedSwitchOnTimestamp = null;
                LOG.info("No scheduled switch off necessary as switch is already turned off");
                return;
            }

            // First 'on' detected, save it for next round
            if (detectedSwitchOnTimestamp == null) {
                LOG.info("Detected switch is turned on");
                detectedSwitchOnTimestamp = LocalDateTime.now();
                return;
            }

            // Get last motion detection
            long lastMotionDetected = 0;
            for(var device : homeAutomation.getDeviceListInfos().getDevices()) {
                if (device.getEtsiUnitInfo() == null || device.getAlert() == null) {
                    continue;
                }
                if (device.getEtsiUnitInfo().getUnittype() == 515) {
                    lastMotionDetected = Math.max(lastMotionDetected, device.getAlert().getLastAlertChgTimestamp());
                }
            }

            // Ensure switch is turned out only when no motion for at least {defaultSwitchOnMinutes} minutes
            LocalDateTime lastMotionDetectedTimestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastMotionDetected * 1000), TimeZone.getDefault().toZoneId());
            if (lastMotionDetectedTimestamp.isBefore(LocalDateTime.now().minusMinutes(defaultSwitchOnMinutes))) {
                LOG.info("Switching device off as no motion was detected for {} minutes", defaultSwitchOnMinutes);
                homeAutomation.switchPowerState(switchId, false);
            }
        }
        catch (RuntimeException ex) {
            LOG.error("Failed on ScheduledSwitchOff", ex);
            throw ex;
        }
        finally {
            if (homeAutomation != null)
                homeAutomation.logout();
        }

        LOG.debug("Finished ScheduledSwitchOff");
    }

    private void validateSwitchDevice(final HomeAutomation homeAutomation) {
        if (!homeAutomation.getSwitchList().contains(switchId)) {
            throw new FritzBoxException("Switch not found");
        }
        if (!homeAutomation.getSwitchPresent(switchId)) {
            throw new FritzBoxException("Switch currently not present");
        }
    }
}
