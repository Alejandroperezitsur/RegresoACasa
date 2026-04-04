# Configuración API Key - Regreso a Casa

## ⚠️ IMPORTANTE: Configurar OpenRouteService API Key

### Paso 1: Obtener API Key gratuita
1. Ve a https://openrouteservice.org/dev/#/signup
2. Crea una cuenta gratuita
3. Genera una API Key en el panel de desarrollador

### Paso 2: Configurar en el proyecto
Crea o edita el archivo `local.properties` en la raíz del proyecto:

```properties
ORS_API_KEY=tu_api_key_aqui
```

Ejemplo:
```properties
ORS_API_KEY=5b3ce3597851110001cf6248xxxxxxxxxxxx
```

### Paso 3: Sincronizar proyecto
En Android Studio:
1. File → Sync Project with Gradle Files
2. Rebuild Project

### Paso 4: Verificar
La API key se compila automáticamente en `BuildConfig.ORS_API_KEY`

## 🔒 Notas de seguridad
- NUNCA subas `local.properties` a git (ya está en .gitignore)
- La API key en BuildConfig es segura para uso en la app
- Para producción, considera usar un backend proxy para ocultar la key

## 🧪 Testing sin API Key
Si no tienes API key aún:
1. El geocoding funciona con Nominatim (sin API key)
2. Las rutas requieren ORS API key
3. Puedes probar la búsqueda y guardado de favoritos sin la key
