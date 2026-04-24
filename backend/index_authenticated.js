const express = require('express');
const axios = require('axios');
const redis = require('redis');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');

const app = express();
app.use(express.json());

// Redis client
const redisClient = redis.createClient({
  host: process.env.REDIS_HOST || 'localhost',
  port: process.env.REDIS_PORT || 6379
});

redisClient.on('error', (err) => console.error('Redis error:', err));

// JWT Secret - MUST be set in environment
const JWT_SECRET = process.env.JWT_SECRET || 'CHANGE_ME_IN_PRODUCTION';
const ORS_API_KEY = process.env.ORS_API_KEY;

// Rate limiting per user (not IP)
const userRateLimits = new Map();
const MAX_REQUESTS_PER_HOUR = 50;

// CORS restricted to your app origin
app.use((req, res, next) => {
  const allowedOrigins = ['https://yourapp.com', 'capacitor://localhost'];
  const origin = req.headers.origin;
  
  if (allowedOrigins.includes(origin)) {
    res.header('Access-Control-Allow-Origin', origin);
  }
  
  res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  
  if (req.method === 'OPTIONS') {
    return res.sendStatus(200);
  }
  
  next();
});

// Middleware to verify JWT
function verifyJWT(req, res, next) {
  const token = req.headers.authorization?.split(' ')[1];
  
  if (!token) {
    return res.status(401).json({ error: 'No token provided' });
  }
  
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    req.userId = decoded.userId;
    next();
  } catch (err) {
    return res.status(401).json({ error: 'Invalid token' });
  }
}

// Rate limiting middleware (per user)
function rateLimit(req, res, next) {
  const userId = req.userId;
  const now = Date.now();
  const hour = 60 * 60 * 1000;
  
  if (!userRateLimits.has(userId)) {
    userRateLimits.set(userId, { count: 0, resetTime: now + hour });
  }
  
  const userLimit = userRateLimits.get(userId);
  
  if (now > userLimit.resetTime) {
    userLimit.count = 0;
    userLimit.resetTime = now + hour;
  }
  
  if (userLimit.count >= MAX_REQUESTS_PER_HOUR) {
    return res.status(429).json({ error: 'Rate limit exceeded' });
  }
  
  userLimit.count++;
  next();
}

// Generate JWT for user
app.post('/api/auth', async (req, res) => {
  const { deviceId } = req.body;
  
  if (!deviceId) {
    return res.status(400).json({ error: 'Device ID required' });
  }
  
  // Generate a user ID from device ID (in production, use proper user registration)
  const userId = crypto.createHash('sha256').update(deviceId).digest('hex');
  
  const token = jwt.sign(
    { userId, deviceId },
    JWT_SECRET,
    { expiresIn: '30d' }
  );
  
  res.json({ token, userId });
});

// Route calculation with authentication
app.post('/api/route', verifyJWT, rateLimit, async (req, res) => {
  const { start, end, profile } = req.body;
  
  // Validate input
  if (!start || !end || !Array.isArray(start) || !Array.isArray(end)) {
    return res.status(400).json({ error: 'Invalid coordinates' });
  }
  
  if (start.length !== 2 || end.length !== 2) {
    return res.status(400).json({ error: 'Coordinates must be [lon, lat]' });
  }
  
  const cacheKey = `route:${start.join(',')}:${end.join(',')}:${profile}`;
  
  try {
    // Check cache
    const cached = await redisClient.get(cacheKey);
    if (cached) {
      return res.json(JSON.parse(cached));
    }
    
    // Call ORS API
    const orsResponse = await axios({
      method: 'POST',
      url: `https://api.openrouteservice.org/v2/directions/${profile || 'foot-walking'}`,
      headers: {
        'Authorization': ORS_API_KEY,
        'Content-Type': 'application/json'
      },
      data: {
        coordinates: [start, end]
      },
      timeout: 10000
    });
    
    const routeData = orsResponse.data;
    
    // Cache for 24 hours
    await redisClient.setex(cacheKey, 86400, JSON.stringify(routeData));
    
    res.json(routeData);
    
  } catch (error) {
    if (error.response) {
      res.status(error.response.status).json({ error: error.response.data });
    } else {
      res.status(500).json({ error: 'Internal server error' });
    }
  }
});

// Emergency endpoint with authentication
app.post('/api/emergency', verifyJWT, async (req, res) => {
  const { emergencyId, message, location, timestamp } = req.body;
  
  // Validate payload
  if (!emergencyId || !message) {
    return res.status(400).json({ error: 'Missing required fields' });
  }
  
  // In production: send to your emergency notification system
  // For now, just acknowledge receipt
  
  console.log('Emergency received:', {
    emergencyId,
    userId: req.userId,
    location,
    timestamp
  });
  
  res.json({ received: true, timestamp: Date.now() });
});

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok' });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});
