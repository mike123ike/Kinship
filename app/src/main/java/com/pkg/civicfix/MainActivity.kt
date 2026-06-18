package com.pkg.civicfix

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainScreen(onSettingsClick = {
                // Real implementation when running on a device
                val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                startActivity(intent)
            })
        }
    }
}

// 1. The structural UI function (isolated so it's preview-friendly)
@Composable
fun MainScreen(onSettingsClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = onSettingsClick) {
            Text(text = "Go to Settings")
        }
    }
}

// 2. The Preview block that Android Studio uses for the Design tab
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    MainScreen(onSettingsClick = {
        // Do nothing mock action for the static preview panel
    })
}