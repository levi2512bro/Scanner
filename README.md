# Scanner – Dokumentenscanner für Android

Fotografiert Dokumente, erkennt die Ränder automatisch, schneidet zu, verbessert den Kontrast und speichert alles als PDF (auch mehrseitig).

**Funktionen**
- Scannen mit Kamera oder Import aus der Galerie (bis 50 Seiten pro Dokument)
- Automatische Kantenerkennung, Zuschneiden, Drehen, Filter (S/W, Graustufen), Radierer für Finger/Flecken
- Liste aller Scans mit Datum und Größe
- Öffnen, Teilen (WhatsApp, Mail …), „Speichern unter …“ (z. B. in Downloads oder Google Drive), Umbenennen, Löschen

Voraussetzung: Android 8.0 oder neuer mit Google Play-Diensten. Beim allerersten Scan lädt das Handy das Scanner-Modul einmalig nach (Internet nötig).

---

## Weg A: APK ohne PC bauen lassen (über GitHub)

1. Kostenloses Konto auf github.com anlegen.
2. Neues Repository erstellen (z. B. `scanner`).
3. Den **Inhalt** dieses Ordners hochladen („Add file → Upload files“, am PC per Drag & Drop, damit die Ordnerstruktur erhalten bleibt). Fehlt danach der versteckte Ordner `.github`, über „Add file → Create new file“ eine Datei mit dem Pfad `.github/workflows/build-apk.yml` anlegen und den Inhalt hineinkopieren.
4. Oben auf **Actions** tippen → der Lauf „APK bauen“ startet automatisch (ca. 5 Minuten).
5. Fertigen Lauf öffnen → unten unter „Artifacts“ **Scanner-APK** herunterladen, entpacken.
6. `app-debug.apk` auf dem Handy öffnen → Installation aus unbekannten Quellen erlauben → installieren.

## Weg B: Android Studio

1. Android Studio installieren und diesen Ordner mit **File → Open** öffnen.
2. Warten, bis Gradle synchronisiert hat.
3. Handy per USB verbinden (USB-Debugging an) und auf ▶ **Run** klicken –
   oder **Build → Build App Bundle(s)/APK(s) → Build APK(s)**.
   Die APK liegt dann in `app/build/outputs/apk/debug/`.
