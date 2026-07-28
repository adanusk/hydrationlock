package com.hydrationlock.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Guarda qué apps (por package name, ej "com.instagram.android") el usuario
 * eligió bloquear. Usa SharedPreferences porque es una lista chica y de
 * lectura frecuente desde el AccessibilityService — no vale la pena Room aquí.
 */
class BlockedAppsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBlockedPackages(): Set<String> {
        return prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
    }

    fun setBlockedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, packages).apply()
    }

    fun addBlockedPackage(packageName: String) {
        val current = getBlockedPackages().toMutableSet()
        current.add(packageName)
        setBlockedPackages(current)
    }

    fun removeBlockedPackage(packageName: String) {
        val current = getBlockedPackages().toMutableSet()
        current.remove(packageName)
        setBlockedPackages(current)
    }

    /**
     * Fase 3 se conecta aquí: cuando el hábito se completa, se llama a esto
     * para dar acceso temporal sin volver a pedir el hábito por X minutos.
     */
    fun setUnlockedUntil(packageName: String, unlockedUntilMillis: Long) {
        prefs.edit().putLong(KEY_PREFIX_UNLOCK + packageName, unlockedUntilMillis).apply()
    }

    fun isCurrentlyUnlocked(packageName: String): Boolean {
        val unlockedUntil = prefs.getLong(KEY_PREFIX_UNLOCK + packageName, 0L)
        return System.currentTimeMillis() < unlockedUntil
    }

    companion object {
        private const val PREFS_NAME = "hydrationlock_blocked_apps"
        private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
        private const val KEY_PREFIX_UNLOCK = "unlocked_until_"
    }
}
