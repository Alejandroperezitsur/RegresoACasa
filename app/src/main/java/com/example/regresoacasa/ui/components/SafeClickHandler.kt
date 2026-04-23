package com.example.regresoacasa.ui.components

/**
 * FASE 4: Debounce global de acciones - Prevenir spam de botones
 * Cooldown: 800ms
 */
class SafeClickHandler {
    private var lastClickTime = 0L
    private val cooldown = 800L // 800ms cooldown
    
    /**
     * Ejecuta acción solo si ha pasado el cooldown
     * @param action Acción a ejecutar
     * @return true si se ejecutó, false si fue bloqueado por cooldown
     */
    fun safeClick(action: () -> Unit): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= cooldown) {
            lastClickTime = currentTime
            action()
            return true
        }
        return false
    }
    
    /**
     * Reset manual del cooldown (para casos especiales)
     */
    fun reset() {
        lastClickTime = 0L
    }
}
