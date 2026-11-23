package com.example.tru_events_buddy;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class IcsParser {
    public static List<CalendarEvent> parse(InputStream input) throws IOException {
        List<String> lines = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && !lines.isEmpty()) {
                int last = lines.size() - 1;
                lines.set(last, lines.get(last) + line.substring(1));
            } else lines.add(line);
        }
        List<CalendarEvent> events = new ArrayList<>();
        CalendarEvent event = null;
        for (String item : lines) {
            if (item.equals("BEGIN:VEVENT")) {
                event = new CalendarEvent();
                continue;
            }
            if (item.equals("END:VEVENT")) {
                if (event != null && event.start != null && event.end != null && event.end.isAfter(event.start))
                    events.add(event);
                event = null;
                continue;
            }
            if (event == null) continue;
            int colon = item.indexOf(':');
            if (colon < 0) continue;
            String key = item.substring(0, colon).toUpperCase(Locale.ROOT);
            String value = item.substring(colon + 1);
            String name = key.split(";", 2)[0];
            try {
                switch (name) {
                    case "SUMMARY":
                        event.title = unescape(value);
                        break;
                    case "DESCRIPTION":
                        event.description = unescape(value);
                        break;
                    case "LOCATION":
                        event.location = unescape(value);
                        break;
                    case "UID":
                        event.uid = value;
                        break;
                    case "DTSTART":
                        event.start = date(value, key);
                        break;
                    case "DTEND":
                        event.end = date(value, key);
                        break;
                    case "RRULE":
                        for (String field : value.split(";")) {
                            String[] parts = field.split("=", 2);
                            if (parts.length != 2) continue;
                            switch (parts[0]) {
                                case "FREQ":
                                    event.frequency = parts[1];
                                    break;
                                case "BYDAY":
                                    event.byDays.addAll(Arrays.asList(parts[1].split(",")));
                                    break;
                                case "UNTIL":
                                    event.until = date(parts[1], "UNTIL");
                                    break;
                                case "COUNT":
                                    event.count = Integer.parseInt(parts[1]);
                                    break;
                            }
                        }
                        break;
                }
            } catch (RuntimeException ignored) { /* Ignore invalid individual fields */ }
        }
        return events;
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\");
    }

    private static ZonedDateTime date(String value, String key) {
        ZoneId zone = ZoneId.systemDefault();
        for (String part : key.split(";"))
            if (part.startsWith("TZID=")) {
                try {
                    zone = ZoneId.of(part.substring(5).replace("\"", ""));
                } catch (Exception ignored) {
                }
            }
        if (value.matches("\\d{8}"))
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(zone);
        if (value.endsWith("Z"))
            return LocalDateTime.parse(value.substring(0, value.length() - 1), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).atZone(ZoneOffset.UTC);
        DateTimeFormatter format = DateTimeFormatter.ofPattern(value.length() == 13 ? "yyyyMMdd'T'HHmm" : "yyyyMMdd'T'HHmmss");
        return LocalDateTime.parse(value, format).atZone(zone);
    }
}
