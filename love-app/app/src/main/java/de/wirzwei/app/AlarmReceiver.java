package de.wirzwei.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (Scheduler.ACTION_REMIND.equals(a)) {
            Notifier.runDaily(c, false);
        } else if (!Scheduler.ACTION_MIDNIGHT.equals(a)) {
            // Neustart, App-Update, Zeit-/Zeitzonenwechsel: verpasste Erinnerungen von heute nachholen
            Notifier.runDaily(c, true);
        }
        LoveWidget.updateAll(c);
        Scheduler.scheduleAll(c);
    }
}
