package com.odin568.helper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class Event
{
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String summary;

    private String location;

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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isActive() {
        var now = LocalDateTime.now();
        // Add some minutes before/after to compensate timings and monitor warmup, etc.
        return now.isAfter(startDate.minusMinutes(5)) && now.isBefore(endDate.plusMinutes(5));
    }

    @Override
    public String toString() {
        return startDate.toString() + " - " + endDate.toString() + " @ " + location + ": " + summary;
    }
}
