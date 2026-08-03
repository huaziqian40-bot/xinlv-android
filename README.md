# Xinlv (心履) — Android Client

A native Android client for the Xinlv mood-tracking and mental-health companion platform. Built with pure Java, Room database, and Material Design 3. Works offline and syncs automatically when connected — shares the same REST API and sync rules as the web app and Windows client.

## Features

- **Login / Register / Guest Mode** — Three ways to get started
- **Mood Calendar** — Month view with daily mood recording (including intensity slider)
- **Smart Recommendations** — Mood-based推 of music, activities, psychology tips
- **AI Confidant** — Anonymous chat with DeepSeek, crisis interception, voice朗读
- **Relaxation Game** — "Merge Big Watermelon" clone, swipe controls
- **Profile Page** — Streak/badges/total records, theme customization
- **Offline Sync** — Record moods offline, auto-sync when online
- **Theme System** — 4 preset themes + custom 3-color system (accent, background, card)
- **Mood Visuals** — Mood affects UI background color + rain effect

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| UI | Material Design 3 (native) |
| Local Database | Room (SQLite) |
| Networking | HttpURLConnection |
| Image Loading | Native Bitmap |
| Build Tool | Gradle |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 36 (Android 14) |

## Development Setup

### Requirements
- Android Studio 2024+
- JDK 17
- Android SDK 36
- Android emulator (API 26+) or real device

### Build & Run

```bash
git clone <your-repo-url>
cd android

# Open in Android Studio, or build from command line:
./gradlew assembleDebug      # Debug APK
./gradlew assembleRelease    # Release APK (requires signing config)
```

### Signing Configuration
Create `keystore.properties` in the project root:

```properties
storeFile=your-keystore-path
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

## Project Structure

```
android/
├── build.gradle
├── settings.gradle
├── app/
│   ├── build.gradle
│   └── src/main/java/com/moodtree/app/
│       ├── App.java                # Application entry
│       ├── db/                     # Room database, entities, DAOs
│       ├── util/
│       │   ├── Config.java         # SharedPreferences wrapper
│       │   ├── ApiClient.java      # REST API client
│       │   └── Json.java           # Gson wrapper
│       ├── model/                  # MoodMeta, Theme
│       ├── sync/SyncEngine.java    # Offline sync engine
│       └── ui/                     # Activities and Fragments
│           ├── LoginActivity.java
│           ├── MainActivity.java
│           ├── CalendarFragment.java
│           ├── MoodDialogFragment.java
│           ├── RecommendFragment.java
│           ├── ChatFragment.java
│           ├── GameFragment.java
│           ├── MeFragment.java
│           └── ColorPickerView.java
```

## Sync Mechanism

Consistent with server and Windows client:
- **UUID dedup** — Each record has a unique UUID
- **LWW (Last-Write-Wins)** — Latest timestamp wins
- **Tombstone** — Deletion marks `deleted=true`, never truly removed
- **Dirty flag** — Offline edits marked dirty, pushed on reconnect
- **Incremental pull** — Sync by `since` timestamp

## License

Personal learning and non-commercial use only.