package com.example.therapytimer.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.therapytimer.R
import kotlinx.coroutines.delay

/** All 10 splash slides (stretch_1.png … stretch_10.png). */
private val STRETCH_DRAWABLE_IDS = listOf(
    R.drawable.stretch_1, R.drawable.stretch_2, R.drawable.stretch_3, R.drawable.stretch_4,
    R.drawable.stretch_5, R.drawable.stretch_6, R.drawable.stretch_7, R.drawable.stretch_8,
    R.drawable.stretch_9, R.drawable.stretch_10
)

/** Delays (ms) per slide, progressing faster: 450 down to 180ms */
private val PROGRESSIVE_DELAYS_MS = listOf(450, 400, 360, 320, 300, 280, 260, 240, 220, 200)

private const val APP_NAME_DISPLAY_MS = 2000L

@Composable
fun CustomSplashScreen(
    onComplete: () -> Unit,
    startDelayMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    var currentIndex by remember { mutableStateOf(0) }
    var phase by remember { mutableStateOf(SplashPhase.IMAGES) }
    var imageAlpha by remember { mutableStateOf(1f) }

    LaunchedEffect(Unit) {
        if (startDelayMs > 0L) delay(startDelayMs)
        for (i in STRETCH_DRAWABLE_IDS.indices) {
            currentIndex = i
            imageAlpha = 1f
            delay(PROGRESSIVE_DELAYS_MS[i].toLong())
        }
        phase = SplashPhase.APP_NAME
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = imageAlpha,
        animationSpec = tween(150)
    )

    Box(modifier = modifier.fillMaxSize()) {
        when (phase) {
            SplashPhase.IMAGES -> {
                Image(
                    painter = painterResource(STRETCH_DRAWABLE_IDS[currentIndex]),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(animatedAlpha),
                    contentScale = ContentScale.Crop
                )
            }
            SplashPhase.APP_NAME -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.app_name_splash),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Built for recovery. Designed for progress",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = onComplete) {
                            Text("Begin")
                        }
                    }
                }
            }
        }
    }
}

private enum class SplashPhase { IMAGES, APP_NAME }
