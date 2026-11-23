package com.example.tru_events_buddy;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class ConflictChecker {
    public static List<CalendarEvent> available(List<CalendarEvent> classes, List<CalendarEvent> events) {
        List<CalendarEvent> result = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now();
        for (CalendarEvent event : events) {
            if (event.start.isBefore(now) || !event.end.isAfter(event.start)) continue;
            boolean conflict = false;
            for (CalendarEvent lesson : classes) {
                if (overlap(event.start, event.end, lesson.start, lesson.end)) {
                    conflict = true;
                    break;
                }
                if (recursDuring(lesson, event.start, event.end)) {
                    conflict = true;
                    break;
                }
            }
            if (!conflict) result.add(event);
        }
        result.sort(Comparator.comparing(e -> e.start.toInstant()));
        return result;
    }

    private static boolean overlap(ZonedDateTime a, ZonedDateTime b, ZonedDateTime c, ZonedDateTime d) {
        return a.toInstant().isBefore(d.toInstant()) && c.toInstant().isBefore(b.toInstant());
    }

    private static boolean recursDuring(CalendarEvent lesson, ZonedDateTime eventStart, ZonedDateTime eventEnd) {
        if (!lesson.frequency.equals("WEEKLY") && !lesson.frequency.equals("DAILY")) return false;
        ZonedDateTime end = eventEnd.withZoneSameInstant(lesson.start.getZone());
        ZonedDateTime start = eventStart.withZoneSameInstant(lesson.start.getZone());
        long days = ChronoUnit.DAYS.between(lesson.start.toLocalDate(), end.toLocalDate());
        if (days < 0) return false;
        // Inspect only dates close to the candidate event, even for years-long calendars.
        LocalDate first = start.toLocalDate().minusDays(1);
        LocalDate last = end.toLocalDate().plusDays(1);
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            long offset = ChronoUnit.DAYS.between(lesson.start.toLocalDate(), day);
            if (offset < 0) continue;
            if (lesson.frequency.equals("DAILY") && lesson.count > 0 && offset >= lesson.count)
                continue;
            if (lesson.frequency.equals("WEEKLY")) {
                String code = day.getDayOfWeek().name().substring(0, 2);
                if (!lesson.byDays.isEmpty() && !lesson.byDays.contains(code)) continue;
                if (lesson.byDays.isEmpty() && day.getDayOfWeek() != lesson.start.getDayOfWeek())
                    continue;
                if (lesson.count > 0 && (offset / 7) >= lesson.count) continue;
            }
            ZonedDateTime occurrence = ZonedDateTime.of(day, lesson.start.toLocalTime(), lesson.start.getZone());
            if (lesson.until != null && occurrence.toInstant().isAfter(lesson.until.toInstant()))
                continue;
            ZonedDateTime finish = occurrence.plus(Duration.between(lesson.start, lesson.end));
            if (overlap(eventStart, eventEnd, occurrence, finish)) return true;
        }
        return false;
    }
}
