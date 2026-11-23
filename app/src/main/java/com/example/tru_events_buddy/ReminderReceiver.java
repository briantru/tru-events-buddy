package com.example.tru_events_buddy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends android.content.BroadcastReceiver {
    public static final String CHANNEL = "event_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26)
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Event reminders", NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Upcoming TRUSU event")
                .setContentText(intent.getStringExtra("title")).setAutoCancel(true);
        try {
            manager.notify(intent.getIntExtra("id", 1), builder.build());
        } catch (SecurityException ignored) {
        }
    }
}
