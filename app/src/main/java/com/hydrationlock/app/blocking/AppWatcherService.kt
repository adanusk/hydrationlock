package com.hydrationlock.app.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.hydrationlock.app.data.BlockedAppsRepository

/**
 * FASE 1 del plan.
 *
 * Este servicio no lee contenido de pantalla (canRetrieveWindowContent=false
 * en accessibility_service_config.xml) — solo escucha el evento
 * TYPE_WINDOW_STATE_CHANGED, que Android dispara cada vez que una nueva
 * ventana/app pasa a primer plano. De ahí sacamos el packageName.
 *
 * Si el packageName está en la lista de apps bloqueadas y no tiene un
 * desbloqueo temporal vigente, lanzamos el OverlayService (Fase 2) que
 * dibuja la pantalla de "completa el hábito para continuar" encima.
 */
class AppWatcherService : AccessibilityService() {

    private lateinit var blockedAppsRepository: BlockedAppsRepository

    // Evita relanzar el overlay repetidamente por eventos duplicados del
    // mismo package en un intervalo muy corto.
    private var lastHandledPackage: String? = null
    private var lastHandledAtMillis: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        blockedAppsRepository = BlockedAppsRepository(applicationContext)
        Log.d(TAG, "AppWatcherService conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Ignora eventos de nuestra propia app (evita loop al mostrar el overlay)
        if (packageName == applicationContext.packageName) return

        val blockedPackages = blockedAppsRepository.getBlockedPackages()
        if (packageName !in blockedPackages) return

        if (blockedAppsRepository.isCurrentlyUnlocked(packageName)) {
            Log.d(TAG, "$packageName está bloqueada pero con desbloqueo temporal vigente")
            return
        }

        val now = System.currentTimeMillis()
        if (packageName == lastHandledPackage && now - lastHandledAtMillis < DEBOUNCE_MS) {
            return
        }
        lastHandledPackage = packageName
        lastHandledAtMillis = now

        Log.d(TAG, "Apertura detectada de app bloqueada: $packageName -> lanzando overlay")
        launchOverlay(packageName)
    }

    private fun launchOverlay(packageName: String) {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_TARGET_PACKAGE, packageName)
        }
        startService(intent)
    }

    override fun onInterrupt() {
        Log.d(TAG, "AppWatcherService interrumpido")
    }

    companion object {
        private const val TAG = "AppWatcherService"
        private const val DEBOUNCE_MS = 1500L
    }
}
