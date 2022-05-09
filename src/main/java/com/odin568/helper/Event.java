package com.odin568.helper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class Event
{
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String summary;

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public boolean isActive() {
        var now = LocalDateTime.now();
        // Add some minutes before/after to compensate timings and monitor warmup, etc.
        return now.isAfter(startDate.minusMinutes(5)) && now.isBefore(endDate.plusMinutes(5));
    }

    @Override
    public String toString() {
        return startDate.toString() + " - " + endDate.toString() + ": " + summary;
    }

    public static LocalDateTime convertToLocalDateTime(Date dateToConvert) {
        return dateToConvert.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
