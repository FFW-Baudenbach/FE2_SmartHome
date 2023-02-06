package com.odin568.schedule;

import com.odin568.helper.Event;
import com.odin568.helper.SwitchState;
import com.odin568.service.CalendarService;
import com.odin568.service.MotionDetectorService;
import com.odin568.service.SwitchDeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class AutoSwitchingService
{
    private static final Logger LOG = LoggerFactory.getLogger(AutoSwitchingService.class);

    private final long defaultSwitchOnMinutes;
    private final long defaultMotionMinutes;

    private final Pattern titleRegex;
    private final Pattern locationRegex;

    private LocalDateTime detectedSwitchOnTimestamp = null;

    private String switchOnEvent = null;

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private SwitchDeviceService switchDeviceService;

    @Autowired
    private MotionDetectorService motionDetectorService;

    public AutoSwitchingService(@Value("${schedule.switchoff.defaultSwitchOnMinutes:60}") long defaultSwitchOnMinutes,
                                @Value("${schedule.switchoff.defaultMotionMinutes:10}") long defaultMotionMinutes,
                                @Value("${schedule.switchon.calendar.titleRegex:.*}") final String titleRegex,
                                @Value("${schedule.switchon.calendar.locationRegex:.*}") final String locationRegex)
    {
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

        try {
            StartScheduledSwitchOn(activeCalendarEvent);
            StartScheduledSwitchOff(activeCalendarEvent);
        }
        catch (Exception e) {
            LOG.error("Unhandled exception", e);
        }

        LOG.debug("Finished ScheduledSwitching");
    }

    private void StartScheduledSwitchOn(Optional<Event> activeCalendarEvent)
    {
        LOG.debug("Started ScheduledSwitchOn");

        if (activeCalendarEvent.isEmpty()) {
            switchOnEvent = null;
            LOG.debug("No active calendar event");
            return;
        }
        if (activeCalendarEvent.get().toString().equals(switchOnEvent)) {
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
            if (switchDeviceService.GetSwitchPowerState() == SwitchState.ON) {
                LOG.info("No scheduled switch on necessary as switch is already turned on for starting calendar event: " + activeCalendarEvent.get());
            }
            else {
                LOG.info("Switching on device due to starting calendar event: " + activeCalendarEvent.get());
                switchDeviceService.SwitchPowerState(SwitchState.ON);
            }

            // Save event to avoid situation that switch is re-started immediately if manually turned off during event
            switchOnEvent = activeCalendarEvent.get().toString();
            // Simulate detection of switch on so that it will be shut off at the end-date of the event
            detectedSwitchOnTimestamp = activeCalendarEvent.get().getEndDate().minusMinutes(defaultSwitchOnMinutes);
        }
        catch (RuntimeException ex) {
            LOG.error("Failed on ScheduledSwitchOn", ex);
        }

        LOG.debug("Finished ScheduledSwitchOn");
    }
    private void StartScheduledSwitchOff(Optional<Event> activeCalendarEvent)
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
            if (switchDeviceService.GetSwitchPowerState() == SwitchState.OFF) {
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
            Optional<LocalDateTime> lastMotionDetected = motionDetectorService.getLastMotionFromMotionDetectors();
            if (lastMotionDetected.isPresent() && defaultMotionMinutes > 0) {
                // Ensure switch is turned off only when no motion for at least {defaultSwitchOnMinutes} minutes
                if (lastMotionDetected.get().isBefore(LocalDateTime.now().minusMinutes(defaultMotionMinutes))) {
                    LOG.info("Switching device off as no motion was detected for {} minutes", defaultMotionMinutes);
                    switchDeviceService.SwitchPowerState(SwitchState.OFF);
                }
            }
            else {
                // Otherwise, we reached the limit, switch off
                LOG.info("Switching device off because it is switched on since {} minutes", defaultSwitchOnMinutes);
                switchDeviceService.SwitchPowerState(SwitchState.OFF);
            }
        }
        catch (RuntimeException ex) {
            LOG.error("Failed on ScheduledSwitchOff", ex);
        }

        LOG.debug("Finished ScheduledSwitchOff");
    }

}
