package com.example.regresoacasa.ui.state

enum class ErrorType {
    // Errores de red
    NO_INTERNET,
    TIMEOUT,
    
    // Errores de API
    API_ERROR,
    API_KEY_INVALID,
    SERVER_ERROR,
    RATE_LIMITED,
    
    // Errores de ubicación
    GPS_ERROR,
    GPS_LOST,
    LOCATION_PERMISSION_DENIED,
    
    // Errores de ruta
    NO_ROUTE,
    ROUTE_CALCULATION_FAILED,
    
    // Errores generales
    UNKNOWN,
    DATABASE_ERROR
}
