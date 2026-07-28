package com.hydrationlock.app.blocking

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.hydrationlock.app.verification.HabitCompletionBroadcast
import com.hydrationlock.app.verification.VerificationActivity

/**
 * FASE 2 + FASE 4.
 *
 * Dibuja una pantalla completa encima de la app bloqueada, con un botón que
 * abre VerificationActivity (cámara real + ML Kit). Si la verificación se
 * completa exitosamente, este servicio recibe el broadcast y se cierra
 * solo, dejando pasar a la app original.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: LinearLayout? = null
    private var currentTargetPackage: String? = null

    private val habitCompletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val completedPackage = intent?.getStringExtra(HabitCompletionBroadcast.EXTRA_TARGET_PACKAGE)
            if (completedPackage != null && completedPackage == currentTargetPackage) {
                dismissOverlay()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ContextCompat.registerReceiver(
            this,
            habitCompletedReceiver,
            IntentFilter(HabitCompletionBroadcast.ACTION_HABIT_COMPLETED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetPackage = intent?.getStringExtra(EXTRA_TARGET_PACKAGE) ?: return START_NOT_STICKY
        currentTargetPackage = targetPackage

        if (overlayView == null) {
            showOverlay(targetPackage)
        }
        return START_STICKY
    }

    private fun showOverlay(targetPackage: String) {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            0, // focusable, recibe touch normalmente
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F0102A43"))
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val message = TextView(this).apply {
            text = "Toma un vaso de agua para desbloquear esta app 💧"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }

        val verifyButton = Button(this).apply {
            text = "Verificar con cámara"
            setOnClickListener {
                val intent = Intent(this@OverlayService, VerificationActivity::class.java).apply {
                    putExtra(VerificationActivity.EXTRA_TARGET_PACKAGE, targetPackage)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
        }

        container.addView(message)
        container.addView(verifyButton)

        overlayView = container
        windowManager.addView(container, params)
    }

    private fun dismissOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(habitCompletedReceiver) }
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }
}
