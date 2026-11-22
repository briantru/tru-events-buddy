package com.example.trusueventfinder;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

public class CalendarEvent {
    public String title = "Untitled event";
    public String description = "";
    public String location = "";
    public ZonedDateTime start, end;
    public String frequency = "";
    public Set<String> byDays = new HashSet<>();
    public ZonedDateTime until;
    public int count = 0;
    public String uid = "";
}
