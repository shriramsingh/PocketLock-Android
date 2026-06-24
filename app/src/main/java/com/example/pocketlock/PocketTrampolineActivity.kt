package com.example.pocketlock // Match your actual package name

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle

class PocketTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Instantly trigger your locking shield service
        val serviceIntent = Intent(this, PocketLockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // 2. Close this helper activity instantly so the user doesn't see it
        finish()
    }
}