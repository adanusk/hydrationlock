package com.hydrationlock.app.verification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.hydrationlock.app.data.BlockedAppsRepository
import com.hydrationlock.app.data.db.HydrationDatabase
import com.hydrationlock.app.data.db.HydrationEventStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FASE 4.
 *
 * Pantalla de verificación real. Se puede abrir con uno de dos extras
 * (según de dónde venga):
 * - EXTRA_TARGET_PACKAGE: viene del OverlayService (Fase 2) → al validar,
 *   desbloquea esa app por 15 min.
 * - EXTRA_EVENT_ID: viene de la notificación de horario (Fase 3) → al
 *   validar, marca ese HydrationEvent como COMPLETED.
 *
 * Al validar en cualquiera de los dos casos, también emite
 * ACTION_HABIT_COMPLETED para que el OverlayService se cierre solo si
 * estaba mostrando la pantalla de bloqueo detrás.
 */
class VerificationActivity : ComponentActivity() {

    private var targetPackage: String? = null
    private var eventId: Long = -1L

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraPermissionGranted = granted
        }

    private var cameraPermissionGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)

        cameraPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!cameraPermissionGranted) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (cameraPermissionGranted) {
                        VerificationScreen(onVerified = ::handleVerified)
                    } else {
                        PermissionDeniedScreen(onRetry = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        })
                    }
                }
            }
        }
    }

    private fun handleVerified() {
        CoroutineScope(Dispatchers.IO).launch {
            targetPackage?.let { pkg ->
                val unlockUntil = System.currentTimeMillis() + UNLOCK_DURATION_MILLIS
                BlockedAppsRepository(applicationContext).setUnlockedUntil(pkg, unlockUntil)
            }
            if (eventId != -1L) {
                val dao = HydrationDatabase.getInstance(applicationContext).hydrationEventDao()
                dao.getById(eventId)?.let { event ->
                    dao.update(
                        event.copy(
                            status = HydrationEventStatus.COMPLETED,
                            resolvedAtMillis = System.currentTimeMillis()
                        )
                    )
                }
            }

            val broadcast = Intent(HabitCompletionBroadcast.ACTION_HABIT_COMPLETED).apply {
                setPackage(packageName) // broadcast local, no sale de la app
                targetPackage?.let { putExtra(HabitCompletionBroadcast.EXTRA_TARGET_PACKAGE, it) }
                if (eventId != -1L) putExtra(HabitCompletionBroadcast.EXTRA_EVENT_ID, eventId)
            }
            sendBroadcast(broadcast)

            runOnUiThread {
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_EVENT_ID = "extra_event_id"
        private const val UNLOCK_DURATION_MILLIS = 15 * 60 * 1000L
    }
}

@Composable
private fun VerificationScreen(onVerified: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var progress by remember { mutableFloatStateOf(0f) }
    var verified by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analyzer = DrinkGestureAnalyzer(
                        onProgress = { p -> progress = p },
                        onCompleted = {
                            if (!verified) {
                                verified = true
                                onVerified()
                            }
                        }
                    )

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                analyzer.analyze(imageProxy)
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Overlay de guía + progreso, encima del preview de cámara
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Acerca un vaso o botella a tu boca y sostenlo un momento 💧",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
        }
    }
}

@Composable
private fun PermissionDeniedScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "HydrationLock necesita acceso a la cámara para verificar el hábito.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Dar permiso")
        }
    }
}
