package com.odin568.schedule;

import com.github.kaklakariada.fritzbox.FritzBoxException;
import com.github.kaklakariada.fritzbox.HomeAutomation;
import com.odin568.helper.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AutoSwitchingService
{
    private static final Logger LOG = LoggerFactory.getLogger(AutoSwitchingService.class);

    private final String url;
    private final String username;
    private final String password;
    private final String switchId;
    private final long defaultSwitchOnMinutes;
    private final long defaultMotionMinutes;

    private final Pattern titleRegex;

    private LocalDateTime detectedSwitchOnTimestamp = null;

    @Autowired
    private CalendarService calendarService;

    public AutoSwitchingService(@Value("${fritzbox.url}") String url,
                                @Value("${fritzbox.username}") String username,
                                @Value("${fritzbox.password}") String password,
                                @Value("${fritzbox.switchid}") Long switchId,
                                @Value("${schedule.switchoff.defaultSwitchOnMinutes:60}") long defaultSwitchOnMinutes,
                                @Value("${schedule.switchoff.defaultMotionMinutes:10}") long defaultMotionMinutes,
                                @Value("${schedule.switchon.calendar.titleRegex:.*}") final String titleRegex)
    {
        this.url = url;
        this.username = username;
        this.password = password;
        this.switchId = String.valueOf(switchId);
        this.defaultSwitchOnMinutes = defaultSwitchOnMinutes;
        this.titleRegex = Pattern.compile(titleRegex, Pattern.CASE_INSENSITIVE);
        if (defaultSwitchOnMinutes <= 0) {
            throw new IllegalArgumentException("defaultSwitchOnMinutes is negative");
        }
        this.defaultMotionMinutes = defaultMotionMinutes;
        if (defaultMotionMinutes <= 0) {
            throw new IllegalArgumentException("defaultMotionMinutes is negative");
        }
    }

    @Scheduled(fixedDelayString = "${schedule.fixedDelayMinutes:}", timeUnit = TimeUnit.MINUTES)
    private void ScheduledSwitching()
    {
        LOG.debug("Started ScheduledSwitching");

        Optional<Event> activeCalendarEvent = calendarService.GetActiveEvent();
        HomeAutomation homeAutomation = null;
        try {
            homeAutomation = HomeAutomation.connect(url, username, password);
            validateSwitchDevice(homeAutomation);

            StartScheduledSwitchOn(homeAutomation, activeCalendarEvent);
            StartScheduledSwitchOff(homeAutomation, activeCalendarEvent);
        }
        catch (Exception e) {
            LOG.error("Unhandled exception", e);
        }
        finally {
            if (homeAutomation != null)
                homeAutomation.logout();
        }

        LOG.debug("Finished ScheduledSwitching");
    }

    private void StartScheduledSwitchOn(final HomeAutomation homeAutomation, Optional<Event> activeCalendarEvent)
    {
        LOG.debug("Started ScheduledSwitchOn");

        if (activeCalendarEvent.isEmpty()) {
            LOG.debug("No active calendar event");
            return;
        }

        // Check if event title
        if (titleRegex.matcher(activeCalendarEvent.get().getSummary()).matches()) {
            LOG.debug("Active event should not trigger switch on: " + activeCalendarEvent.get().getSummary());
            return;
        }

        try {
            // If switch is already on - nothing to do
            if (homeAutomation.getSwitchState(switchId)) {
                LOG.debug("No scheduled switch on necessary as switch is already turned on");
                return;
            }
            detectedSwitchOnTimestamp = LocalDateTime.now();
            homeAutomation.switchPowerState(switchId, true);
            LOG.info("Switching on device due to active calendar event: " + activeCalendarEvent.get());
        }
        catch (RuntimeException ex) {
            LOG.error("Failed on ScheduledSwitchOn", ex);
        }

        LOG.debug("Finished ScheduledSwitchOn");
    }
    private void StartScheduledSwitchOff(HomeAutomation homeAutomation, Optional<Event> activeCalendarEvent)
    {
        LOG.debug("Started ScheduledSwitchOff");

        // Wait the minimum time after
        if (detectedSwitchOnTimestamp != null) {
            LocalDateTime calculatedSwitchOffTimestamp = detectedSwitchOnTimestamp.plusMinutes(defaultSwitchOnMinutes);
            if (LocalDateTime.now().isBefore(calculatedSwitchOffTimestamp)) {
                LOG.debug("Skipping because did not reach calculated switch off time yet");
                return;
            }
        }

        if (activeCalendarEvent.isPresent()) {
            LOG.debug("Skipping because there is an active calendar event");
            return;
        }

        try {
            // If switch is already off - reset for next round
            if (!homeAutomation.getSwitchState(switchId)) {
                detectedSwitchOnTimestamp = null;
                LOG.debug("Switch already turned off");
                return;
            }

            // First 'on' detected, save timestamp for next round
            if (detectedSwitchOnTimestamp == null) {
                LOG.info("Detected switch is turned on");
                detectedSwitchOnTimestamp = LocalDateTime.now();
                return;
            }

            // Try to get last motion detection if available
            Optional<LocalDateTime> lastMotionDetected = getLastMotionFromMotionDetectors(homeAutomation);
            if (lastMotionDetected.isPresent()) {
                // Ensure switch is turned off only when no motion for at least {defaultSwitchOnMinutes} minutes
                if (lastMotionDetected.get().isBefore(LocalDateTime.now().minusMinutes(defaultMotionMinutes))) {
                    LOG.info("Switching device off as no motion was detected for {} minutes", defaultMotionMinutes);
                    homeAutomation.switchPowerState(switchId, false);
                }
            }
            else {
                // Otherwise, we reached the limit, switch off
                LOG.info("Switching device off because it is switched on since {} minutes", defaultSwitchOnMinutes);
                homeAutomation.switchPowerState(switchId, false);
            }
        }
        catch (RuntimeException ex) {
            LOG.error("Failed on ScheduledSwitchOff", ex);
        }

        LOG.debug("Finished ScheduledSwitchOff");
    }




    /**
     * Try to find any motion detector. If found, return the latest motion detecte time
     * @param homeAutomation the fritzbox api object
     * @return LocalDateTime with last motion detected or empty
     */
    private Optional<LocalDateTime> getLastMotionFromMotionDetectors(final HomeAutomation homeAutomation) {
        long lastMotionDetected = 0;
        for(var device : homeAutomation.getDeviceListInfos().getDevices()) {
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

    private void validateSwitchDevice(final HomeAutomation homeAutomation) {
        if (!homeAutomation.getSwitchList().contains(switchId)) {
            throw new FritzBoxException("Switch not found");
        }
        if (!homeAutomation.getSwitchPresent(switchId)) {
            throw new FritzBoxException("Switch currently not present");
        }
    }

}
