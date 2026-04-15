package com.example.regresoacasa.domain.utils

object InstructionTranslator {
    
    private val translations = mapOf(
        // Direcciones básicas
        "Continue" to "Continúa",
        "Turn" to "Gira",
        "Head" to "Dirígete",
        "Take" to "Toma",
        "Enter" to "Entra",
        "Exit" to "Sal",
        "Leave" to "Abandona",
        "Keep" to "Mantente",
        "Stay" to "Permanece",
        "Follow" to "Sigue",
        "Merge" to "Incorpórate",
        "Fork" to "Bifurcación",
        "Roundabout" to "Rotonda",
        "U-turn" to "Media vuelta",
        "Arrive" to "Llegarás",
        "Destination" to "Destino",
        "Way" to "Camino",
        "Road" to "Carretera",
        "Street" to "Calle",
        "Avenue" to "Avenida",
        "Boulevard" to "Bulevar",
        "Highway" to "Autopista",
        "Motorway" to "Autopista",
        "Bridge" to "Puente",
        "Tunnel" to "Túnel",
        "Path" to "Sendero",
        "Trail" to "Camino",
        
        // Modificadores de dirección
        "left" to "a la izquierda",
        "right" to "a la derecha",
        "slightly" to "ligeramente",
        "sharp" to "pronunciadamente",
        "straight" to "recto",
        "ahead" to "adelante",
        "forward" to "hacia adelante",
        "backward" to "hacia atrás",
        "back" to "atrás",
        "onto" to "hacia",
        "toward" to "hacia",
        "towards" to "hacia",
        "into" to "hacia",
        "on" to "por",
        "along" to "a lo largo de",
        "past" to "pasando",
        "through" to "a través de",
        "over" to "sobre",
        "under" to "debajo de",
        "at" to "en",
        "before" to "antes de",
        "after" to "después de",
        "until" to "hasta",
        "and" to "y",
        "the" to "la",
        "your" to "tu",
        
        // Frases comunes
        "onto the" to "hacia la",
        "onto your" to "hacia tu",
        "at the" to "en la",
        "at your" to "en tu",
        "into the" to "hacia la",
        "into your" to "hacia tu",
        "on the" to "por la",
        "on your" to "por tu",
        
        // Puntos cardinales
        "north" to "norte",
        "south" to "sur",
        "east" to "este",
        "west" to "oeste",
        "northeast" to "noreste",
        "northwest" to "noroeste",
        "southeast" to "sureste",
        "southwest" to "suroeste",
        
        // Números ordinales para rotondas
        "1st" to "1ª",
        "2nd" to "2ª",
        "3rd" to "3ª",
        "4th" to "4ª",
        "5th" to "5ª",
        "6th" to "6ª",
        "7th" to "7ª",
        "8th" to "8ª",
        "9th" to "9ª",
        "first" to "primera",
        "second" to "segunda",
        "third" to "tercera",
        "fourth" to "cuarta",
        "fifth" to "quinta",
        "sixth" to "sexta",
        "seventh" to "séptima",
        "eighth" to "octava",
        "ninth" to "novena",
        "exit" to "salida",
        
        // Maniobras específicas
        "Enter the roundabout" to "Entra en la rotonda",
        "Exit the roundabout" to "Sal de la rotonda",
        "At the roundabout" to "En la rotonda",
        "Make a U-turn" to "Da media vuelta",
        "You have arrived" to "Has llegado a tu destino",
        "You have reached" to "Has llegado a",
        "Your destination" to "tu destino",
        "is on the left" to "está a la izquierda",
        "is on the right" to "está a la derecha",
        "is ahead" to "está adelante",
        "Keep right" to "Mantente a la derecha",
        "Keep left" to "Mantente a la izquierda",
        "Merge right" to "Incorpórate por la derecha",
        "Merge left" to "Incorpórate por la izquierda",
        "Take the exit" to "Toma la salida",
    )
    
    fun translate(instruction: String): String {
        if (instruction.isBlank()) return ""
        
        var translated = instruction
        
        // Ordenar por longitud descendente para evitar reemplazos parciales
        val sortedTranslations = translations.entries.sortedByDescending { it.key.length }
        
        for ((english, spanish) in sortedTranslations) {
            // Reemplazar preservando mayúsculas/minúsculas donde aplique
            translated = translated.replace(english, spanish, ignoreCase = true)
        }
        
        // Capitalizar primera letra
        return translated.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    
    fun translateWithDistance(instruction: String, distance: Double): String {
        val translated = translate(instruction)
        val distanceText = formatDistance(distance)
        return "$translated ($distanceText)"
    }
    
    private fun formatDistance(distance: Double): String {
        return when {
            distance < 1000 -> "${distance.toInt()} m"
            else -> "${(distance / 1000).toInt()} km"
        }
    }
}
