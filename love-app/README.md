# Wir zwei – Love-App für Android

Dunkle, elegante Paar-App: Tage zusammen, Countdown zu Geburtstagen & Jahrestagen,
automatische Meilensteine (100 Tage, 1.000 Tage, 1 Million Minuten …), Erinnerungen
und ein Homescreen-Widget. Alle Daten bleiben nur auf dem Handy.

- `WirZwei.apk` – fertige App zum Installieren (wird von GitHub Actions gebaut)
- `app/src/main/assets/index.html` – Oberfläche
- `app/src/main/java/...` – Widget, Erinnerungen, WebView-Hülle

Bauen: `gradle -p love-app assembleRelease` (Android SDK nötig) oder Workflow
„Love-App APK bauen“ in GitHub Actions.
