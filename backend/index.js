/**
 * Backend Proxy para OpenRouteService
 * - Protege API Key
 * - Rate Limiting (50 req/hora por IP)
 * - Caché de rutas (24h TTL)
 * - Timeouts y Reintentos
 */

const redis = require('redis');
const axios = require('axios');

const client = redis.createClient({ 
    url: process.env.REDIS_URL || 'redis://localhost:6379' 
});

const ORS_API_KEY = process.env.ORS_API_KEY;
const ORS_URL = 'https://api.openrouteservice.org/v2/directions';

let redisConnected = false;

async function initRedis() {
    try {
        await client.connect();
        redisConnected = true;
        console.log('Redis conectado exitosamente');
    } catch (error) {
        console.error('Error conectando a Redis:', error.message);
        redisConnected = false;
    }
}

initRedis();

exports.handler = async (event) => {
    const headers = {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Content-Type, Authorization',
        'Access-Control-Allow-Methods': 'POST, OPTIONS',
        'Content-Type': 'application/json'
    };

    // Manejar preflight CORS
    if (event.requestContext?.http?.method === 'OPTIONS') {
        return { statusCode: 204, headers };
    }

    try {
        const body = typeof event.body === 'string' ? JSON.parse(event.body) : event.body;
        const { start, end, profile = 'driving-car' } = body;
        
        // Validar entrada
        if (!start || !end || !Array.isArray(start) || !Array.isArray(end) || start.length !== 2 || end.length !== 2) {
            return { 
                statusCode: 400, 
                headers, 
                body: JSON.stringify({ error: 'Coordenadas inválidas. Formato: { start: [lng, lat], end: [lng, lat] }' }) 
            };
        }

        const ip = event.requestContext?.http?.sourceIp || 'unknown';

        // RATE LIMITING por IP
        if (redisConnected) {
            const rateKey = `rate:${ip}`;
            const requests = await client.incr(rateKey);
            if (requests === 1) {
                await client.expire(rateKey, 3600); // 1 hora
            }
            if (requests > 50) {
                return { 
                    statusCode: 429, 
                    headers, 
                    body: JSON.stringify({ error: 'Demasiadas solicitudes. Límite: 50 por hora.' }) 
                };
            }
        }

        // CACHE KEY para rutas
        const cacheKey = `route:${profile}:${start.join(',')}:${end.join(',')}`;
        
        if (redisConnected) {
            const cachedData = await client.get(cacheKey);
            if (cachedData) {
                console.log(`Cache hit para ${cacheKey}`);
                return { 
                    statusCode: 200, 
                    headers, 
                    body: cachedData 
                };
            }
        }

        // REQUEST A ORS con timeout de 8s
        let orsResponse;
        try {
            orsResponse = await axios.post(
                `${ORS_URL}/${profile}`,
                { 
                    coordinates: [start, end],
                    format: 'json'
                },
                {
                    headers: { 
                        'Authorization': ORS_API_KEY, 
                        'Content-Type': 'application/json' 
                    },
                    timeout: 8000,
                    validateStatus: (status) => status < 500 // No lanzar error para 4xx
                }
            );
        } catch (error) {
            if (error.code === 'ECONNABORTED') {
                return { 
                    statusCode: 504, 
                    headers, 
                    body: JSON.stringify({ error: 'Timeout al calcular ruta. Intente nuevamente.' }) 
                };
            }
            throw error;
        }

        // Manejar errores de ORS
        if (orsResponse.status !== 200) {
            let status = orsResponse.status;
            let message = `Error del servicio de rutas: ${orsResponse.status}`;
            
            if (orsResponse.status === 429) {
                message = 'Límite de API excedido. Intente en unos minutos.';
            } else if (orsResponse.status >= 500) {
                status = 502;
                message = 'Servicio de rutas no disponible temporalmente.';
            } else if (orsResponse.status === 400) {
                message = 'Coordenadas inválidas o ruta no posible.';
            }

            return { 
                statusCode: status, 
                headers, 
                body: JSON.stringify({ error: message }) 
            };
        }

        const routeData = JSON.stringify(orsResponse.data);

        // CACHE para 24 horas
        if (redisConnected) {
            await client.setEx(cacheKey, 86400, routeData);
        }

        return { 
            statusCode: 200, 
            headers, 
            body: routeData 
        };

    } catch (error) {
        console.error('Error en proxy:', error.message);
        
        let status = 502;
        let message = 'Error interno del servidor';
        
        if (error.response?.status === 429) {
            status = 429;
            message = 'Límite de API excedido.';
        }

        return { 
            statusCode: status, 
            headers, 
            body: JSON.stringify({ error: message }) 
        };
    }
};
