# App Blocker 🌌

A session-based Android application designed to enhance digital wellbeing by limiting continuous screen time on highly engaging apps (YouTube, TikTok). Built with Kotlin and modern Jetpack Compose architecture.

##  Features

* **Session-Based Tracking:** Unlike traditional daily limiters, this app resets the timer for each distinct session, preventing instant lockouts.
* **Grace Period Logic:** Allows users to briefly background an app without losing their current session time.
* **System-Level Overlay:** Uses `TYPE_APPLICATION_OVERLAY` to enforce limits with a custom Jetpack Compose lock screen.
* **DataStore Persistence:** Asynchronous, type-safe storage for user preferences (session limits and grace periods).
* **Modern UI:** Features a custom "Nebula/Starfield" aesthetic built entirely in Jetpack Compose using `Canvas`.

## 📂 Project Structure

The project follows a standard Android Kotlin structure.

app/src/main/java/com/example/appblocker/
├── MainActivity.kt         # Entry point, handles permissions and main dashboard UI
├── AppBlockerService.kt    # Foreground service containing the core state machine & UsageStats tracking
├── BlockedApps.kt          # Configuration object defining the target application packages
├── BlockerPreferences.kt   # Preferences DataStore implementation for saving limits
└── BlockOverlayScreen.kt   # Jetpack Compose UI for the lock screen overlay

app/src/main/res/           # Standard Android resources (icons, themes)
app/build.gradle.kts        # Module-level Gradle configuration (Compose & DataStore dep

## Tech Stack

Language: Kotlin

UI: Jetpack Compose

Local Storage: Androidx DataStore Preferences

Core APIs: UsageStatsManager, WindowManager, Foreground Services

🔐 Permissions Required

To function correctly, the app requests the following system permissions:

Usage Access: To detect which app is currently in the foreground.

Display Over Other Apps: To draw the lock screen overlay.

Post Notifications: To maintain the persistent foreground service.
