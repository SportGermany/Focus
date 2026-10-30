package de.wirzwei.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.util.Calendar;

/** Plant zwei wiederkehrende Wecker: Erinnerungen zur gewählten Uhrzeit und das Widget-Update kurz nach Mitternacht. */
final class Scheduler {
    static final String ACTION_REMIND = "de.wirzwei.app.REMIND";
    static final String ACTION_MIDNIGHT = "de.wirzwei.app.MIDNIGHT";

    private Scheduler() {
    }

    static PendingIntent pending(Context c, String action) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, action.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Uhrzeit der Erinnerungen als {Stunde, Minute}. */
    static int[] reminderTime(JSONObject s) {
        int h = 9, m = 0;
        try {
            String[] t = s.optString("time", "09:00").split(":");
            h = Integer.parseInt(t[0]);
            m = Integer.parseInt(t[1]);
        } catch (Exception ignored) {
        }
        return new int[]{h, m};
    }

    static void scheduleAll(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        JSONObject s = Store.sched(c);

        Calendar mid = Calendar.getInstance();
        mid.add(Calendar.DAY_OF_YEAR, 1);
        mid.set(Calendar.HOUR_OF_DAY, 0);
        mid.set(Calendar.MINUTE, 0);
        mid.set(Calendar.SECOND, 20);
        mid.set(Calendar.MILLISECOND, 0);
        am.setAndAllowWhileIdle(AlarmManager.RTC, mid.getTimeInMillis(), pending(c, ACTION_MIDNIGHT));

        PendingIntent remind = pending(c, ACTION_REMIND);
        if (!s.optBoolean("notif", false)) {
            am.cancel(remind);
            return;
        }
        int[] hm = reminderTime(s);
        Calendar r = Calendar.getInstance();
        r.set(Calendar.HOUR_OF_DAY, hm[0]);
        r.set(Calendar.MINUTE, hm[1]);
        r.set(Calendar.SECOND, 0);
        r.set(Calendar.MILLISECOND, 0);
        if (r.getTimeInMillis() <= System.currentTimeMillis()) r.add(Calendar.DAY_OF_YEAR, 1);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.getTimeInMillis(), remind);
    }
}
