# Disc Port Toolkit | Kurzanleitung

[English](../../README.md) · [Italiano](it.md) · [Français](fr.md) · Deutsch · [Español](es.md) · [日本語](ja.md)

Mit deiner PAL-Disc von BLiNX: The Time Sweeper kannst du Clone 1 für Windows oder eines der beiden Android-Profile für den Eigengebrauch bauen.

| | Benötigte Dateien | Funktionen |
| --- | --- | --- |
| Windows (Clone 1) | Verwende deine vollständige PAL-Disc. | Starte Clone 1 für Windows mit den Dateien deiner PAL-Version. |
| Android Original | Verwende deine vollständige PAL-Disc. | Starte das PAL-Spiel direkt mit der Bildschirmsteuerung. |
| Android Full | Vollständige PAL-Disc und OXM12-Demo, 263 private Oberflächen- und Mediendateien, zwei ARM64-Bibliotheken und ein lokaler Signaturschlüssel. | Wähle im Menü zwischen Original Game und der OXM12-Demo. |

## Erste Schritte

Öffne unter 64-Bit-Windows den Toolkit-Ordner und doppelklicke auf `START_HERE.cmd`. Wähle Windows Clone 1 oder Android Original. Wähle das Abbild der PAL-Spielpartition. Wenn du die Disc bereits entpackt hast, kann ihr Ordner auf einem beliebigen Laufwerk bleiben, auch außerhalb des Toolkits. Öffne ihn im Dateidialog und wähle `default.xbe` neben dem Ordner `media/`. Behalte alle Dateien und Unterordner unter `media/` in ihrer ursprünglichen Struktur.

Jeder längere Schritt hat einen eigenen Fortschrittsbalken. Das Zeichen ~ kennzeichnet einen Schätzwert. Die Laufzeit wird auch dann aktualisiert, wenn das Werkzeug keine Meldungen ausgibt. Bei erfolgreichem Abschluss erreicht der Schritt 100 %.

Unter Windows startet das Spiel. Für Android kopierst du die fertige APK auf dein Smartphone und öffnest sie.

Wenn die Disc-Prüfung fehlschlägt, aktualisiere das Toolkit und versuche es mit derselben Disc erneut. Der Launcher verwendet die bereits installierten Werkzeuge weiter. [Fehlerbehebung](../TROUBLESHOOTING.md#pal-disc-files-differ-from-the-reference)

| | |
| --- | --- |
| Windows | `START_HERE.cmd windows "D:\Discs\Blinx.iso"` |
| Android Original | `START_HERE.cmd android-original "D:\Discs\Blinx.iso"` |
| Android Full | `build_android_full.cmd` |

Für Android Original erzeugt Pillow 12.0.0 die 20 Grafiken der Bildschirmsteuerung aus zwei Texturen deiner PAL-Disc.

[Vorschau mit Sprachauswahl](../../preview/index.html) · [Technische Anleitung auf Englisch](../../README.md) · [Build-Details](../FINAL_AUDIT.md) · [Lizenzen](../../LICENSES.md)
