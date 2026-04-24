package com.example.regresoacasa.core.safety.security

/**
 * V3 FASE 0 — THREAT MODEL
 * 
 * Define explícitamente los actores de amenaza y superficies de ataque.
 * Cada módulo debe defender al menos una superficie.
 */

/**
 * Actores de amenaza que el sistema debe defender
 */
enum class ThreatActor {
    /**
     * Usuario casual - uso normal de la app
     * Riesgo: errores operacionales, baja batería, pérdida de señal
     */
    CASUAL_USER,
    
    /**
     * App maliciosa - otra app intenta interferir
     * Riesgo: spoofing de GPS, intercepción de SMS, MITM
     */
    MALICIOUS_APP,
    
    /**
     * Atacante de red - MITM, captive portal
     * Riesgo: intercepción de comunicaciones, phishing
     */
    NETWORK_ATTACKER,
    
    /**
     * Dispositivo comprometido - root, tampering
     * Riesgo: bypass de protecciones, acceso a datos encriptados
     */
    DEVICE_COMPROMISED,
    
    /**
     * Atacante físico - persona real persiguiendo
     * Riesgo: GPS spoofing cercano, intercepción física
     */
    PHYSICAL_ATTACKER
}

/**
 * Superficies de ataque del sistema
 */
enum class AttackSurface {
    /**
     * GPS - spoofing, jamming, signal manipulation
     * Defensas: LocationEngine, SpoofingDetector
     */
    GPS,
    
    /**
     * SMS - intercepción, fallo de entrega, spoofing
     * Defensas: AlertEngine, delivery confirmation
     */
    SMS,
    
    /**
     * Red - MITM, captive portal, DNS poisoning
     * Defensas: NetworkHardening, certificate pinning
     */
    NETWORK,
    
    /**
     * Proceso - kill, background restriction, doze mode
     * Defensas: WorkManager watchdog, anti-kill detection
     */
    PROCESS,
    
    /**
     * Storage - data exfiltration, tampering
     * Defensas: SQLCipher, IntegrityGuard
     */
    STORAGE
}

/**
 * Nivel de amenaza actual del sistema
 */
data class ThreatLevel(
    val actor: ThreatActor,
    val surfaces: Set<AttackSurface>,
    val confidence: Float // 0.0 - 1.0
) {
    /**
     * Verifica si el nivel es crítico
     */
    fun isCritical(): Boolean = confidence > 0.7f
    
    /**
     * Verifica si hay amenaza física
     */
    fun hasPhysicalThreat(): Boolean = actor == ThreatActor.PHYSICAL_ATTACKER
    
    /**
     * Verifica si el dispositivo está comprometido
     */
    fun isDeviceCompromised(): Boolean = actor == ThreatActor.DEVICE_COMPROMISED
}

/**
 * Matriz de defensa - qué módulo defiende qué superficie
 */
object DefenseMatrix {
    private val defenses = mapOf(
        AttackSurface.GPS to listOf("LocationEngine", "SpoofingDetector"),
        AttackSurface.SMS to listOf("AlertEngine", "DeliveryTracker"),
        AttackSurface.NETWORK to listOf("NetworkHardening", "CertificatePinner"),
        AttackSurface.PROCESS to listOf("WorkManagerWatchdog", "AntiKillDetector"),
        AttackSurface.STORAGE to listOf("SQLCipher", "IntegrityGuard")
    )
    
    /**
     * Obtiene los módulos que defienden una superficie
     */
    fun getDefenses(surface: AttackSurface): List<String> {
        return defenses[surface] ?: emptyList()
    }
    
    /**
     * Verifica si una superficie está defendida
     */
    fun isDefended(surface: AttackSurface): Boolean {
        return defenses.containsKey(surface)
    }
}

/**
 * Evaluador de amenazas en tiempo real
 */
class ThreatEvaluator {
    
    private var currentLevel = ThreatLevel(
        actor = ThreatActor.CASUAL_USER,
        surfaces = emptySet(),
        confidence = 0.0f
    )
    
    /**
     * Evalúa el nivel de amenaza actual
     */
    fun evaluate(
        isRooted: Boolean = false,
        isMockLocation: Boolean = false,
        isNetworkSecure: Boolean = true,
        isProcessAlive: Boolean = true
    ): ThreatLevel {
        val surfaces = mutableSetOf<AttackSurface>()
        var actor = ThreatActor.CASUAL_USER
        var confidence = 0.0f
        
        // Detectar dispositivo comprometido
        if (isRooted) {
            actor = ThreatActor.DEVICE_COMPROMISED
            surfaces.add(AttackSurface.STORAGE)
            surfaces.add(AttackSurface.PROCESS)
            confidence = 0.9f
        }
        
        // Detectar GPS spoofing
        if (isMockLocation) {
            if (actor == ThreatActor.CASUAL_USER) {
                actor = ThreatActor.MALICIOUS_APP
            }
            surfaces.add(AttackSurface.GPS)
            confidence = maxOf(confidence, 0.7f)
        }
        
        // Detectar red insegura
        if (!isNetworkSecure) {
            if (actor == ThreatActor.CASUAL_USER) {
                actor = ThreatActor.NETWORK_ATTACKER
            }
            surfaces.add(AttackSurface.NETWORK)
            confidence = maxOf(confidence, 0.5f)
        }
        
        // Detectar proceso muerto
        if (!isProcessAlive) {
            surfaces.add(AttackSurface.PROCESS)
            confidence = maxOf(confidence, 0.8f)
        }
        
        currentLevel = ThreatLevel(
            actor = actor,
            surfaces = surfaces,
            confidence = confidence
        )
        
        return currentLevel
    }
    
    /**
     * Obtiene el nivel actual
     */
    fun getCurrentLevel(): ThreatLevel = currentLevel
    
    /**
     * Verifica si el sistema está bajo ataque
     */
    fun isUnderAttack(): Boolean = currentLevel.confidence > 0.5f
}
