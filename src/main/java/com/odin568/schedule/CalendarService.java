package com.odin568.schedule;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
import com.odin568.helper.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CalendarService
{
    private static final Logger LOG = LoggerFactory.getLogger(CalendarService.class);
    private final String icsUrl;
    private ICalendar cachedCalendar;
    private boolean forceUpdateOfCalendarOnNextRun = false;

    public CalendarService(@Value("${schedule.switchon.calendar.url:}") final String url)
    {
        this.icsUrl = url;
    }

    private boolean IsActivated() {
        return icsUrl != null && !icsUrl.isBlank();
    }

    public Optional<Event> GetActiveEvent() {
        return getEvents()
                .stream()
                .filter(Event::isActive)
                .findFirst();
    }

    @Scheduled(cron = "0 0 */6 * * *")
    private void ScheduledCalendarRefresh()
    {
        LOG.debug("Started ScheduledCalendarRefresh");
        forceUpdateOfCalendarOnNextRun = true;
        LOG.debug("Finished ScheduledCalendarRefresh");
    }

    private List<Event> getEvents()
    {
        Optional<ICalendar> icsContent = getCalendar();
        if (icsContent.isEmpty()) {
            return new ArrayList<>();
        }

        // Filter recurring events to the ones from interest
        LocalDateTime now = new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        Date startDateFilter = Date.from(now.minusDays(1).atZone(ZoneId.systemDefault()).toInstant());
        Date endDateFilter = Date.from(now.plusDays(1).atZone(ZoneId.systemDefault()).toInstant());

        Map<LocalDateTime, Event> sortedEventMap = new TreeMap<>();
        for (var event : icsContent.get().getEvents()) {
            DateIterator it = event.getDateIterator(TimeZone.getDefault());
            while (it.hasNext())
            {
                Date baseDate = it.next();
                if (baseDate.after(endDateFilter)) {
                    break;
                }
                if (baseDate.before(startDateFilter)) {
                    continue;
                }
                long duration = ChronoUnit.MINUTES
                        .between(Event.convertToLocalDateTime(event.getDateStart().getValue()),
                                Event.convertToLocalDateTime(event.getDateEnd().getValue()));

                Event newEvent = new Event();
                newEvent.setStartDate(Event.convertToLocalDateTime(baseDate));
                newEvent.setEndDate(newEvent.getStartDate().plusMinutes(duration));
                newEvent.setSummary(event.getSummary().getValue());

                sortedEventMap.put(newEvent.getStartDate(), newEvent);
            }
        }
        return sortedEventMap.values().stream().toList();
    }

    private synchronized Optional<ICalendar> getCalendar()
    {
        if (!IsActivated()) {
            return Optional.empty();
        }
        if (forceUpdateOfCalendarOnNextRun || cachedCalendar == null) {
            try {
                LOG.debug("Started updating calendar ics");
                StringBuilder buffer = new StringBuilder();
                URL url = new URL(icsUrl);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String temp;
                    while ((temp = reader.readLine()) != null) {
                        buffer
                            .append(temp)
                            .append(System.lineSeparator());
                    }
                }
                cachedCalendar = Biweekly.parse(buffer.toString().trim()).first();
                forceUpdateOfCalendarOnNextRun = false;
                LOG.debug("Finished updating calendar ics");
            }
            catch (Exception e) {
                LOG.error("Unable to retrieve calendar ics", e);
            }
        }

        // If updating failed, old state might be reused.
        if (cachedCalendar != null)
            return Optional.of(cachedCalendar);

        return Optional.empty();
    }
}
