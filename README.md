# GymTracker - Krafttraining & Progress Tracker

Eine moderne, leichtgewichtige Android-App zur Protokollierung von Krafttraining mit Fokus auf 1RM-Berechnung, Fortschrittsvisualisierung und Datensicherheit durch lokales JSON-Speichermanagement.

## 🚀 Features

- **1RM-Rechner:** Automatische Berechnung des One-Repetition-Maximums nach der Epley-Formel: `Gewicht * (1 + Wiederholungen / 30)`.
- **Session-Management:** Jedes Training wird als einzelne JSON-Datei (`workout_YYYY-MM-DD.json`) im internen App-Speicher abgelegt.
- **Progression Graph:** Eine benutzerdefinierte View (`ProgressionGraphView`), die den Kraftverlauf jeder Übung über die Zeit darstellt. Mehrere Sätze pro Tag werden farblich differenziert.
- **Trainingszonen:** Sofortige Anzeige der optimalen Gewichtsbereiche für:
    - Ausdauer (15-30%)
    - Kraftausdauer (30-50%)
    - Muskelaufbau/Hypertrophie (60-80%)
    - Intramuskuläre Koordination (90-105%)
- **Smart UI:**
    - Automatisches Hochzählen der Satznummern.
    - Vorschlag der zuletzt verwendeten Übungen.
    - Validierung gegen doppelte Einträge am selben Tag.
- **Export-Funktion:** Kombiniert alle einzelnen Session-Dateien zu einem vollständigen Backup-JSON für den externen Datenexport (z.B. WhatsApp, Mail, Cloud).

## 🎨 Design

Die App nutzt ein modernes **Material 3 Dark Mode** Design:
- **Primärfarbe:** Action Blue (#4D94FF)
- **Akzentfarbe:** Success Green (#4ADE80)
- **Hintergrund:** Deep Charcoal (#121212)
- **Text:** High-Contrast White für beste Lesbarkeit im Gym.

## 🛠 Technische Details

- **Sprache:** Kotlin
- **UI:** XML mit Material Design 3
- **Speicherung:** `org.json` für lokale Datenverarbeitung
- **Sharing:** `androidx.core.content.FileProvider` für sicheren Datei-Export
- **Grafik:** Custom View Drawing API für die Progression-Anzeige

## 📂 Datenstruktur (JSON)
Jeder Eintrag folgt diesem Schema:
```json
{
    "tag": "2023-10-27",
    "uhrzeit": "15:30:00",
    "übung": "Bankdrücken",
    "satz": "1",
    "wiederholungen": 10,
    "gewicht": 80.0,
    "1RM": 106.67
}
```

---
Entwickelt für maximale Fokuszeit im Training. Keine Cloud, kein Abo, nur Daten.
