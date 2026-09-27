# App Blocker 🌌

A session-based Android application designed to enhance digital wellbeing by limiting continuous screen time on TikTok. Built with Kotlin and modern Jetpack Compose architecture.

## 🚀 Features

* **Session-Based Tracking:** Unlike traditional daily limiters, this app monitors continuous foreground usage per session, preventing instant lockouts and endless doomscrolling.
* **Grace Period Logic:** Allows users to briefly background the app (e.g. 60 seconds) without losing their current session time. After the grace period expires, the session timer resets.
* **Targeted Focus:** Specifically monitors and restricts **TikTok** (`com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`, `com.zhiliaoapp.musically.go`). YouTube is excluded.
* **System-Level Overlay:** Uses `TYPE_APPLICATION_OVERLAY` to enforce limits with a custom Jetpack Compose lock screen.
* **DataStore Persistence:** Asynchronous, type-safe storage for user preferences (session limits and grace periods).
* **Cyberpunk/Nebula UI:** Features a custom space aesthetic built entirely in Jetpack Compose with canvas animations, dynamic glow, and starfields.

## 📂 Project Structure

```text
app/src/main/java/com/example/appblocker/
├── MainActivity.kt         # Entry point, handles permissions, settings, and main dashboard UI
├── AppBlockerService.kt    # Foreground service containing the core state machine & UsageStats tracking
├── BlockedApps.kt          # Configuration object defining the target TikTok packages
├── BlockerPreferences.kt   # Preferences DataStore implementation for saving limits
└── BlockOverlayScreen.kt   # Jetpack Compose UI for the lock screen overlay

app/src/main/res/           # Standard Android resources (icons, themes)
app/build.gradle.kts        # Module-level Gradle configuration (Compose & DataStore dependencies)
```

## 🛠 Tech Stack

* **Language:** Kotlin
* **UI:** Jetpack Compose (Material 3)
* **Local Storage:** AndroidX DataStore Preferences
* **Core APIs:** UsageStatsManager, WindowManager, Foreground Services

## 🔐 Permissions Required

To function correctly, the app requests the following system permissions:
1. **Usage Access:** To detect when TikTok is running in the foreground (`PACKAGE_USAGE_STATS`).
2. **Display Over Other Apps:** To draw the lock screen overlay (`SYSTEM_ALERT_WINDOW`).
3. **Post Notifications:** To maintain the persistent foreground service (`POST_NOTIFICATIONS`).

## 📦 Build & Installation

To build the debug APK:
```bash
./gradlew assembleDebug
```

Output APK location:
`app/build/outputs/apk/debug/app-debug.apk`
