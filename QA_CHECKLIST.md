# QA Checklist - Navegación en Tiempo Real

## ✅ Features Implementados

### 1. Tracking en Tiempo Real
- [x] LocationCallback con FusedLocationProviderClient
- [x] Frecuencia: 3 segundos (configurable 2-5s)
- [x] PRIORITY_HIGH_ACCURACY
- [x] Flujo de corutinas (callbackFlow)

### 2. Mapa Dinámico
- [x] Seguimiento automático del usuario
- [x] Auto-centrado del mapa
- [x] Botón "Recentrar" cuando se desactiva follow
- [x] Manejo incremental de overlays (sin clear)

### 3. Re-Routing Automático
- [x] Detección de desviación (>50m)
- [x] Recálculo automático (>100m)
- [x] Debounce de 10s entre recálculos
- [x] Mutex para evitar requests simultáneos

### 4. Distancia/Tiempo Real
- [x] Cálculo continuo de distancia restante
- [x] Actualización de tiempo estimado
- [x] Fórmula Haversine para cálculos precisos

### 5. Control de Requests
- [x] Cancelación de jobs previos
- [x] Mutex en cálculo de rutas
- [x] Job cancellable para location updates

### 6. Manejo de Errores
- [x] UiState sealed class
- [x] NavigationUiState sealed class
- [x] ErrorType enum (NO_INTERNET, GPS_DISABLED, API_ERROR, TIMEOUT)

## 🧪 Escenarios de QA

### Escenario 1: Caminar 5 minutos con GPS
**Pasos:**
1. Iniciar navegación a casa
2. Caminar 5 minutos
3. Verificar:
   - [ ] Mapa sigue al usuario
   - [ ] Distancia restante disminuye
   - [ ] No hay parpadeos en el mapa
   - [ ] Sin crashes

### Escenario 2: Apagar Internet
**Pasos:**
1. Iniciar navegación
2. Activar modo avión
3. Verificar:
   - [ ] Mensaje "Sin conexión" aparece
   - [ ] App no crashea
   - [ ] Al reactivar internet, recalcula automáticamente

### Escenario 3: Cambiar Ubicación Manualmente (Mock)
**Pasos:**
1. Usar app con ubicación simulada
2. "Teletransportarse" 200m lejos de ruta
3. Verificar:
   - [ ] Detecta desviación (>50m)
   - [ ] Muestra alerta "Desviado de la ruta"
   - [ ] Recalcula ruta automáticamente

### Escenario 4: Rotar Pantalla
**Pasos:**
1. Iniciar navegación
2. Rotar pantalla 3 veces
3. Verificar:
   - [ ] No pierde estado de navegación
   - [ ] Sigue mostrando ruta
   - [ ] Tracking continúa

### Escenario 5: Spam de Botón "Ir a Casa"
**Pasos:**
1. Presionar "Ir a Casa" 10 veces rápido
2. Verificar:
   - [ ] Solo una ruta calculada
   - [ ] No hay requests duplicados
   - [ ] Mutex funciona correctamente

### Escenario 6: GPS Lento/Impreciso
**Pasos:**
1. Simular GPS con precisión baja (100m)
2. Verificar:
   - [ ] App sigue funcionando
   - [ ] No muestra desviación falsa
   - [ ] Maneja ubicaciones imprecisas

### Escenario 7: Background/Foreground
**Pasos:**
1. Iniciar navegación
2. Minimizar app (home)
3. Volver a app después de 30s
4. Verificar:
   - [ ] Tracking se reanuda
   - [ ] Estado preservado
   - [ ] No hay leaks de memoria

## 📊 Métricas de Performance

| Métrica | Objetivo | Estado |
|---------|----------|--------|
| Tiempo de recálculo | < 3 segundos | ⏳ |
| Parpadeo del mapa | 0 | ⏳ |
| Requests duplicados | 0 | ⏳ |
| Crashes | 0 | ⏳ |
| Memoria estable | Sin leaks | ⏳ |

## 🔧 Comandos para Testing

```bash
# Simular ubicación (requiere developer options)
adb shell appops set com.example.regresoacasa android:mock_location allow

# Ver logs de navegación
adb logcat -s "NavigationViewModel:D" "LocationTrackingService:D"

# Monitorear requests de red
adb logcat -s "OkHttp:D"
```

## 📝 Notas de Implementación

### Cambios Clave Realizados:
1. **LocationTrackingService**: Nuevo servicio con callbackFlow
2. **NavigationViewModel**: ViewModel dedicado a navegación
3. **NavigationState**: Estado completo de navegación
4. **UiState sealed classes**: Manejo robusto de estados
5. **Re-routing**: Detección automática + recálculo
6. **MapaView**: Follow user + recentrar

### Archivos Modificados/Creados:
- ✅ `LocationTrackingService.kt` (nuevo)
- ✅ `NavigationViewModel.kt` (nuevo)
- ✅ `NavigationState.kt` (nuevo)
- ✅ `UiState.kt` (nuevo)
- ✅ `NavigationScreen.kt` (actualizado)
- ✅ `MapaView.kt` (actualizado)
- ✅ `MainActivity.kt` (actualizado)
- ✅ `AppModule.kt` (actualizado)

## 🚀 Próximos Pasos

1. Ejecutar checklist de QA
2. Probar en dispositivo real
3. Optimizar si es necesario
4. Documentar métricas reales
