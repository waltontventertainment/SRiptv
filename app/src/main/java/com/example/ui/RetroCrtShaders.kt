package com.example.ui

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.util.Random

/**
 * Generates and plays a 1-second white noise ("khash-khash") analog static audio clip dynamically.
 * Self-contained, does not require any asset files.
 */
fun playWhiteNoiseSound() {
    Thread {
        try {
            val sampleRate = 22050
            val durationSeconds = 0.8
            val numSamples = (sampleRate * durationSeconds).toInt()
            val buffer = ShortArray(numSamples)
            val random = Random()
            
            for (i in 0 until numSamples) {
                // Generate randomized PCM samples for fuzzy analog static sound
                buffer[i] = (random.nextInt(14000) - 7000).toShort()
            }
            
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                numSamples * 2,
                AudioTrack.MODE_STATIC
            )
            
            audioTrack.write(buffer, 0, numSamples)
            audioTrack.play()
            
            // Wait for duration to complete then release resources
            Thread.sleep((durationSeconds * 1000).toLong())
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }.start()
}

/**
 * Highly responsive CRT Static Noise (ঝিরঝির) animation.
 * Replicates the fuzzy, horizontal banding of nostalgic analog TVs.
 */
@Composable
fun CrtStaticNoise(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "CrtStatic")
    val frameState = infiniteTransition.animateValue(
        initialValue = 0,
        targetValue = 20,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "StaticFrame"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val rand = Random(frameState.value.toLong())

        // Draw horizontal static bands
        for (i in 0 until 35) {
            val bandY = rand.nextFloat() * height
            val bandHeight = rand.nextFloat() * 25f + 4f
            val alpha = rand.nextFloat() * 0.35f + 0.4f
            val isDark = rand.nextBoolean()
            val color = if (isDark) Color(0xFF151515) else Color(0xFFE5E5E5)
            
            drawRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(0f, bandY),
                size = Size(width, bandHeight)
            )
        }

        // Draw fine grain particles
        for (i in 0 until 400) {
            val grainX = rand.nextFloat() * width
            val grainY = rand.nextFloat() * height
            val grainSize = rand.nextFloat() * 6f + 2f
            val grayValue = rand.nextFloat() * 0.4f + 0.3f
            
            drawRect(
                color = Color(grayValue, grayValue, grayValue, 0.65f),
                topLeft = Offset(grainX, grainY),
                size = Size(grainSize, grainSize)
            )
        }
    }
}

/**
 * Draws vintage scanlines, phosphor screen grid lines, and curved glass vignette shading
 * to give an authentic analog tube feel.
 */
@Composable
fun CrtScanlineOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Horizontal scanlines
        val scanlineSpacing = 5f
        var y = 0f
        while (y < height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.16f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.5f
            )
            y += scanlineSpacing
        }

        // 2. Vertical phosphor grille
        val phosphorSpacing = 15f
        var x = 0f
        while (x < width) {
            drawLine(
                color = Color.Black.copy(alpha = 0.04f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1.5f
            )
            x += phosphorSpacing
        }

        // 3. Curved tube bezel and screen frame vignette shadow
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                center = Offset(width / 2f, height / 2f),
                radius = (width / 2f) * 1.35f
            )
        )

        // Subtle ambient screen glare
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.03f), Color.Transparent),
                start = Offset(0f, 0f),
                end = Offset(width, height)
            )
        )
    }
}

enum class CrtScreenState {
    IDLE,
    COLLAPSING_Y,
    COLLAPSING_X,
    OFF
}

/**
 * Animates a nostalgic CRT Screen Off effect.
 * Collapses the view first vertically to a thin line, then horizontally to a single dot, and turns off.
 */
@Composable
fun CrtScreenOffOverlay(
    state: CrtScreenState,
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state == CrtScreenState.IDLE) return

    var heightScale by remember { mutableStateOf(1f) }
    var widthScale by remember { mutableStateOf(1f) }

    LaunchedEffect(state) {
        if (state == CrtScreenState.COLLAPSING_Y) {
            animate(
                initialValue = 1f,
                targetValue = 0.005f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
            ) { value, _ ->
                heightScale = value
            }
            onAnimationFinished() // Signal we finished collapsing Y
        } else if (state == CrtScreenState.COLLAPSING_X) {
            heightScale = 0.005f
            animate(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)
            ) { value, _ ->
                widthScale = value
            }
            onAnimationFinished() // Signal we finished collapsing X (OFF)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (state != CrtScreenState.OFF) {
            // Draw the collapsing phosphor beam line
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthScale)
                    .fillMaxHeight(heightScale)
                    .background(Color.White)
            )
        }
    }
}
