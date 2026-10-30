package de.wirzwei.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 1;
    private static final int REQ_NOTIF = 2;
    private static final int REQ_CAMERA = 3;
    private static final String NOTIF_PERM = "android.permission.POST_NOTIFICATIONS";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;

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
                if (params.isCaptureEnabled() && Build.VERSION.SDK_INT >= 29 && openCamera()) return true;
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

    /** Kamera-App öffnen; das Foto landet direkt in „Bilder/Wir zwei“ der Galerie. */
    private boolean openCamera() {
        try {
            cameraUri = newGalleryImage("WirZwei_" + System.currentTimeMillis() + ".jpg");
            if (cameraUri == null) return false;
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
            return true;
        } catch (Exception e) {
            if (cameraUri != null) getContentResolver().delete(cameraUri, null, null);
            cameraUri = null;
            return false;
        }
    }

    private Uri newGalleryImage(String name) {
        if (Build.VERSION.SDK_INT < 29) return null;
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Wir zwei");
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_CAMERA) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && cameraUri != null) result = new Uri[]{cameraUri};
            else if (cameraUri != null) getContentResolver().delete(cameraUri, null, null);
            if (fileCallback != null) fileCallback.onReceiveValue(result);
            fileCallback = null;
            cameraUri = null;
            return;
        }
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

        private File photoFile(String id) {
            if (id == null || !id.matches("[A-Za-z0-9_]{1,64}")) return null;
            File dir = new File(getFilesDir(), "photos");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, id + ".jpg");
        }

        private byte[] decodeDataUrl(String dataUrl) {
            return Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
        }

        @JavascriptInterface
        public boolean savePhoto(String id, String dataUrl) {
            File f = photoFile(id);
            if (f == null) return false;
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(decodeDataUrl(dataUrl));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String loadPhoto(String id) {
            File f = photoFile(id);
            if (f == null || !f.exists()) return "";
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] b = new byte[(int) f.length()];
                int off = 0, r;
                while (off < b.length && (r = in.read(b, off, b.length - off)) > 0) off += r;
                return "data:image/jpeg;base64," + Base64.encodeToString(b, Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void deletePhoto(String id) {
            File f = photoFile(id);
            if (f != null) f.delete();
        }

        /** Speichert ein Bild in der Galerie (Bilder/Wir zwei) und gibt die URI zurück. */
        private Uri toGallery(String dataUrl) {
            Uri u = newGalleryImage("WirZwei_Collage_" + System.currentTimeMillis() + ".jpg");
            if (u == null) return null;
            try (OutputStream out = getContentResolver().openOutputStream(u)) {
                out.write(decodeDataUrl(dataUrl));
                return u;
            } catch (Exception e) {
                getContentResolver().delete(u, null, null);
                return null;
            }
        }

        @JavascriptInterface
        public boolean saveImage(String dataUrl) {
            return toGallery(dataUrl) != null;
        }

        @JavascriptInterface
        public boolean shareImage(String dataUrl) {
            Uri u = toGallery(dataUrl);
            if (u == null) return false;
            Intent send = new Intent(Intent.ACTION_SEND).setType("image/jpeg")
                    .putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            runOnUiThread(() -> startActivity(Intent.createChooser(send, "Collage teilen")));
            return true;
        }

        @JavascriptInterface
        public boolean pinWidget() {
            AppWidgetManager m = getSystemService(AppWidgetManager.class);
            if (m == null || !m.isRequestPinAppWidgetSupported()) return false;
            return m.requestPinAppWidget(new ComponentName(MainActivity.this, LoveWidget.class), null, null);
        }
    }
}
