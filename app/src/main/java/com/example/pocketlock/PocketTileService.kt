package com.example.pocketlock // Match your package name

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class PocketTileService : TileService() {

    // This updates the button's color/state when the notification shade is pulled down
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        if (Settings.canDrawOverlays(this)) {
            val isServiceRunning = isServiceRunning(PocketLockService::class.java)

            if (isServiceRunning) {
                // If it's already active, clicking it again will stop the lock shield
                val stopIntent = Intent(this, PocketLockService::class.java).apply {
                    action = "STOP_SERVICE"
                }
                startService(stopIntent)

                // Update button color to grey instantly
                qsTile.state = Tile.STATE_INACTIVE
                qsTile.updateTile()
            } else {
                // If it's off, use the official API to start it safely
                // Find this exact block inside your PocketTileService onClick() function:
                unlockAndRun {
                    // Reverted back to a simple clean intent call
                    val startIntent = Intent(this, PocketLockService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(startIntent)
                    } else {
                        startService(startIntent)
                    }

                    qsTile.state = Tile.STATE_ACTIVE
                    qsTile.updateTile()
                }            }
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        if (isServiceRunning(PocketLockService::class.java)) {
            tile.state = Tile.STATE_ACTIVE // Highlights the button (Blue/Green/White)
            tile.subtitle = "Active"
        } else {
            tile.state = Tile.STATE_INACTIVE // Grays out the button
            tile.subtitle = "Off"
        }
        tile.updateTile() // Pushes the visual changes to the UI
    }

    // Helper function to check if the background service is actively running
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}