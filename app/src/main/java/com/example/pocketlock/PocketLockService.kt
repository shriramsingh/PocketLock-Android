package com.example.pocketlock // Match your package name

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class PocketLockService : Service(), SensorEventListener {

    private var windowManager: WindowManager? = null
    private var parentLayout: FrameLayout? = null
    private var unlockButton: Button? = null
    private var shieldView: View? = null

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var isProximityConfigEnabled = true
    private var isTripleTapConfigEnabled = false
    private var isCurrentlyNear = false

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        dimScreenAndHideButton()
    }

    private var clickCount = 0
    private var lastClickTime: Long = 0
    private val CLICK_TIME_DELTA: Long = 350

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }

        // Read saved data directly from disk safely
        val sharedPrefs = getSharedPreferences("PocketLockPrefs", Context.MODE_PRIVATE)
        isProximityConfigEnabled = sharedPrefs.getBoolean("use_proximity_sensor", true)
        isTripleTapConfigEnabled = sharedPrefs.getBoolean("use_triple_tap", false)

        showForegroundNotification()

        // Only create the shield if it doesn't exist yet to avoid crashes/leaks
        if (parentLayout == null) {
            createAdvancedTouchShield()
        } else {
            // Update UI settings if service was already active
            unlockButton?.text = if (isTripleTapConfigEnabled) "🔒 Triple-Tap" else "🔒 Double-Tap"
        }

        initProximitySensor()

        return START_STICKY
    }
    private fun showForegroundNotification() {
        val channelId = "pocket_lock_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Pocket Lock", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Pocket Lock Shield Active")
            .setContentText(if (isProximityConfigEnabled) "Proximity automation enabled." else "Manual screen guard active.")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun createAdvancedTouchShield() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        parentLayout = FrameLayout(this)

        // 1. Full-Screen Shield Layer
        shieldView = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0.0f

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    wakeScreenAndShowButton()
                }
                true
            }
        }
        parentLayout?.addView(shieldView)

        // 2. Floating Button
        // Find this block inside createAdvancedTouchShield() where unlockButton is defined:
        unlockButton = Button(this).apply {
            // Update this line to use our direct intent tracking variable
            text = if (isTripleTapConfigEnabled) "🔒 Triple-Tap" else "🔒 Double-Tap"

            setBackgroundColor(Color.parseColor("#CC000000"))
            setTextColor(Color.WHITE)
            setPadding(30, 20, 30, 20)
            alpha = 1f
        }

        val buttonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = 100
            topMargin = 200
        }
        parentLayout?.addView(unlockButton, buttonParams)

        // 3. Drag and Tap Logic
        unlockButton?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0f
            private var initialY = 0f
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isMoving = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                wakeScreenAndShowButton()

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = buttonParams.leftMargin.toFloat()
                        initialY = buttonParams.topMargin.toFloat()
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY

                        if (abs(deltaX) > 10 || abs(deltaY) > 10 || isMoving) {
                            isMoving = true
                            buttonParams.leftMargin = (initialX + deltaX).toInt()
                            buttonParams.topMargin = (initialY + deltaY).toInt()
                            windowManager?.updateViewLayout(parentLayout, parentLayout?.layoutParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMoving) {
                            val clickTime = System.currentTimeMillis()
                            // Update this line to read our direct tracking variable
                            val requiredClicks = if (isTripleTapConfigEnabled) 3 else 2

                            if (clickTime - lastClickTime > CLICK_TIME_DELTA) {
                                clickCount = 0
                            }

                            clickCount++
                            lastClickTime = clickTime

                            if (clickCount >= requiredClicks) {
                                clickCount = 0
                                stopSelf()
                            } else {
                                val remaining = requiredClicks - clickCount
                                unlockButton?.text = if (remaining == 1) "👆 Tap Once More!" else "👆 Tap $remaining Times!"

                                Handler(Looper.getMainLooper()).postDelayed({
                                    unlockButton?.text = if (isTripleTapConfigEnabled) "🔒 Triple-Tap" else "🔒 Double-Tap"
                                }, 1000)
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        // 4. Mount Window Layout
        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(parentLayout, windowParams)
        resetHideTimer()
    }

    private fun initProximitySensor() {
        if (sensorManager == null) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        }

        // Always unregister first to avoid duplicate listeners or cleanup if disabled
        sensorManager?.unregisterListener(this)

        if (isProximityConfigEnabled) {
            proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

            if (proximitySensor == null) {
                Log.e("PocketLock", "Proximity sensor not found on this device!")
                android.widget.Toast.makeText(this, "Proximity sensor not detected!", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
                Log.d("PocketLock", "Proximity sensor registered.")
            }
        } else {
            // If proximity is disabled, ensure we are in a "Far" state for the logic
            isCurrentlyNear = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isProximityConfigEnabled || event == null) return

        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val distanceValue = event.values[0]
            val maxRange = event.sensor.maximumRange
            Log.d("PocketLock", "Proximity event: value=$distanceValue, maxRange=$maxRange")

            // Determine if something is "Near" the sensor
            // Most sensors use 0.0 for near. We'll use a threshold to be safe.
            val nowNear = distanceValue < maxRange && distanceValue <= 3.0f

            if (nowNear != isCurrentlyNear) {
                isCurrentlyNear = nowNear
                if (isCurrentlyNear) {
                    Log.d("PocketLock", "Pocket detected! Dimming.")
                    dimScreenAndHideButton()
                } else {
                    Log.d("PocketLock", "Out of pocket! Waking up.")
                    wakeScreenAndShowButton()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun dimScreenAndHideButton() {
        unlockButton?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
            unlockButton?.visibility = View.INVISIBLE
        }?.start()
        shieldView?.animate()?.alpha(0.98f)?.setDuration(300)?.start()
    }

    private fun wakeScreenAndShowButton() {
        shieldView?.animate()?.alpha(0.0f)?.setDuration(150)?.start()

        if (unlockButton?.visibility != View.VISIBLE || unlockButton?.alpha == 0f) {
            unlockButton?.visibility = View.VISIBLE
            unlockButton?.animate()?.alpha(1f)?.setDuration(150)?.start()
        }

        // IMPORTANT: If proximity is enabled and we are "Far", do NOT start the auto-hide timer.
        // This keeps the screen awake while you are holding it.
        if (isProximityConfigEnabled && !isCurrentlyNear) {
            hideHandler.removeCallbacks(hideRunnable)
        } else {
            resetHideTimer()
        }
    }

    private fun resetHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideRunnable)

        if (isProximityConfigEnabled) {
            sensorManager?.unregisterListener(this)
        }

        if (parentLayout != null && windowManager != null) {
            windowManager?.removeView(parentLayout)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(this, ComponentName(this, PocketTileService::class.java))
        }
    }
}