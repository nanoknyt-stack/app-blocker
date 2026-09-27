package com.example.appblocker

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.random.Random

// ============================================================
// COLOR PALETTE — Galaxy / Nebula
// ============================================================
private val DeepSpace      = Color(0xFF05010F)
private val NebulaPurple   = Color(0xFF1A0B3D)
private val NebulaViolet   = Color(0xFF2D1B5E)
private val NeonCyan       = Color(0xFF00F0FF)
private val NeonMagenta    = Color(0xFFFF00E5)
private val NeonPink       = Color(0xFFFF3DAA)
private val GlassWhite     = Color(0x14FFFFFF)
private val GlassBorder    = Color(0x33FFFFFF)
private val TextPrimary    = Color(0xFFEDE6FF)
private val TextSecondary  = Color(0xFFAFA5C9)
private val DangerRed      = Color(0xFFFF4D6D)
private val SuccessGreen   = Color(0xFF4DFFB8)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = NeonCyan,
                    secondary = NeonMagenta,
                    background = DeepSpace,
                    surface = NebulaPurple,
                    onPrimary = DeepSpace,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
                AppBlockerScreen()
            }
        }
    }
}

// ============================================================
// PERMISSIONS STATE
// ============================================================
private data class PermissionsState(
    val usageStats: Boolean,
    val overlay: Boolean,
    val notifications: Boolean
) {
    val allGranted: Boolean get() = usageStats && overlay && notifications
}

private fun Context.checkPermissions(): PermissionsState {
    val usage = hasUsageStatsPermission()
    val overlay = Settings.canDrawOverlays(this)
    val notif = if (Build.VERSION.SDK_INT >= 33)
        ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    else true
    return PermissionsState(usage, overlay, notif)
}

private fun Context.hasUsageStatsPermission(): Boolean {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= 29) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

// ============================================================
// MAIN SCREEN
// ============================================================
@Composable
fun AppBlockerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var perms by remember { mutableStateOf(context.checkPermissions()) }
    var serviceRunning by rememberSaveable { mutableStateOf(false) }
    var showLimitDialog by remember { mutableStateOf(false) }
    var showRestrictedHint by remember { mutableStateOf(false) }

    // ── Подписка на DataStore (Flow → State) ──────────────────
    val limitMinutes by BlockerPreferences
        .sessionLimitFlow(context)
        .collectAsStateWithLifecycle(initialValue = BlockerPreferences.DEFAULT_LIMIT_MIN)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        perms = context.checkPermissions()
    }

    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { perms = context.checkPermissions() }

    Box(modifier = Modifier.fillMaxSize()) {
        StarfieldBackground(modifier = Modifier.fillMaxSize())
        NebulaGlow(modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = NebulaViolet,
                        contentColor = TextPrimary,
                        actionColor = NeonCyan,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScrollSafe(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                HeaderSection()
                Spacer(Modifier.height(28.dp))

                TimerDial(
                    minutes = limitMinutes,
                    active = serviceRunning,
                    onClick = { showLimitDialog = true }
                )

                Spacer(Modifier.height(16.dp))

                // ── Дисклеймер про Shorts ─────────────────────
                DisclaimerCard()

                Spacer(Modifier.height(24.dp))

                GlassCard {
                    Column(Modifier.padding(20.dp)) {
                        SectionTitle("System Access", Icons.Default.Security)
                        Spacer(Modifier.height(16.dp))
                        PermissionRow(
                            label = "Usage Stats",
                            sublabel = "Foreground app tracking",
                            granted = perms.usageStats,
                            onClick = {
                                showRestrictedHint = true
                                openSettingsSafely(
                                    context = context,
                                    primary = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                                    fallback = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    ),
                                    onError = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }
                        )
                        DividerLine()
                        PermissionRow(
                            label = "Display Over Apps",
                            sublabel = "Blocking overlay",
                            granted = perms.overlay,
                            onClick = {
                                openSettingsSafely(
                                    context = context,
                                    primary = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ),
                                    fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                                    onError = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }
                        )
                        DividerLine()
                        PermissionRow(
                            label = "Notifications",
                            sublabel = "Foreground service alert",
                            granted = perms.notifications,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Auto-granted on this Android version")
                                    }
                                }
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showRestrictedHint && !perms.usageStats && Build.VERSION.SDK_INT >= 33,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        RestrictedSettingsHint()
                    }
                }

                Spacer(Modifier.height(28.dp))

                NeonActionButton(
                    text = if (serviceRunning) "STOP BLOCKER" else "ENGAGE BLOCKER",
                    icon = if (serviceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    enabled = perms.allGranted,
                    danger = serviceRunning,
                    onClick = {
                        val intent = Intent(context, AppBlockerService::class.java)
                        if (serviceRunning) {
                            context.stopService(intent)
                            serviceRunning = false
                        } else {
                            try {
                                ContextCompat.startForegroundService(context, intent)
                                serviceRunning = true
                            } catch (e: Exception) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Failed to start service: ${e.message}")
                                }
                            }
                        }
                    }
                )

                if (!perms.allGranted) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Grant all 3 permissions to activate",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }

        if (showLimitDialog) {
            LimitDialog(
                currentMinutes = limitMinutes,
                onDismiss = { showLimitDialog = false },
                onConfirm = { newLimit ->
                    scope.launch {
                        BlockerPreferences.setSessionLimit(context, newLimit)
                    }
                    showLimitDialog = false
                }
            )
        }
    }
}

// ============================================================
// SAFE INTENT LAUNCHER
// ============================================================
private fun openSettingsSafely(
    context: Context,
    primary: Intent,
    fallback: Intent,
    onError: (String) -> Unit
) {
    primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(primary)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(fallback)
        } catch (e2: ActivityNotFoundException) {
            onError("This device has no settings menu for this permission. Grant it manually in App Info.")
        } catch (e2: Exception) {
            onError("Could not open settings: ${e2.message}")
        }
    } catch (e: Exception) {
        onError("Could not open settings: ${e.message}")
    }
}

// ============================================================
// DISCLAIMER CARD — новый компонент
// ============================================================
@Composable
private fun DisclaimerCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassWhite)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        NeonMagenta.copy(alpha = 0.4f),
                        NeonCyan.copy(alpha = 0.4f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = NeonMagenta,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "TIKTOK SESSION LIMIT",
                    color = NeonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Timer tracks continuous TikTok usage per session. YouTube is not blocked.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Background TikTok for 60s to reset the session timer.",
                    color = TextPrimary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

// ============================================================
// ANIMATED STARFIELD
// ============================================================
private data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val brightness: Float,
    val parallaxLayer: Int
)

@Composable
private fun StarfieldBackground(modifier: Modifier = Modifier) {
    val stars = remember {
        List(120) {
            val layer = Random.nextInt(3)
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = when (layer) { 0 -> 0.8f; 1 -> 1.4f; else -> 2.2f },
                speed = when (layer) { 0 -> 0.015f; 1 -> 0.035f; else -> 0.07f },
                brightness = 0.3f + Random.nextFloat() * 0.7f,
                parallaxLayer = layer
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "starfield")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "star_progress"
    )

    Canvas(modifier = modifier.background(
        Brush.verticalGradient(listOf(DeepSpace, NebulaPurple, DeepSpace))
    )) {
        drawStars(stars, progress)
    }
}

private fun DrawScope.drawStars(stars: List<Star>, progress: Float) {
    stars.forEach { star ->
        val animatedY = ((star.y + progress * star.speed * 10f) % 1f)
        val px = star.x * size.width
        val py = animatedY * size.height

        if (star.parallaxLayer == 2) {
            drawCircle(
                color = NeonCyan.copy(alpha = star.brightness * 0.3f),
                radius = star.size * 4f,
                center = Offset(px, py)
            )
        }
        drawCircle(
            color = Color.White.copy(alpha = star.brightness),
            radius = star.size * 1.5f,
            center = Offset(px, py)
        )
    }
}

// ============================================================
// NEBULA GLOW
// ============================================================
@Composable
private fun NebulaGlow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "nebula")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonMagenta.copy(alpha = 0.25f),
                    NeonMagenta.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.2f, size.height * 0.25f),
                radius = size.width * 0.5f * pulse
            ),
            radius = size.width * 0.5f * pulse,
            center = Offset(size.width * 0.2f, size.height * 0.25f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonCyan.copy(alpha = 0.22f),
                    NeonCyan.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.85f, size.height * 0.75f),
                radius = size.width * 0.55f * pulse
            ),
            radius = size.width * 0.55f * pulse,
            center = Offset(size.width * 0.85f, size.height * 0.75f)
        )
    }
}

// ============================================================
// HEADER
// ============================================================
@Composable
private fun HeaderSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "TIKTOK CONTROL",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
            fontFamily = FontFamily.SansSerif
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(60.dp)
                .background(
                    Brush.horizontalGradient(listOf(NeonCyan, NeonMagenta)),
                    RoundedCornerShape(1.dp)
                )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "digital wellbeing • orbital control",
            color = TextSecondary,
            fontSize = 11.sp,
            letterSpacing = 2.sp
        )
    }
}

// ============================================================
// TIMER DIAL
// ============================================================
@Composable
private fun TimerDial(minutes: Int, active: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "ring")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().blur(20.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (active) SuccessGreen else NeonCyan).copy(alpha = glowAlpha),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension / 2f
            )
        }
        Canvas(modifier = Modifier.size(220.dp)) {
            rotate(rotation) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(NeonCyan, NeonMagenta, NeonPink, NeonCyan)
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(NebulaViolet.copy(alpha = 0.7f), DeepSpace.copy(alpha = 0.9f))
                    )
                )
                .border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$minutes",
                    color = TextPrimary,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Thin,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "MIN / SESSION",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("tap to edit", color = TextSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

// ============================================================
// GLASS CARD
// ============================================================
@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GlassWhite)
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
    ) { content() }
}

// ============================================================
// PERMISSION ROW
// ============================================================
@Composable
private fun PermissionRow(
    label: String,
    sublabel: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusOrb(granted = granted)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(sublabel, color = TextSecondary, fontSize = 12.sp)
        }
        if (!granted) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = NeonCyan)
        } else {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
        }
    }
}

@Composable
private fun StatusOrb(granted: Boolean) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )
    val color = if (granted) SuccessGreen else DangerRed

    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().blur(8.dp)) {
            drawCircle(color = color.copy(alpha = pulse * 0.7f))
        }
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GlassBorder)
    )
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

// ============================================================
// NEON ACTION BUTTON
// ============================================================
@Composable
private fun NeonActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    danger: Boolean,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "btn")
    val glow by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "btn_glow"
    )

    val accent = if (danger) DangerRed else NeonCyan
    val accent2 = if (danger) NeonPink else NeonMagenta

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        contentAlignment = Alignment.Center
    ) {
        if (enabled) {
            Canvas(modifier = Modifier.fillMaxSize().blur(24.dp)) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(accent, accent2)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(40.dp.toPx()),
                    alpha = glow * 0.8f
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(36.dp))
                .background(
                    if (enabled) Brush.horizontalGradient(listOf(accent, accent2))
                    else Brush.horizontalGradient(listOf(GlassWhite, GlassWhite))
                )
                .border(
                    1.dp,
                    if (enabled) Color.White.copy(alpha = 0.4f) else GlassBorder,
                    RoundedCornerShape(36.dp)
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) DeepSpace else TextSecondary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text,
                    color = if (enabled) DeepSpace else TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
            }
        }
    }
}

// ============================================================
// RESTRICTED SETTINGS HINT
// ============================================================
@Composable
private fun RestrictedSettingsHint() {
    GlassCard {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = NeonMagenta,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Toggle disabled?",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "On Android 13+ go to App Info → tap ⋮ (top right) → Allow restricted settings, then return here.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

// ============================================================
// LIMIT DIALOG
// ============================================================
@Composable
private fun LimitDialog(
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var value by remember { mutableIntStateOf(currentMinutes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NebulaViolet,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(24.dp),
        title = { Text("SESSION LIMIT", letterSpacing = 2.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$value min",
                    color = NeonCyan,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Thin,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = value.toFloat(),
                    onValueChange = { value = it.toInt() },
                    valueRange = 1f..120f,
                    steps = 0,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = GlassBorder
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Continuous use before overlay locks the app",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text("CONFIRM", color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextSecondary)
            }
        }
    )
}

// ============================================================
// SCROLL HELPER
// ============================================================
@Composable
private fun Modifier.verticalScrollSafe(): Modifier {
    val state = rememberScrollState()
    return this.verticalScroll(state)
}

