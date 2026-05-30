package com.example.appblocker

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

// Та же палитра, что и на главном
private val OvDeepSpace = Color(0xFF05010F)
private val OvPurple    = Color(0xFF1A0B3D)
private val OvViolet    = Color(0xFF2D1B5E)
private val OvCyan      = Color(0xFF00F0FF)
private val OvMagenta   = Color(0xFFFF00E5)
private val OvPink      = Color(0xFFFF3DAA)
private val OvRed       = Color(0xFFFF4D6D)
private val OvGlass     = Color(0x14FFFFFF)
private val OvBorder    = Color(0x33FFFFFF)
private val OvText      = Color(0xFFEDE6FF)
private val OvTextDim   = Color(0xFFAFA5C9)

@Composable
fun BlockOverlayScreen(
    appName: String,
    usedMinutes: Long,
    graceSeconds: Long,
    onGoHome: () -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OvCyan,
            background = OvDeepSpace,
            surface = OvPurple,
            onBackground = OvText
        )
    ) {
        Box(Modifier.fillMaxSize().background(OvDeepSpace)) {
            OverlayStarfield(Modifier.fillMaxSize())
            DangerNebula(Modifier.fillMaxSize())

            // Vignette по краям
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, OvDeepSpace.copy(alpha = 0.6f)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.7f
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LockEmblem()

                Spacer(Modifier.height(40.dp))

                Text(
                    "SESSION LIMIT",
                    color = OvRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "REACHED",
                    color = OvText,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .width(80.dp)
                        .background(
                            Brush.horizontalGradient(listOf(OvRed, OvMagenta)),
                            RoundedCornerShape(1.dp)
                        )
                )
                Spacer(Modifier.height(20.dp))

                Text(
                    appName.uppercase(),
                    color = OvCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp
                )

                Spacer(Modifier.height(28.dp))
                StatsGlassCard(usedMinutes, graceSeconds)
                Spacer(Modifier.height(36.dp))
                GoHomeButton(onGoHome)
                Spacer(Modifier.height(20.dp))

                Text(
                    "leave the app for ${graceSeconds}s to reset",
                    color = OvTextDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ======================================================
// Lock emblem: внешнее свечение + 2 орбитальных кольца + пульс
// ======================================================
@Composable
private fun LockEmblem() {
    val t = rememberInfiniteTransition(label = "lock")

    val outerRot by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label = "outer"
    )
    val innerRot by t.animateFloat(
        360f, 0f,
        infiniteRepeatable(tween(9_000, easing = LinearEasing)),
        label = "inner"
    )
    val pulse by t.animateFloat(
        0.85f, 1.05f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val glow by t.animateFloat(
        0.4f, 0.9f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        // Красное свечение
        Canvas(Modifier.fillMaxSize().blur(28.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(OvRed.copy(alpha = glow), Color.Transparent)
                ),
                radius = size.minDimension / 2.2f
            )
        }
        // Внешнее кольцо
        Canvas(Modifier.size(170.dp)) {
            rotate(outerRot) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(OvRed, OvMagenta, OvPink, OvRed)
                    ),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        // Внутреннее кольцо
        Canvas(Modifier.size(130.dp)) {
            rotate(innerRot) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(OvCyan, OvMagenta, OvCyan)
                    ),
                    startAngle = 0f,
                    sweepAngle = 200f,
                    useCenter = false,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
        // Стеклянный диск с замком
        Box(
            Modifier
                .size(95.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            OvViolet.copy(alpha = 0.9f),
                            OvDeepSpace.copy(alpha = 0.95f)
                        )
                    )
                )
                .border(1.dp, OvRed.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = OvRed,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

// ======================================================
// Glassmorphism-карточка со статами
// ======================================================
@Composable
private fun StatsGlassCard(usedMinutes: Long, graceSeconds: Long) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(OvGlass)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(OvRed.copy(alpha = 0.5f), OvMagenta.copy(alpha = 0.5f))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(vertical = 20.dp, horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatCell(usedMinutes.toString(), "MIN USED", OvRed)
            Box(
                Modifier
                    .height(48.dp)
                    .width(1.dp)
                    .background(OvBorder)
            )
            StatCell(graceSeconds.toString(), "RESET (SEC)", OvCyan)
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = accent,
            fontSize = 36.sp,
            fontWeight = FontWeight.Thin,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = OvTextDim,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ======================================================
// Неоновая кнопка "Go home" с пульсирующим glow
// ======================================================
@Composable
private fun GoHomeButton(onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "btn")
    val glow by t.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "btn_glow"
    )

    Box(
        Modifier.fillMaxWidth().height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().blur(24.dp)) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(OvCyan, OvMagenta)),
                cornerRadius = CornerRadius(40.dp.toPx()),
                alpha = glow * 0.7f
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(32.dp))
                .background(Brush.horizontalGradient(listOf(OvCyan, OvMagenta)))
                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(32.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, null, tint = OvDeepSpace)
                Spacer(Modifier.width(12.dp))
                Text(
                    "RETURN HOME",
                    color = OvDeepSpace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
            }
        }
    }
}

// ======================================================
// Starfield (80 звёзд — overlay живёт недолго, экономим CPU)
// ======================================================
private data class OvStar(
    val x: Float, val y: Float, val size: Float,
    val speed: Float, val brightness: Float, val layer: Int
)

@Composable
private fun OverlayStarfield(modifier: Modifier = Modifier) {
    val stars = remember {
        List(80) {
            val layer = Random.nextInt(3)
            OvStar(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = when (layer) { 0 -> 0.8f; 1 -> 1.4f; else -> 2.2f },
                speed = when (layer) { 0 -> 0.015f; 1 -> 0.035f; else -> 0.07f },
                brightness = 0.3f + Random.nextFloat() * 0.7f,
                layer = layer
            )
        }
    }

    val t = rememberInfiniteTransition(label = "sf")
    val p by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Restart),
        label = "sf_p"
    )

    Canvas(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(OvDeepSpace, OvPurple, OvDeepSpace))
        )
    ) {
        stars.forEach { s ->
            val y = ((s.y + p * s.speed * 10f) % 1f)
            val px = s.x * size.width
            val py = y * size.height
            if (s.layer == 2) {
                drawCircle(
                    color = OvRed.copy(alpha = s.brightness * 0.35f),
                    radius = s.size * 4f,
                    center = Offset(px, py)
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = s.brightness),
                radius = s.size * 1.5f,
                center = Offset(px, py)
            )
        }
    }
}

// ======================================================
// Туманность: красная сверху (danger) + магента снизу
// ======================================================
@Composable
private fun DangerNebula(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "neb")
    val pulse by t.animateFloat(
        0.85f, 1.2f,
        infiniteRepeatable(tween(6000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "neb_p"
    )

    Canvas(modifier = modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    OvRed.copy(alpha = 0.35f),
                    OvRed.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.5f, size.height * 0.3f),
                radius = size.width * 0.65f * pulse
            ),
            radius = size.width * 0.65f * pulse,
            center = Offset(size.width * 0.5f, size.height * 0.3f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(OvMagenta.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(size.width * 0.2f, size.height * 0.9f),
                radius = size.width * 0.5f * pulse
            ),
            radius = size.width * 0.5f * pulse,
            center = Offset(size.width * 0.2f, size.height * 0.9f)
        )
    }
}

