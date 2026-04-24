package com.example.regresoacasa.core.safety

/**
 * SLA de Seguridad - Service Level Agreement
 * 
 * Estas constantes definen los umbrales críticos del sistema.
 * NO deben modificarse sin revisión de seguridad.
 */
object SafetyConstants {
    
    // ==================== GPS TIMEOUTS ====================
    
    /**
     * Tiempo sin GPS antes de WARNING
     * 60 segundos = tiempo razonable para túneles cortos o edificios
     */
    const val GPS_TIMEOUT_WARNING = 60000L
    
    /**
     * Tiempo sin GPS antes de CRITICAL
     * 120 segundos = indica fallo grave o dispositivo apagado
     */
    const val GPS_TIMEOUT_CRITICAL = 120000L
    
    /**
     * Intervalo de verificación del watchdog
     * 10 segundos = balance entre detección rápida y consumo de batería
     */
    const val WATCHDOG_CHECK_INTERVAL = 10000L
    
    // ==================== ALERT RETRY ====================
    
    /**
     * Máximo número de reintentos para envío de alertas
     * 3 reintentos = suficiente para superar fallos temporales de red
     */
    const val ALERT_RETRY_LIMIT = 3
    
    /**
     * Delay entre reintentos de alertas (backoff base)
     * 5 segundos = tiempo para que la red se recupere
     */
    const val ALERT_RETRY_DELAY_BASE = 5000L
    
    /**
     * Timeout para entrega de SMS
     * 30 segundos = tiempo máximo para considerar delivery fallido
     */
    const val SMS_DELIVERY_TIMEOUT = 30000L
    
    // ==================== GPS PRECISION ====================
    
    /**
     * Precisión GPS mínima para navegación confiable
     * 25 metros = precisión aceptable para navegación peatonal
     */
    const val GPS_PRECISION_NAVIGABLE = 25f
    
    /**
     * Precisión GPS mínima para tracking básico
     * 50 metros = precisión aceptable para tracking general
     */
    const val GPS_PRECISION_TRACKING = 50f
    
    /**
     * Precisión GPS considerada degradada
     * 100 metros = GPS pobre, solo para fallback
     */
    const val GPS_PRECISION_DEGRADED = 100f
    
    // ==================== VELOCITY VALIDATION ====================
    
    /**
     * Velocidad máxima considerada realista (m/s)
     * 100 m/s = 360 km/h, cualquier cosa mayor es spoofing
     */
    const val MAX_REALISTIC_VELOCITY_MS = 100.0
    
    /**
     * Velocidad máxima para peatón (m/s)
     * 10 m/s = 36 km/h, sprint rápido
     */
    const val MAX_PEDESTRIAN_VELOCITY_MS = 10.0
    
    // ==================== PERSISTENCE ====================
    
    /**
     * TTL para snapshots de seguridad en persistencia
     * 24 horas = tiempo para recuperar sesiones interrumpidas
     */
    const val SNAPSHOT_TTL = 86400000L
    
    /**
     * Intervalo de guardado de snapshots
     * Cada cambio importante de estado debe persistirse
     */
    const val SNAPSHOT_SAVE_INTERVAL = 30000L
    
    // ==================== DEGRADATION MODES ====================
    
    /**
     * Tiempo sin internet antes de cambiar a modo NO_INTERNET
     * 30 segundos = tiempo para detectar pérdida de red
     */
    const val INTERNET_LOSS_THRESHOLD = 30000L
    
    /**
     * Número de ubicaciones degradadas consecutivas antes de modo LOW_GPS
     * 5 = patrón consistente de GPS pobre
     */
    const val DEGRADED_GPS_COUNT_THRESHOLD = 5
    
    // ==================== BATTERY ====================
    
    /**
     * Nivel de batería para activar modo ahorro
     * 20% = umbral crítico de batería
     */
    const val BATTERY_LOW_THRESHOLD = 20
    
    /**
     * Nivel de batería para modo crítico
     * 10% = batería casi agotada
     */
    const val BATTERY_CRITICAL_THRESHOLD = 10
    
    // ==================== MOCK LOCATION DETECTION ====================
    
    /**
     * Diferencia de tiempo máxima entre providers
     * 1000ms = si GPS y Network provider difieren por más de 1s, es sospechoso
     */
    const val PROVIDER_TIME_DIFF_THRESHOLD = 1000L
    
    /**
     * Número de saltos irreales consecutivos para detectar spoofing
     * 3 = patrón consistente de ubicaciones imposibles
     */
    const val SPOOFING_DETECTION_THRESHOLD = 3
}
