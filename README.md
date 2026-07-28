# HydrationLock — MVP

Prototipo Android para validar dos hipótesis:
1. ¿Se puede detectar de forma confiable (mano cerca de la boca) que alguien tomó agua, usando solo la cámara?
2. ¿Bloquear apps de redes sociales hasta completar ese hábito reduce el uso compulsivo?

## Estado actual (lo generado hoy)

- ✅ Fase 0 — Setup del proyecto (Gradle, Compose, dependencias de CameraX/ML Kit/Room ya declaradas)
- ✅ Fase 1 — `AppWatcherService` (AccessibilityService) detecta cuándo se abre una app bloqueada
- ✅ Fase 2 — `OverlayService` lanza la verificación real con cámara y se auto-cierra al completarse
- ✅ Fase 3 — Horarios configurables (hasta 3) + snoozes (máx 3, +10 min c/u) + registro en Room de cada evento (completado/perdido)
- ✅ Fase 4 — `VerificationActivity`: cámara en vivo (CameraX) + ML Kit Pose Detection, heurística "muñeca cerca de la nariz sostenida 1.5s". Reemplaza los placeholders del overlay y de la notificación.
- ✅ Fase 5 — `HistoryActivity`: resumen de hoy (completados/perdidos/pendientes), racha de días consecutivos, lista de eventos recientes. Botón "Ver historial" en la pantalla principal.
- ⬜ Fase 6 — Pulido UX
- ⬜ Fase 7 — Validación con usuarios

## Cómo abrir el proyecto

1. Instala Android Studio: https://developer.android.com/studio
2. Abre Android Studio → **Open** → selecciona la carpeta `HydrationLock/`
3. Espera a que sincronice Gradle (primera vez puede tardar varios minutos, descarga dependencias)
4. Conecta tu celular Android por USB con "Depuración USB" activada (Ajustes → Acerca del teléfono → toca 7 veces "Número de compilación" para activar Opciones de Desarrollador), o crea un emulador desde Device Manager
5. Dale ▶️ Run

**Importante**: `AccessibilityService` y overlays de sistema **no funcionan bien en el emulador** en muchos casos — para probar el flujo real de bloqueo, mejor usar un celular físico.

## Probar el flujo end-to-end (con el placeholder actual)

1. Al abrir la app, dale a **"Activar Accesibilidad"** → en la pantalla de Ajustes que se abre, busca "HydrationLock" y actívalo
2. Vuelve a la app, dale a **"Activar Superposición"** → activa el permiso de "Mostrar sobre otras apps"
3. En la lista de apps, marca el checkbox de alguna app instalada (ej. Chrome, para probar sin necesitar Instagram)
4. Sal de HydrationLock y abre la app que marcaste
5. Debería aparecer el overlay azul pidiendo tomar agua
6. Toca "Ya tomé agua (simulado)" → el overlay desaparece y tienes 15 min de acceso libre a esa app antes de que vuelva a pedirlo

## Probar los horarios (Fase 3)

1. Abre la app, dale a "Horario 1" (o 2/3) y elige una hora **1-2 minutos en el futuro** (para no esperar de verdad)
2. Sal de la app o deja el celular con pantalla apagada
3. A la hora exacta debería salir una notificación "Hora de tomar agua 💧" con botones "Completar" y "Posponer"
4. Prueba "Posponer" varias veces — a la 3ra vez debería dejar de reprogramar y marcar el evento como perdido (revisable luego en la base de datos, aún sin pantalla de historial)
5. Prueba "Completar" — el evento queda como COMPLETED

**Nota**: "Completar" desde la notificación por ahora confía en la palabra del usuario (no hay cámara todavía) — eso se conecta en Fase 4.

## Probar la verificación con cámara (Fase 4) — REQUIERE CELULAR FÍSICO

CameraX y ML Kit no funcionan bien en el emulador (la cámara virtual no da
suficiente señal para pose detection). Con celular físico:

1. Marca una app para bloquear, ábrela → toca "Verificar con cámara"
2. Se abre la cámara frontal con una guía de texto abajo
3. Acerca tu mano (sosteniendo algo, o incluso sin nada por ahora — la
   heurística actual solo mira la posición de la muñeca, no si hay un vaso
   real) a la altura de tu nariz/boca y sostenla
4. Debería llenarse una barra de progreso en ~1.5 segundos y la pantalla se
   cierra sola, desbloqueando la app por 15 min
5. Prueba también desde una notificación de horario (botón "Completar")

**Nota de diseño**: el detector actual NO verifica que haya un vaso/botella
real en la mano — solo la posición geométrica de la muñeca respecto a la
nariz. Es intencional para el MVP (ver conversación de diseño): el objetivo
es generar el hábito y la pausa consciente, no impedir hacer trampa. Si más
adelante se quiere sumar detección de objeto (vaso/botella), se puede
entrenar un modelo simple con ML Kit Custom Model o TensorFlow Lite y
sumarlo como una segunda señal en `DrinkGestureAnalyzer`.

## Siguiente sesión

Seguimos con Fase 6 (pulido UX: animaciones, haptics, ícono real, ajuste de
sensibilidad del detector con pruebas reales) — o directo a Fase 7 si ya
tienes el celular Android para probar el flujo completo con gente real.
