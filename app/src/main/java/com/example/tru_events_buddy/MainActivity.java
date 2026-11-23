package com.example.tru_events_buddy;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.SamplerConfig;
import com.google.ai.edge.litertlm.ToolProvider;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_CLASSES = 10, PICK_EVENTS = 11;
    private final List<CalendarEvent> classes = new ArrayList<>(), events = new ArrayList<>();
    private List<CalendarEvent> available;
    private TextView status;
    private LinearLayout results;
    private Engine engine;
    private Button btnGetRecommendations;
    List<CalendarEvent> recommended;
    private EditText interests;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        status = findViewById(R.id.status);
        results = findViewById(R.id.results);
        interests = findViewById(R.id.interests);
        btnGetRecommendations = findViewById(R.id.getRecommendations);
        findViewById(R.id.importClasses).setOnClickListener(v -> pick(PICK_CLASSES));
        findViewById(R.id.importEvents).setOnClickListener(v -> pick(PICK_EVENTS));
        findViewById(R.id.findEvents).setOnClickListener(v -> showAvailable());
        btnGetRecommendations.setOnClickListener(v -> getRecommendations());
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);

        EngineConfig engineConfig = new EngineConfig(
                // Download this model from https://huggingface.co/litert-community/Gemma3-1B-IT/tree/main
                // and push it to /data/data/com.example.tru_events_buddy/files/gemma3-1b-it-int4.litertlm
                // on your device.
                getFilesDir().toPath().resolve("gemma3-1b-it-int4.litertlm").toAbsolutePath().toString(),
                new Backend.GPU()
        );
        engine = new Engine(engineConfig);
        engine.initialize();
    }

    private void getRecommendations() {
        ConversationConfig conversationConfig = new ConversationConfig(
                Contents.Companion.of(
                        String.format("The user likes %s. Decide if the event matches their interests. " +
                                "Only answer with 'yes' or 'no'.", interests.getText().toString())),
                new ArrayList<Message>(),
                new ArrayList<ToolProvider>(),
                new SamplerConfig(10, 0.95, 0.9, 42)
        );

        recommended = new ArrayList<CalendarEvent>();
        btnGetRecommendations.setText("5.\nSuggesting...");
        new Thread(() -> {
            for (int i = 0; i < available.size(); i++) {
                CalendarEvent calendarEvent = available.get(i);
                try (Conversation conversation = engine.createConversation(conversationConfig)) {
                    String answer = conversation.sendMessage(
                            String.format(
                                    "Here's the event: '%s'",
                                    calendarEvent.title, calendarEvent.description
                            )).getContents().toString();
                    Log.d("test", answer);
                    if (answer.toLowerCase().contains("yes")) {
                        recommended.add(calendarEvent);
                    }
                }
                if (recommended.size() == 3) {
                    break;
                }
            }
            MainActivity.this.runOnUiThread(() -> {
                showCalendarEvents(recommended);
                btnGetRecommendations.setText("4.\n✨ Suggest");
            });
        }).start();
    }

    private String getCourseList() {
        Set<String> courseList = new HashSet<>();
        for (CalendarEvent course : classes
        ) {
            courseList.add(course.title.split("\\(")[0].trim());
        }
        return String.join(", ", courseList);
    }

    private void pick(int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/calendar", "application/octet-stream", "text/plain"});
        startActivityForResult(intent, request);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Cannot open file");
            List<CalendarEvent> parsed = IcsParser.parse(input);
            if (request == PICK_CLASSES) {
                classes.clear();
                classes.addAll(parsed);
                showToast(String.format(Locale.getDefault(), "Imported %d classes!", parsed.size()));
            } else if (request == PICK_EVENTS) {
                events.clear();
                events.addAll(parsed);
                showToast(String.format(Locale.getDefault(), "Imported %d events!", parsed.size()));
            }
            status.setText("Loaded " + classes.size() + " class entries and " + events.size() + " TRUSU event entries.");
        } catch (Exception error) {
            status.setText("Could not read calendar: " + error.getMessage());
        }
    }

    private void showAvailable() {
        if (classes.isEmpty() || events.isEmpty()) {
            status.setText("Import both your class schedule and TRUSU event calendar first.");
            return;
        }
        available = ConflictChecker.available(classes, events);
        showCalendarEvents(available);
    }

    private void showCalendarEvents(List<CalendarEvent> calendarEvents) {
        results.removeAllViews();
        status.setText(calendarEvents.size() + " upcoming event(s) without class conflicts.");
        if (calendarEvents.isEmpty()) {
            addText("No matching events found.");
            return;
        }
        DateTimeFormatter format = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a");
        for (CalendarEvent event : calendarEvents) {
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

    private void addText(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        results.addView(view);
    }

    private void schedule(CalendarEvent event) {
        long when = event.start.toInstant().toEpochMilli() - 60 * 60 * 1000L;
        if (when <= System.currentTimeMillis()) {
            showToast("Reminder time has passed");
            return;
        }
        int id = (event.uid + event.start.toInstant()).hashCode();
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("title", event.title);
        intent.putExtra("id", id);
        PendingIntent pending = PendingIntent.getBroadcast(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
        // Inexact alarms work without special exact-alarm access.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
        showToast("Reminder scheduled");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        engine.close();
        super.onDestroy();
    }
}