package com.hydrationlock.app.verification

/**
 * FASE 4.
 *
 * Acción de broadcast local (misma app) que se dispara cuando la
 * verificación con cámara tiene éxito. El OverlayService la escucha para
 * cerrarse automáticamente si el hábito se completó mientras estaba
 * mostrando la pantalla de bloqueo.
 */
object HabitCompletionBroadcast {
    const val ACTION_HABIT_COMPLETED = "com.hydrationlock.app.ACTION_HABIT_COMPLETED"
    const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    const val EXTRA_EVENT_ID = "extra_event_id"
}
