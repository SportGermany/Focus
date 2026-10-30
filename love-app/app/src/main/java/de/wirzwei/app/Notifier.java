package de.wirzwei.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;

final class Notifier {
    private static final String CHANNEL = "reminders";

    private Notifier() {
    }

    static void createChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Geburtstage, Jahrestage und Meilensteine");
        ch.enableLights(true);
        ch.setLightColor(0xFFEAA7B9);
        nm.createNotificationChannel(ch);
    }

    /**
     * Zeigt alle Erinnerungen für heute – höchstens einmal pro Tag.
     * catchUp: nur nachholen, wenn die eingestellte Uhrzeit heute schon vorbei ist.
     */
    static void runDaily(Context c, boolean catchUp) {
        JSONObject s = Store.sched(c);
        if (!s.optBoolean("notif", false)) return;
        String today = LocalDate.now().toString();
        if (today.equals(Store.prefs(c).getString("lastNotified", ""))) return;
        if (catchUp) {
            int[] hm = Scheduler.reminderTime(s);
            if (LocalTime.now().isBefore(LocalTime.of(hm[0], hm[1]))) return;
        }
        Store.prefs(c).edit().putString("lastNotified", today).apply();
        JSONArray list = s.optJSONArray("remind");
        if (list == null) return;
        int id = 100;
        for (int i = 0; i < list.length(); i++) {
            JSONObject r = list.optJSONObject(i);
            if (r != null && today.equals(r.optString("d"))) show(c, id++, r.optString("t"), r.optString("b"));
        }
    }

    static void show(Context c, int id, String title, String text) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return;
        createChannel(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFFC8668A)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify(id, n);
    }
}
