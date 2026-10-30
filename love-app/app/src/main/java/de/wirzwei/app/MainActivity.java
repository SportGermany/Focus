package de.wirzwei.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 1;
    private static final int REQ_NOTIF = 2;
    private static final String NOTIF_PERM = "android.permission.POST_NOTIFICATIONS";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0D0A0E);
        getWindow().setNavigationBarColor(0xFF0D0A0E);
        Notifier.createChannel(this);

        web = new WebView(this);
        web.setBackgroundColor(0xFF0D0A0E);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        s.setAllowContentAccess(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Foto wählen"), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        setContentView(web);
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.onResume&&window.onResume()", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
            if (fileCallback != null) fileCallback.onReceiveValue(result);
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_NOTIF) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            web.evaluateJavascript("window.onNotifPerm&&window.onNotifPerm('" + (ok ? "granted" : "denied") + "')", null);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("window.onBack?window.onBack():false", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    private String notifState() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(NOTIF_PERM) != PackageManager.PERMISSION_GRANTED) {
            return Store.prefs(this).getBoolean("askedNotif", false) && !shouldShowRequestPermissionRationale(NOTIF_PERM)
                    ? "blocked" : "missing";
        }
        return nm.areNotificationsEnabled() ? "granted" : "blocked";
    }

    private void openNotifSettings() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    /** Schnittstelle für die Web-Oberfläche (window.Android). */
    private class Bridge {
        @JavascriptInterface
        public String load() {
            return Store.data(MainActivity.this);
        }

        @JavascriptInterface
        public void save(String data, String sched) {
            Store.save(MainActivity.this, data, sched);
            Scheduler.scheduleAll(MainActivity.this);
            LoveWidget.updateAll(MainActivity.this);
        }

        @JavascriptInterface
        public String notifState() {
            return MainActivity.this.notifState();
        }

        @JavascriptInterface
        public void requestNotif() {
            runOnUiThread(() -> {
                String st = MainActivity.this.notifState();
                if ("granted".equals(st)) {
                    web.evaluateJavascript("window.onNotifPerm&&window.onNotifPerm('granted')", null);
                } else if (Build.VERSION.SDK_INT >= 33 && "missing".equals(st)) {
                    Store.prefs(MainActivity.this).edit().putBoolean("askedNotif", true).apply();
                    requestPermissions(new String[]{NOTIF_PERM}, REQ_NOTIF);
                } else {
                    openNotifSettings();
                }
            });
        }

        @JavascriptInterface
        public void openNotifSettings() {
            runOnUiThread(MainActivity.this::openNotifSettings);
        }

        @JavascriptInterface
        public void testNotif() {
            Notifier.show(MainActivity.this, 9999, "💞 So sehen eure Erinnerungen aus",
                    "Hier erinnert dich „Wir zwei“ an Geburtstage, Jahrestage & Meilensteine.");
        }

        @JavascriptInterface
        public boolean pinWidget() {
            AppWidgetManager m = getSystemService(AppWidgetManager.class);
            if (m == null || !m.isRequestPinAppWidgetSupported()) return false;
            return m.requestPinAppWidget(new ComponentName(MainActivity.this, LoveWidget.class), null, null);
        }
    }
}
