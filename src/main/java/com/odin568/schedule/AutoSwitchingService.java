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
    private final Pattern locationRegex;

    private LocalDateTime detectedSwitchOnTimestamp = null;

    private Event switchOnEvent = null;

    @Autowired
    private CalendarService calendarService;

    public AutoSwitchingService(@Value("${fritzbox.url}") String url,
                                @Value("${fritzbox.username}") String username,
                                @Value("${fritzbox.password}") String password,
                                @Value("${fritzbox.switchid}") Long switchId,
                                @Value("${schedule.switchoff.defaultSwitchOnMinutes:60}") long defaultSwitchOnMinutes,
                                @Value("${schedule.switchoff.defaultMotionMinutes:10}") long defaultMotionMinutes,
                                @Value("${schedule.switchon.calendar.titleRegex:.*}") final String titleRegex,
                                @Value("${schedule.switchon.calendar.locationRegex:.*}") final String locationRegex)
    {
        this.url = url;
        this.username = username;
        this.password = password;
        this.switchId = String.valueOf(switchId);
        this.defaultSwitchOnMinutes = defaultSwitchOnMinutes;
        this.defaultMotionMinutes = defaultMotionMinutes;
        this.titleRegex = Pattern.compile(titleRegex, Pattern.CASE_INSENSITIVE);
        this.locationRegex = Pattern.compile(locationRegex, Pattern.CASE_INSENSITIVE);

        if (defaultSwitchOnMinutes < 0) {
            throw new IllegalArgumentException("defaultSwitchOnMinutes is negative");
        }
        if (defaultMotionMinutes < 0) {
            throw new IllegalArgumentException("defaultMotionMinutes is negative");
        }
    }

    @Scheduled(initialDelayString = "${schedule.initialDelayMinutes:${schedule.fixedDelayMinutes:}}", fixedDelayString = "${schedule.fixedDelayMinutes:}", timeUnit = TimeUnit.MINUTES)
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
            switchOnEvent = null;
            LOG.debug("No active calendar event");
            return;
        }
        if (activeCalendarEvent.get() == switchOnEvent) {
            LOG.debug("This event already triggered a switch on");
            return;
        }

        // Check event title
        boolean matchingTitle = titleRegex.matcher(activeCalendarEvent.get().getSummary()).find();
        boolean matchingLocation = locationRegex.matcher(activeCalendarEvent.get().getLocation()).find();
        if (!matchingTitle && !matchingLocation) {
            LOG.debug("Found event does not fulfill criteria for switching on: " + activeCalendarEvent);
            return;
        }

        try {
            if (homeAutomation.getSwitchState(switchId)) {
                LOG.info("No scheduled switch on necessary as switch is already turned on for starting calendar event: " + activeCalendarEvent.get());
            }
            else {
                LOG.info("Switching on device due to starting calendar event: " + activeCalendarEvent.get());
                homeAutomation.switchPowerState(switchId, true);
            }

            // Save event to avoid situation that switch is re-started immediately if manually turned off during event
            switchOnEvent = activeCalendarEvent.get();
            // Simulate detection of switch on so that it will be shut off at the end-date of the event
            detectedSwitchOnTimestamp = activeCalendarEvent.get().getEndDate().minusMinutes(defaultSwitchOnMinutes);
        }
        catch (RuntimeException ex) {
            LOG.error("Failed on ScheduledSwitchOn", ex);
        }

        LOG.debug("Finished ScheduledSwitchOn");
    }
    private void StartScheduledSwitchOff(HomeAutomation homeAutomation, Optional<Event> activeCalendarEvent)
    {
        LOG.debug("Started ScheduledSwitchOff");

        // Do not switch off during active calendar event
        if (activeCalendarEvent.isPresent()) {
            LOG.debug("Skipping because there is an active calendar event");
            return;
        }

        // If switch on was detected previously
        if (detectedSwitchOnTimestamp != null) {
            LocalDateTime calculatedSwitchOffTimestamp = detectedSwitchOnTimestamp.plusMinutes(defaultSwitchOnMinutes);
            if (LocalDateTime.now().isBefore(calculatedSwitchOffTimestamp)) {
                LOG.debug("Skipping because did not reach calculated switch off time yet");
                return;
            }
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
    private Optional<LocalDateTime> getLastMotionFromMotionDetectors(final HomeAutomation homeAutomation)
    {
        if (defaultMotionMinutes == 0) {
            return Optional.empty();
        }

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
