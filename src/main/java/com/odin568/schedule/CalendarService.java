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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CalendarService
{
    private static final Logger LOG = LoggerFactory.getLogger(CalendarService.class);

    private String icsUrl;

    private String icsContent;

    public CalendarService(@Value("${schedule.switchon.calendar.url:}") final String url)
    {
        this.icsUrl = url;
    }

    private boolean IsActivated() {
        return icsUrl != null && !icsUrl.isBlank();
    }

    public Optional<Event> GetActiveEvent() {
        return getEvents().stream().filter(Event::isActive).findFirst();
    }

    @Scheduled(cron = "0 0 1 * * *")
    private void ScheduledCalendarRefresh()
    {
        if (!IsActivated())
            return;

        LOG.debug("Started ScheduledCalendarRefresh");
        getIcsContent(true);
        LOG.debug("Finished ScheduledCalendarRefresh");
    }

    private List<Event> getEvents()
    {
        Optional<String> icsContent = getIcsContent(false);
        if (icsContent.isEmpty()) {
            return new ArrayList<>();
        }

        ICalendar iCal = Biweekly.parse(icsContent.get()).first();

        // Filter recurring events to the ones from interest
        LocalDateTime now = new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        Date startDateFilter = Date.from(now.minusDays(10).atZone(ZoneId.systemDefault()).toInstant());
        Date endDateFilter = Date.from(now.plusDays(10).atZone(ZoneId.systemDefault()).toInstant());

        Map<LocalDateTime, Event> sortedEventMap = new TreeMap<>();
        for (var event : iCal.getEvents()) {
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

    private synchronized Optional<String> getIcsContent(boolean forceRefresh)
    {
        if (!IsActivated()) {
            return Optional.empty();
        }
        if (forceRefresh || icsContent == null || icsContent.isBlank()) {
            try {
                icsContent = "";
                StringBuilder buffer = new StringBuilder();
                URL url = new URL(icsUrl);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String temp;
                    while ((temp = reader.readLine()) != null)
                        buffer.append(temp).append(System.lineSeparator());
                }
                icsContent = buffer.toString().trim();
            }
            catch (IOException e) {
                LOG.error("Unable to retrieve calendar", e);
            }
        }
        if (icsContent != null && !icsContent.isBlank())
            return Optional.of(icsContent);
        return Optional.empty();
    }
}
