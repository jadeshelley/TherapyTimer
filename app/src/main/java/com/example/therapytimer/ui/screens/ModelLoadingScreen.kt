package com.example.therapytimer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.therapytimer.util.VoskModelLoadState

@Composable
fun ModelLoadingScreen(
    state: VoskModelLoadState,
    onContinueAnyway: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                is VoskModelLoadState.Failed -> {
                    Text(
                        "Voice model could not be loaded",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        state.message,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (onContinueAnyway != null) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onContinueAnyway) {
                            Text("Continue anyway")
                        }
                    }
                }
                else -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = when (state) {
                            VoskModelLoadState.Loading -> "Preparing voice recognition..."
                            VoskModelLoadState.CopyingFromAssets -> "Copying voice model..."
                            else -> "Preparing..."
                        },
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
