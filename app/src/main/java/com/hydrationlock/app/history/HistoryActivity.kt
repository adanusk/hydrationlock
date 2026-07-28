package com.hydrationlock.app.history

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hydrationlock.app.data.db.HydrationEvent
import com.hydrationlock.app.data.db.HydrationEventStatus
import com.hydrationlock.app.data.db.HydrationStats
import com.hydrationlock.app.data.db.HydrationStatsRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * FASE 5.
 *
 * Lee los HydrationEvent que ya se vienen guardando desde Fase 3 (cada vez
 * que se dispara un horario, se completa o se pierde) y muestra un resumen.
 * No agrega tracking nuevo — es una vista sobre datos existentes.
 */
class HistoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HydrationStatsRepository(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HistoryScreen(repository)
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(repository: HydrationStatsRepository) {
    var stats by remember { mutableStateOf<HydrationStats?>(null) }
    var events by remember { mutableStateOf<List<HydrationEvent>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            stats = repository.getTodayStats()
            events = repository.getRecentEvents()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Tu historial de hidratación", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            stats?.let { s ->
                StatsSummary(s)
            } ?: run {
                CircularProgressIndicator()
            }

            Spacer(Modifier.height(24.dp))
            Text("Eventos recientes:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }

        if (events.isEmpty()) {
            item {
                Text(
                    "Todavía no hay eventos registrados. Define un horario en la pantalla principal.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        items(events) { event ->
            EventRow(event)
        }
    }
}

@Composable
private fun StatsSummary(stats: HydrationStats) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatCard(label = "Hoy completados", value = stats.completedToday.toString())
        StatCard(label = "Hoy perdidos", value = stats.missedToday.toString())
        StatCard(label = "Racha (días)", value = stats.streakDays.toString())
    }
    if (stats.pendingToday > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Tienes ${stats.pendingToday} horario(s) pendiente(s) hoy.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(modifier = Modifier.padding(4.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EventRow(event: HydrationEvent) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("dd/MM HH:mm") }
    val triggeredAt = remember(event.triggeredAtMillis) {
        LocalDateTime.ofInstant(
            Instant.ofEpochMilli(event.triggeredAtMillis),
            ZoneId.systemDefault()
        ).format(timeFormatter)
    }

    val (statusLabel, statusEmoji) = when (event.status) {
        HydrationEventStatus.COMPLETED -> "Completado" to "✅"
        HydrationEventStatus.MISSED -> "Perdido" to "❌"
        HydrationEventStatus.PENDING -> "Pendiente" to "⏳"
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Horario ${event.scheduledTime} — disparado $triggeredAt")
            if (event.snoozeCount > 0) {
                Text(
                    "Pospuesto ${event.snoozeCount} vez/veces",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text("$statusEmoji $statusLabel")
    }
    Divider()
}
