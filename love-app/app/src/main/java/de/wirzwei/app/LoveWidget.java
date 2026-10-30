package de.wirzwei.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.Base64;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class LoveWidget extends AppWidgetProvider {
    private static final int AVATAR_PX = 132;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        updateAll(c);
    }

    @Override
    public void onEnabled(Context c) {
        Scheduler.scheduleAll(c);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, LoveWidget.class));
        if (ids == null || ids.length == 0) return;
        m.updateAppWidget(ids, build(c));
    }

    private static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        JSONObject s = Store.sched(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        String n1 = s.optString("n1", ""), n2 = s.optString("n2", "");
        v.setImageViewBitmap(R.id.a1, avatar(s.optString("a1", ""), n1));
        v.setImageViewBitmap(R.id.a2, avatar(s.optString("a2", ""), n2));
        v.setTextViewText(R.id.names, (n1.isEmpty() ? "Du" : n1) + " & " + (n2.isEmpty() ? "Ich" : n2));

        LocalDate today = LocalDate.now();
        String start = s.optString("start", "");
        if (start.isEmpty() || "null".equals(start)) {
            v.setTextViewText(R.id.days, "♥");
            v.setTextViewText(R.id.label, "App öffnen zum Einrichten");
            v.setViewVisibility(R.id.next, View.GONE);
            return v;
        }
        LocalDate st = LocalDate.parse(start);
        long days = ChronoUnit.DAYS.between(st, today);
        if (days < 0) {
            v.setTextViewText(R.id.days, String.format(Locale.GERMANY, "%,d", -days));
            v.setTextViewText(R.id.label, -days == 1 ? "TAG BIS ES LOSGEHT" : "TAGE BIS ES LOSGEHT");
        } else {
            if (s.optBoolean("plus1", false)) days += 1;
            v.setTextViewText(R.id.days, String.format(Locale.GERMANY, "%,d", days));
            v.setTextViewText(R.id.label, days == 1 ? "TAG ZUSAMMEN" : "TAGE ZUSAMMEN");
        }

        String next = null;
        JSONArray list = s.optJSONArray("next");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject e = list.optJSONObject(i);
                if (e == null) continue;
                LocalDate d;
                try {
                    d = LocalDate.parse(e.optString("d"));
                } catch (Exception ex) {
                    continue;
                }
                long n = ChronoUnit.DAYS.between(today, d);
                if (n < 0) continue;
                String when = n == 0 ? "heute 🎉" : n == 1 ? "morgen" : String.format(Locale.GERMANY, "in %,d Tagen", n);
                next = e.optString("e") + "  " + e.optString("t") + " · " + when;
                break;
            }
        }
        if (next != null) {
            v.setViewVisibility(R.id.next, View.VISIBLE);
            v.setTextViewText(R.id.next, next);
        } else {
            v.setViewVisibility(R.id.next, View.GONE);
        }
        return v;
    }

    /** Rundes Profilbild aus der Data-URL – oder ein Kreis mit Initiale, wenn kein Foto gesetzt ist. */
    private static Bitmap avatar(String dataUrl, String name) {
        Bitmap out = Bitmap.createBitmap(AVATAR_PX, AVATAR_PX, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        float r = AVATAR_PX / 2f;
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setColor(0xFFD9B77D);
        cv.drawCircle(r, r, r, ring);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFF0D0A0E);
        cv.drawCircle(r, r, r - 4, bg);

        Bitmap src = null;
        try {
            if (dataUrl != null && dataUrl.startsWith("data:")) {
                byte[] b = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
                src = BitmapFactory.decodeByteArray(b, 0, b.length);
            }
        } catch (Exception ignored) {
        }
        float inner = r - 8;
        if (src != null) {
            Bitmap sc = Bitmap.createScaledBitmap(src, (int) (inner * 2), (int) (inner * 2), true);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            BitmapShader sh = new BitmapShader(sc, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            android.graphics.Matrix mx = new android.graphics.Matrix();
            mx.setTranslate(r - inner, r - inner);
            sh.setLocalMatrix(mx);
            p.setShader(sh);
            cv.drawCircle(r, r, inner, p);
        } else {
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(0xFF2A2129);
            cv.drawCircle(r, r, inner, fill);
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(0xFFEAA7B9);
            t.setTextSize(AVATAR_PX * 0.42f);
            t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
            t.setTextAlign(Paint.Align.CENTER);
            String init = name == null || name.trim().isEmpty() ? "♥" : name.trim().substring(0, 1).toUpperCase(Locale.GERMANY);
            Paint.FontMetrics fm = t.getFontMetrics();
            cv.drawText(init, r, r - (fm.ascent + fm.descent) / 2, t);
        }
        return out;
    }
}
