package com.hydrationlock.app.verification

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.hypot

/**
 * FASE 4 — núcleo de la hipótesis técnica del proyecto.
 *
 * Heurística deliberadamente simple: no intentamos reconocer "la acción de
 * beber" con un modelo de video/acción entrenado — eso es caro de construir
 * y de correr on-device. En cambio, usamos pose estimation nativo de ML Kit
 * (gratis, rápido, on-device) para verificar una condición geométrica
 * sostenida en el tiempo: "¿la muñeca (mano) estuvo cerca de la nariz
 * (proxy de la boca) durante ~1.5 segundos?" — suficiente para generar el
 * hábito real de pausa y gesto, sin pretender ser una prueba forense.
 *
 * Normalizamos la distancia por el ancho de hombros para que el umbral
 * funcione sin importar qué tan cerca/lejos esté la cara de la cámara.
 */
class DrinkGestureAnalyzer(
    private val onProgress: (progress: Float) -> Unit,
    private val onCompleted: () -> Unit
) {
    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private var gestureStartedAtMillis: Long? = null
    private var completed = false

    fun analyze(imageProxy: ImageProxy) {
        if (completed) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        poseDetector.process(inputImage)
            .addOnSuccessListener { pose -> handlePose(pose) }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun handlePose(pose: Pose) {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        if (nose == null || leftShoulder == null || rightShoulder == null ||
            (leftWrist == null && rightWrist == null)
        ) {
            resetProgress()
            return
        }

        val shoulderWidth = distance(
            leftShoulder.position.x, leftShoulder.position.y,
            rightShoulder.position.x, rightShoulder.position.y
        )
        if (shoulderWidth <= 0f) {
            resetProgress()
            return
        }

        val distances = listOfNotNull(
            leftWrist?.let { distance(it.position.x, it.position.y, nose.position.x, nose.position.y) },
            rightWrist?.let { distance(it.position.x, it.position.y, nose.position.x, nose.position.y) }
        )
        val closestWristDistance = distances.minOrNull()
        if (closestWristDistance == null) {
            resetProgress()
            return
        }

        // Normalizado: la muñeca debe estar a menos de ~0.6x el ancho de
        // hombros de la nariz. Ajustable si en pruebas reales resulta muy
        // estricto o muy laxo.
        val normalizedDistance = closestWristDistance / shoulderWidth
        val isNearMouth = normalizedDistance < PROXIMITY_THRESHOLD

        if (isNearMouth) {
            val startedAt = gestureStartedAtMillis ?: System.currentTimeMillis().also {
                gestureStartedAtMillis = it
            }
            val elapsed = System.currentTimeMillis() - startedAt
            val progress = (elapsed / HOLD_DURATION_MILLIS.toFloat()).coerceIn(0f, 1f)
            onProgress(progress)

            if (elapsed >= HOLD_DURATION_MILLIS && !completed) {
                completed = true
                onCompleted()
            }
        } else {
            resetProgress()
        }
    }

    private fun resetProgress() {
        gestureStartedAtMillis = null
        onProgress(0f)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return hypot((x1 - x2).toDouble(), (y1 - y2).toDouble()).toFloat()
    }

    fun close() {
        poseDetector.close()
    }

    companion object {
        private const val PROXIMITY_THRESHOLD = 0.6f
        private const val HOLD_DURATION_MILLIS = 1500L
    }
}
