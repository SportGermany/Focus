package de.wirzwei.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** Ablage der App-Daten. Die Web-Oberfläche liefert zusätzlich einen vorberechneten Zeitplan für Widget & Erinnerungen. */
final class Store {
    private Store() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("wirzwei", Context.MODE_PRIVATE);
    }

    static String data(Context c) {
        return prefs(c).getString("data", "");
    }

    static JSONObject sched(Context c) {
        try {
            return new JSONObject(prefs(c).getString("sched", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static void save(Context c, String data, String sched) {
        prefs(c).edit().putString("data", data).putString("sched", sched).apply();
    }
}
