package com.example.trusueventfinder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    private static final int PICK_CLASSES = 10, PICK_EVENTS = 11;
    private final List<CalendarEvent> classes = new ArrayList<>(), events = new ArrayList<>();
    private TextView status;
    private LinearLayout results;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        results = findViewById(R.id.results);
        findViewById(R.id.importClasses).setOnClickListener(v -> pick(PICK_CLASSES));
        findViewById(R.id.importEvents).setOnClickListener(v -> pick(PICK_EVENTS));
        findViewById(R.id.findEvents).setOnClickListener(v -> showAvailable());
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
    }
    private void pick(int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/calendar", "application/octet-stream", "text/plain"});
        startActivityForResult(intent, request);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Cannot open file");
            List<CalendarEvent> parsed = IcsParser.parse(input);
            if (request == PICK_CLASSES) { classes.clear(); classes.addAll(parsed); }
            else if (request == PICK_EVENTS) { events.clear(); events.addAll(parsed); }
            status.setText("Loaded " + classes.size() + " class entries and " + events.size() + " TRUSU event entries.");
        } catch (Exception error) { status.setText("Could not read calendar: " + error.getMessage()); }
    }
    private void showAvailable() {
        results.removeAllViews();
        if (classes.isEmpty() || events.isEmpty()) {
            status.setText("Import both your class schedule and TRUSU event calendar first."); return;
        }
        List<CalendarEvent> available = ConflictChecker.available(classes, events);
        status.setText(available.size() + " upcoming event(s) without class conflicts.");
        if (available.isEmpty()) { addText("No matching events found."); return; }
        DateTimeFormatter format = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a");
        for (CalendarEvent event : available) {
            TextView item = new TextView(this);
            item.setText(event.title + "\n" + event.start.format(format) + " – " + event.end.format(DateTimeFormatter.ofPattern("h:mm a")) + "\n" + event.location);
            item.setTextSize(17);
            item.setPadding(12, 20, 12, 12);
            results.addView(item);
            Button remind = new Button(this);
            remind.setText("Remind me 1 hour before");
            remind.setOnClickListener(v -> schedule(event));
            results.addView(remind);
        }
    }
    private void addText(String message) { TextView view = new TextView(this); view.setText(message); results.addView(view); }
    private void schedule(CalendarEvent event) {
        long when = event.start.toInstant().toEpochMilli() - 60 * 60 * 1000L;
        if (when <= System.currentTimeMillis()) { Toast.makeText(this, "Reminder time has passed", Toast.LENGTH_SHORT).show(); return; }
        int id = (event.uid + event.start.toInstant()).hashCode();
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("title", event.title); intent.putExtra("id", id);
        PendingIntent pending = PendingIntent.getBroadcast(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
        // Inexact alarms work without special exact-alarm access.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
        Toast.makeText(this, "Reminder scheduled", Toast.LENGTH_SHORT).show();
    }
}
