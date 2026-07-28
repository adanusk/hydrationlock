package com.hydrationlock.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hydrationlock.app.data.BlockedAppsRepository
import com.hydrationlock.app.history.HistoryActivity
import com.hydrationlock.app.schedule.AlarmScheduler
import com.hydrationlock.app.schedule.HydrationNotifier
import com.hydrationlock.app.schedule.ScheduleRepository
import java.time.LocalTime

/**
 * FASE 6.
 *
 * En vez de mostrar TODAS las apps instaladas (que era ruidoso y fácil de
 * marcar por error), el MVP se enfoca solo en estas redes sociales
 * específicas. Si alguna no está instalada en el celular, simplemente no
 * aparece en la lista — no genera error.
 */
private val TARGET_SOCIAL_APPS = listOf(
    "com.instagram.android",
    "com.linkedin.android",
    "com.twitter.android", // X (el packageName histórico de Twitter se mantuvo tras el rebranding)
    "com.zhiliaoapp.musically", // TikTok (versión global)
    "com.ss.android.ugc.trill", // TikTok (variante regional, algunos mercados)
    "com.zhiliaoapp.musically.go", // TikTok Lite
    "com.facebook.katana",
    "com.snapchat.android",
    "com.google.android.youtube"
)

/**
 * FASE 0/1/3 — pantalla mínima para:
 * 1. Pedir el permiso de Accessibility (requerido por AppWatcherService)
 * 2. Pedir el permiso de overlay (requerido por OverlayService)
 * 3. Definir hasta 3 horarios de hidratación (Fase 3)
 * 4. Elegir qué apps instaladas bloquear
 *
 * El diseño visual se pule en Fase 6. Esto es solo funcional.
 */
class MainActivity : ComponentActivity() {

    private lateinit var blockedAppsRepository: BlockedAppsRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var alarmScheduler: AlarmScheduler

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockedAppsRepository = BlockedAppsRepository(applicationContext)
        scheduleRepository = ScheduleRepository(applicationContext)
        alarmScheduler = AlarmScheduler(applicationContext)

        HydrationNotifier.ensureChannel(applicationContext)
        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SetupScreen(
                        blockedAppsRepository = blockedAppsRepository,
                        scheduleRepository = scheduleRepository,
                        alarmScheduler = alarmScheduler,
                        onOpenAccessibilitySettings = { openAccessibilitySettings() },
                        onOpenOverlaySettings = { openOverlaySettings() },
                        onOpenHistory = { startActivity(Intent(this, HistoryActivity::class.java)) }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}

@Composable
fun SetupScreen(
    blockedAppsRepository: BlockedAppsRepository,
    scheduleRepository: ScheduleRepository,
    alarmScheduler: AlarmScheduler,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var blockedPackages by remember { mutableStateOf(blockedAppsRepository.getBlockedPackages()) }
    var schedules by remember { mutableStateOf(scheduleRepository.getSchedules()) }

    // Limpieza única: si quedaron apps bloqueadas de antes de este cambio
    // (cuando la lista mostraba TODAS las apps instaladas), las saca del
    // set bloqueado para que solo queden las 6 redes sociales objetivo.
    LaunchedEffect(Unit) {
        val cleaned = blockedPackages.intersect(TARGET_SOCIAL_APPS.toSet())
        if (cleaned != blockedPackages) {
            blockedPackages = cleaned
            blockedAppsRepository.setBlockedPackages(cleaned)
        }
    }

    val installedApps = remember {
        val pm = context.packageManager
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .associateBy { it.packageName }

        TARGET_SOCIAL_APPS.mapNotNull { packageName -> allApps[packageName] }
            .sortedBy { pm.getApplicationLabel(it).toString() }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("HydrationLock — MVP", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "1. Activa Accesibilidad y Superposición. 2. Define tus horarios. " +
                    "3. Elige qué apps bloquear.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))
            Row {
                Button(onClick = onOpenAccessibilitySettings) {
                    Text("Activar Accesibilidad")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onOpenOverlaySettings) {
                    Text("Activar Superposición")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenHistory) {
                Text("Ver historial")
            }

            Spacer(Modifier.height(24.dp))
            Text("Horarios de hidratación (hasta 3):", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            ScheduleEditor(
                schedules = schedules,
                onSchedulesChanged = { updated ->
                    schedules = updated
                    scheduleRepository.setSchedules(updated)
                    alarmScheduler.scheduleAll(updated)
                }
            )

            Spacer(Modifier.height(24.dp))
            Text("Apps para bloquear:", style = MaterialTheme.typography.titleMedium)
            if (installedApps.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "No se encontró ninguna de las apps objetivo (Instagram, LinkedIn, X, " +
                        "TikTok, Facebook, Snapchat, YouTube) instalada en este celular.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        items(installedApps) { app: ApplicationInfo ->
            val pm = context.packageManager
            val label = pm.getApplicationLabel(app).toString()
            val isBlocked = blockedPackages.contains(app.packageName)

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isBlocked,
                    onCheckedChange = { checked ->
                        val updated = blockedPackages.toMutableSet()
                        if (checked) updated.add(app.packageName) else updated.remove(app.packageName)
                        blockedPackages = updated
                        blockedAppsRepository.setBlockedPackages(updated)
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
    }
}

/**
 * Muestra hasta 3 "slots" de horario. Cada uno abre un TimePickerDialog
 * nativo de Android al tocarlo (más simple que armar un picker propio en
 * Compose para el MVP).
 */
@Composable
fun ScheduleEditor(
    schedules: List<LocalTime>,
    onSchedulesChanged: (List<LocalTime>) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column {
        for (slotIndex in 0 until ScheduleRepository.MAX_SCHEDULES) {
            val currentTime = schedules.getOrNull(slotIndex)

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Horario ${slotIndex + 1}: ", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))

                OutlinedButton(onClick = {
                    val initial = currentTime ?: LocalTime.of(10, 0)
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val newTime = LocalTime.of(hour, minute)
                            val updated = schedules.toMutableList()
                            while (updated.size <= slotIndex) updated.add(LocalTime.of(0, 0))
                            updated[slotIndex] = newTime
                            onSchedulesChanged(updated.take(ScheduleRepository.MAX_SCHEDULES))
                        },
                        initial.hour, initial.minute, true
                    ).show()
                }) {
                    Text(currentTime?.toString() ?: "Sin definir")
                }

                if (currentTime != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        val updated = schedules.toMutableList()
                        if (slotIndex < updated.size) updated.removeAt(slotIndex)
                        onSchedulesChanged(updated)
                    }) {
                        Text("Quitar")
                    }
                }
            }
        }
    }
}
