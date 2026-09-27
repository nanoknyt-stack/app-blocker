package com.example.appblocker

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AppBlockerService : Service() {

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
        private const val PREFS_REHYDRATE_INTERVAL_MS = 30_000L
        private const val CHANNEL_ID = "app_blocker_channel"
        private const val NOTIF_ID = 0xB10C
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var windowManager: WindowManager

    private var overlayView: View? = null
    private var currentlyBlockedPkg: String? = null
    private var fakeOwner: FakeLifecycleOwner? = null

    // --- НОВЫЕ ПЕРЕМЕННЫЕ ЗДЕСЬ ---
    private var currentFgPkg: String? = null
    private var lastEventTime: Long = System.currentTimeMillis()

    // ── Настройки из DataStore ────────────────────────────────
    private var sessionLimitMillis: Long = 15 * 60_000L
    private var graceMillis: Long = 60_000L
    private var lastPrefsRehydrate: Long = 0L

    private data class SessionState(
        var activeMillis: Long = 0L,
        var lastForegroundTickMs: Long = 0L,
        var lastBackgroundedAtMs: Long? = null
    )

    private val sessions: MutableMap<String, SessionState> =
        BlockedApps.watchedPackages.associateWith { SessionState() }.toMutableMap()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        windowManager     = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        rehydratePrefs()
        startInForeground()
        handler.post(monitorLoop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(monitorLoop)
        removeOverlay()
        super.onDestroy()
    }

    private fun rehydratePrefs() {
        try {
            sessionLimitMillis = runBlocking {
                BlockerPreferences.sessionLimitFlow(this@AppBlockerService).first()
            } * 60_000L
            graceMillis = runBlocking {
                BlockerPreferences.graceSecondsFlow(this@AppBlockerService).first()
            } * 1000L
            lastPrefsRehydrate = System.currentTimeMillis()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID, "App Blocker", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }

        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Blocker active")
            .setContentText("TikTok session limit active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private val monitorLoop = object : Runnable {
        override fun run() {
            try { tick() }
            catch (t: Throwable) { t.printStackTrace() }
            finally { handler.postDelayed(this, POLL_INTERVAL_MS) }
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()

        if (now - lastPrefsRehydrate >= PREFS_REHYDRATE_INTERVAL_MS) {
            rehydratePrefs()
        }

        val fgPkg = currentForegroundPackage(now)

        for ((pkg, state) in sessions) {
            val isCurrentlyForeground = (pkg == fgPkg)
            updateSessionState(pkg, state, isCurrentlyForeground, now)
        }

        val rule = fgPkg?.let { BlockedApps.ruleFor(it) }
        if (rule == null) {
            if (overlayView != null) removeOverlay()
            return
        }

        val state = sessions[rule.packageName] ?: return
        if (state.activeMillis >= sessionLimitMillis) {
            showOverlay(rule, state.activeMillis)
        } else {
            if (overlayView != null && currentlyBlockedPkg == rule.packageName) {
                removeOverlay()
            }
        }
    }

    private fun updateSessionState(
        pkg: String,
        state: SessionState,
        isCurrentlyForeground: Boolean,
        now: Long
    ) {
        if (isCurrentlyForeground) {
            // Вернулся в foreground в пределах grace — продолжаем сессию.
            state.lastBackgroundedAtMs = null

            if (state.lastForegroundTickMs == 0L) {
                // Самый первый tick этой сессии.
                state.activeMillis = POLL_INTERVAL_MS
            } else {
                // Инкрементируем на реальную дельту между tick'ами
                val delta = (now - state.lastForegroundTickMs).coerceAtMost(POLL_INTERVAL_MS * 2)
                state.activeMillis += delta
            }
            state.lastForegroundTickMs = now
        } else {
            // Не foreground. Сессия может либо быть в grace, либо отсутствовать.
            if (state.activeMillis == 0L) {
                state.lastForegroundTickMs = 0L
                return
            }

            if (state.lastBackgroundedAtMs == null) {
                // Только что ушёл в background — стартуем grace-таймер.
                state.lastBackgroundedAtMs = now
            } else {
                val backgroundedFor = now - state.lastBackgroundedAtMs!!
                if (backgroundedFor >= graceMillis) {
                    // Grace истёк → полный сброс сессии.
                    state.activeMillis = 0L
                    state.lastForegroundTickMs = 0L
                    state.lastBackgroundedAtMs = null
                }
            }
        }
    }

    private fun currentForegroundPackage(now: Long): String? {
        // Запрашиваем события с момента последней проверки (с небольшим нахлестом в 2 сек, чтобы ничего не пропустить)
        val events = usageStatsManager.queryEvents(lastEventTime - 2000L, now + 1000L)
        val ev = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                // Приложение открыли — запоминаем его
                currentFgPkg = ev.packageName
            } else if (ev.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                // Приложение свернули — забываем, только если это оно
                if (currentFgPkg == ev.packageName) {
                    currentFgPkg = null
                }
            }
        }

        lastEventTime = now
        return currentFgPkg
    }

    private fun showOverlay(rule: BlockedApps.Rule, usedMillis: Long) {
        if (!Settings.canDrawOverlays(this)) return
        if (overlayView != null && currentlyBlockedPkg == rule.packageName) return
        removeOverlay()

        val composeView = ComposeView(this).apply {
            setContent {
                BlockOverlayScreen(
                    appName = rule.displayName,
                    usedMinutes = usedMillis / 60_000,
                    graceSeconds = graceMillis / 1000,
                    onGoHome = {
                        val home = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(home)
                    }
                )
            }
        }

        fakeOwner = FakeLifecycleOwner().apply { performRestore() }
        composeView.setViewTreeLifecycleOwner(fakeOwner)
        composeView.setViewTreeSavedStateRegistryOwner(fakeOwner)
        composeView.setViewTreeViewModelStoreOwner(fakeOwner)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.TOP or Gravity.START }

        try {
            windowManager.addView(composeView, params)
            overlayView = composeView
            currentlyBlockedPkg = rule.packageName
        } catch (e: WindowManager.BadTokenException) {
            // permission revoked between check and add
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        currentlyBlockedPkg = null

        fakeOwner?.destroy()
        fakeOwner = null
    }
}

// ============================================================
// УТИЛИТА ДЛЯ РАБОТЫ COMPOSE В WINDOW MANAGER
// ============================================================
private class FakeLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    fun performRestore() {
        savedStateRegistryController.performRestore(Bundle())
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}