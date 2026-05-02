# GymTracker - Strength Training & Progress Tracker

A modern, lightweight Android app for logging strength training with a focus on 1RM calculation, progress visualization, and data security through local JSON storage management.

## Features

- **1RM Calculator:** Automatic calculation of the One-Repetition Maximum using the Epley formula: `weight * (1 + reps / 30)`.
- **Session Management:** Each workout is stored as an individual JSON file (`workout_YYYY-MM-DD.json`) in the internal app storage.
- **Progression Graph:** A custom view (`ProgressionGraphView`) that displays the strength progression of each exercise over time. Multiple sets per day are color-coded.
- **Training Zones:** Immediate display of optimal weight ranges for:
    - Endurance (<50%)
    - Strength Endurance (50-70%)
    - Hypertrophy (70-85%)
    - Maximal Strength (>85%)
- **Smart UI:**
    - Automatic incrementing of set numbers.
    - Suggestions for recently used exercises.
    - Validation against extreme deviations from previous sessions.
- **Export Function:** Combines all individual session files into a complete backup JSON for external data export (e.g., WhatsApp, Email, Cloud).

## Design

The app uses a modern **Material 3 Dark Mode** design (Slate Theme):
- **Primary Color:** Cyan 500 (#06B6D4)
- **Secondary Color:** Blue 500 (#3B82F6)
- **Background:** Slate 900 (#0F172A)
- **Surface:** Slate 800 (#1E293B)
- **Text:** Slate 50 (#F8FAFC) for best readability in the gym.

## Technical Details

- **Language:** Kotlin
- **UI:** XML with Material Design 3
- **Storage:** `org.json` for local data processing
- **Sharing:** `androidx.core.content.FileProvider` for secure file export
- **Graphics:** Custom View Drawing API for the progression display

## Data Structure (JSON)
Each entry follows this schema:
```json
{
    "id": "1710945600000",
    "tag": "2024-03-20",
    "uhrzeit": "15:30:00",
    "übung": "Bench Press",
    "satz": "1",
    "wiederholungen": 10,
    "gewicht": 80.0,
    "1RM": 106.67,
    "isBodyweight": false
}
```

---
Developed for maximum focus during training. No cloud, no subscription, just data.

## About & AI Development

This application was developed significantly using **AI-assisted coding**. It serves primarily as a **robust foundation** for derivative projects or as a template that can be easily **adapted to individual training requirements**. 

Whether you want to extend the data analysis, add a cloud sync, or build a completely different logging tool, the clean JSON-based structure and modular Kotlin code provide an ideal starting point for further development.
