package com.example.regresoacasa.domain.utils

/**
 * Simplificador de instrucciones de navegación para mostrar texto claro y conciso
 */
object InstructionSimplifier {
    
    private val REPLACEMENTS = mapOf(
        "Turn right" to "Gira a la derecha",
        "Turn left" to "Gira a la izquierda",
        "Continue straight" to "Continúa recto",
        "Head north" to "Dirígete al norte",
        "Head south" to "Dirígete al sur",
        "Head east" to "Dirígete al este",
        "Head west" to "Dirígete al oeste",
        "Make a U-turn" to "Da media vuelta",
        "Keep right" to "Mantente a la derecha",
        "Keep left" to "Mantente a la izquierda",
        "Merge" to "Incorpórate",
        "Take the exit" to "Toma la salida",
        "Enter the roundabout" to "Entra a la rotonda",
        "Exit the roundabout" to "Sal de la rotonda",
        "At the roundabout" to "En la rotonda",
        "Arrive at" to "Has llegado a",
        "You have arrived" to "Has llegado",
        "Destination reached" to "Destino alcanzado",
        "toward" to "hacia",
        "towards" to "hacia",
        "onto" to "por",
        "on" to "en",
        "via" to "vía"
    )
    
    /**
     * Simplifica una instrucción de navegación
     * @param instruction Texto original de la instrucción
     * @param streetName Nombre de la calle (opcional)
     * @return Texto simplificado en español
     */
    fun simplify(instruction: String, streetName: String = ""): String {
        var result = instruction
        
        // Aplicar reemplazos en orden específico
        REPLACEMENTS.forEach { (key, value) ->
            result = result.replace(key, value, ignoreCase = true)
        }
        
        // Limpiar espacios extra
        result = result.trim().replace(Regex("\\s+"), " ")
        
        // Agregar nombre de calle si existe y no está ya en la instrucción
        if (streetName.isNotBlank() && !result.contains(streetName, ignoreCase = true)) {
            // Solo agregar calle si es relevante (no números de autopista genéricos)
            if (shouldIncludeStreet(streetName)) {
                result = "$result en $streetName"
            }
        }
        
        // Truncar si es muy largo (máximo 50 caracteres)
        return if (result.length > 50) {
            result.take(47) + "..."
        } else {
            result
        }
    }
    
    /**
     * Determina si el nombre de calle debe incluirse en la instrucción
     */
    private fun shouldIncludeStreet(streetName: String): Boolean {
        // Excluir calles genéricas o sin nombre
        val excludedPatterns = listOf(
            "^\\d+$",                    // Solo números
            "^unnamed$",                 // Sin nombre
            "^link$",                    // Links de autopista
            "^way$",                     // Vías genéricas
            "^track$",                  // Caminos
            "^path$"                    // Senderos
        )
        
        return excludedPatterns.none { pattern ->
            streetName.matches(Regex(pattern, RegexOption.IGNORE_CASE))
        }
    }
    
    /**
     * Obtiene un texto corto para la instrucción (máximo 30 caracteres)
     */
    fun getShortText(instruction: String, streetName: String = ""): String {
        val simplified = simplify(instruction, streetName)
        return if (simplified.length > 30) {
            simplified.take(27) + "..."
        } else {
            simplified
        }
    }
    
    /**
     * Obtiene texto de anticipación (para mostrar 100m antes)
     */
    fun getApproachingText(maneuver: String): String {
        return when {
            maneuver.contains("derecha", ignoreCase = true) -> "Giro a la derecha próximo"
            maneuver.contains("izquierda", ignoreCase = true) -> "Giro a la izquierda próximo"
            maneuver.contains("rotonda", ignoreCase = true) -> "Rotonda próxima"
            maneuver.contains("salida", ignoreCase = true) -> "Salida próxima"
            else -> "Maniobra próxima"
        }
    }
}
