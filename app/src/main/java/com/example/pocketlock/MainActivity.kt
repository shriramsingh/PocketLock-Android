package com.example.pocketlock // Match your package name

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = getSharedPreferences("PocketLockPrefs", Context.MODE_PRIVATE)

        setContent {
            var isTripleTapEnabled by remember {
                mutableStateOf(sharedPrefs.getBoolean("use_triple_tap", false))
            }
            var isProximityEnabled by remember {
                mutableStateOf(sharedPrefs.getBoolean("use_proximity_sensor", true))
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Pocket Lock Settings", fontSize = 24.sp, modifier = Modifier.padding(bottom = 30.dp))

                // 1. Triple-Tap Toggle Switch Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Text(text = "Require Triple-Tap to unlock", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(15.dp))
                    Switch(
                        checked = isTripleTapEnabled,
                        onCheckedChange = { checked ->
                            isTripleTapEnabled = checked
                            sharedPrefs.edit().putBoolean("use_triple_tap", checked).commit()
                        }
                    )
                }

                // 2. Proximity Automation Switch Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 40.dp)
                ) {
                    Text(text = "Enable Proximity Sensor Automation", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(15.dp))
                    Switch(
                        checked = isProximityEnabled,
                        onCheckedChange = { checked ->
                            isProximityEnabled = checked
                            sharedPrefs.edit().putBoolean("use_proximity_sensor", checked).commit()
                        }
                    )
                }

                Button(onClick = { startPocketLock() }) {
                    Text(text = "Activate Pocket Lock", fontSize = 18.sp)
                }
            }
        }
    }

    private fun startPocketLock() {
        // Check if we have permission to draw overlays
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please grant permission to overlay apps", Toast.LENGTH_LONG).show()
        }
        else {
            // Permission is granted, start our background blocking service
            val serviceIntent = Intent(this, PocketLockService::class.java).apply {
                val livePrefs = getSharedPreferences("PocketLockPrefs", Context.MODE_PRIVATE)
                putExtra("EXTRA_USE_TRIPLE_TAP", livePrefs.getBoolean("use_triple_tap", false))
                putExtra("EXTRA_USE_PROXIMITY", livePrefs.getBoolean("use_proximity_sensor", true))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            // Ask Android to add the Quick Settings Tile automatically
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val statusBarManager = getSystemService(StatusBarManager::class.java)
                statusBarManager?.requestAddTileService(
                    ComponentName(this, PocketTileService::class.java),
                    "Pocket Lock",
                    Icon.createWithResource(this, android.R.drawable.ic_secure),
                    { executor -> executor.run { } },
                    { result -> }
                )
            }

            // Send user to home screen immediately
            val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            startActivity(homeIntent)
        }
    }
}