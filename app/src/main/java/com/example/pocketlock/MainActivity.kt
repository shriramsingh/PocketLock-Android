package com.example.pocketlock

import android.app.ActivityManager
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pocketlock.ui.theme.PocketLockTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        
        // Force the splash screen to stay for at least 1 second
        var isReady = false
        splashScreen.setKeepOnScreenCondition { !isReady }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isReady = true
        }, 1000)

        super.onCreate(savedInstanceState)

        val sharedPrefs = getSharedPreferences("PocketLockPrefs", Context.MODE_PRIVATE)

        setContent {
            PocketLockTheme {
                var isTripleTapEnabled by remember {
                    mutableStateOf(sharedPrefs.getBoolean("use_triple_tap", false))
                }
                var isProximityEnabled by remember {
                    mutableStateOf(sharedPrefs.getBoolean("use_proximity_sensor", true))
                }
                val isServiceRunning = remember { 
                    mutableStateOf(isServiceRunning(PocketLockService::class.java)) 
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        "Pocket Lock",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            StatusCard(isServiceRunning.value)

                            Spacer(modifier = Modifier.height(32.dp))

                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.fillMaxWidth(),
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsToggleCard(
                                icon = Icons.Default.TouchApp,
                                title = "Triple-Tap Unlock",
                                description = "Require three taps to unlock.",
                                checked = isTripleTapEnabled,
                                onCheckedChange = {
                                    isTripleTapEnabled = it
                                    sharedPrefs.edit().putBoolean("use_triple_tap", it).apply()
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsToggleCard(
                                icon = Icons.Default.Sensors,
                                title = "Proximity Sensor",
                                description = "Auto-lock when covered.",
                                checked = isProximityEnabled,
                                onCheckedChange = {
                                    isProximityEnabled = it
                                    sharedPrefs.edit().putBoolean("use_proximity_sensor", it).apply()
                                }
                            )

                            Spacer(modifier = Modifier.height(48.dp))

                            Button(
                                onClick = { 
                                    startPocketLock()
                                    isServiceRunning.value = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Activate Pocket Lock", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Text(
                                text = "Prevents accidental touches while your phone is in your pocket.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusCard(isRunning: Boolean) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) Color.Green else Color.Gray)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isRunning) "Service is running" else "Service is inactive",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    @Composable
    private fun SettingsToggleCard(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        description: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun startPocketLock() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val statusBarManager = getSystemService(StatusBarManager::class.java)
                statusBarManager?.requestAddTileService(
                    ComponentName(this, PocketTileService::class.java),
                    "Pocket Lock",
                    Icon.createWithResource(this, R.drawable.ic_tile_pocketlock),
                    { executor -> executor.run { } },
                    { result -> }
                )
            }
            val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            startActivity(homeIntent)
        }
    }
}