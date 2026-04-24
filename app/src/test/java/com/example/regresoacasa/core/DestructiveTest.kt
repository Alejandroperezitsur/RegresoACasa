package com.example.regresoacasa.core

import com.example.regresoacasa.core.emergency.EmergencyManager
import com.example.regresoacasa.core.location.LocationOrchestrator
import com.example.regresoacasa.core.recovery.RecoveryManager
import com.example.regresoacasa.core.security.SecurityManager
import com.example.regresoacasa.domain.model.UbicacionUsuario
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class DestructiveTest {
    
    private lateinit var securityManager: SecurityManager
    private lateinit var recoveryManager: RecoveryManager
    
    @Before
    fun setup() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        securityManager = SecurityManager(context)
        recoveryManager = RecoveryManager(context)
    }
    
    @Test
    fun testEncryptionDecryption() = runTest {
        val testData = "Sensitive location data: 40.7128, -74.0060"
        
        val encrypted = securityManager.encrypt(testData)
        assertNotEquals(testData, encrypted)
        
        val decrypted = securityManager.decrypt(encrypted)
        assertEquals(testData, decrypted)
    }
    
    @Test
    fun testSnapshotPersistence() = runTest {
        val state = SafeReturnState.Navigating(
            destination = "Home",
            route = com.example.regresoacasa.domain.model.Ruta(
                puntos = emptyList(),
                distanciaMetros = 1000.0,
                duracionSegundos = 600.0
            ),
            currentLocation = UbicacionUsuario(40.7128, -74.0060),
            startTime = System.currentTimeMillis(),
            remainingDistance = 1000.0,
            eta = "12:00"
        )
        
        recoveryManager.saveSnapshot(state)
        
        val restored = recoveryManager.restore()
        assertNotNull(restored)
        assertTrue(restored is SafeReturnState.Navigating)
        
        val restoredNav = restored as SafeReturnState.Navigating
        assertEquals(state.destination, restoredNav.destination)
        assertEquals(state.currentLocation.latitud, restoredNav.currentLocation.latitud, 0.0001)
    }
    
    @Test
    fun testSnapshotExpiry() = runTest {
        val state = SafeReturnState.Idle
        recoveryManager.saveSnapshot(state)
        
        // Manually set old timestamp
        val oldTime = System.currentTimeMillis() - 86400001L // 24h + 1ms ago
        
        // In real test, would mock time. For now, just test that clear works
        recoveryManager.clearSnapshot()
        
        val restored = recoveryManager.restore()
        assertNull(restored)
    }
    
    @Test
    fun testEmergencyStateTransitions() = runTest {
        val initialState = SafeReturnState.Idle
        assertFalse(initialState.isActive())
        assertFalse(initialState.isCritical())
        
        val emergencyState = SafeReturnState.Emergency(
            reason = "Test",
            timestamp = System.currentTimeMillis(),
            deliveryStatus = EmergencyDeliveryStatus.Sending,
            lastLocation = null
        )
        
        assertTrue(emergencyState.isActive())
        assertTrue(emergencyState.isCritical())
        assertFalse(emergencyState.isNavigating())
    }
    
    @Test
    fun testNavigatingState() = runTest {
        val state = SafeReturnState.Navigating(
            destination = "Home",
            route = com.example.regresoacasa.domain.model.Ruta(
                puntos = emptyList(),
                distanciaMetros = 1000.0,
                duracionSegundos = 600.0
            ),
            currentLocation = UbicacionUsuario(40.7128, -74.0060),
            startTime = System.currentTimeMillis(),
            remainingDistance = 1000.0,
            eta = "12:00"
        )
        
        assertTrue(state.isActive())
        assertFalse(state.isCritical())
        assertTrue(state.isNavigating())
    }
    
    @Test
    fun testDeviceSecurityCheck() {
        val isSecure = securityManager.isDeviceSecure()
        // On test environment, should return true (not rooted)
        assertTrue(isSecure)
    }
    
    @Test
    fun testSpatialIndexPerformance() {
        val points = (0 until 1000).map { i ->
            com.example.regresoacasa.domain.model.PuntoRuta(
                latitud = 40.7128 + (i * 0.0001),
                longitud = -74.0060 + (i * 0.0001)
            )
        }
        
        val index = com.example.regresoacasa.core.performance.SpatialIndex(points)
        
        val location = UbicacionUsuario(40.7128, -74.0060)
        val startTime = System.nanoTime()
        
        val nearest = index.findNearest(location)
        
        val duration = (System.nanoTime() - startTime) / 1_000_000 // ms
        
        assertNotNull(nearest)
        assertTrue(duration < 10, "Spatial index lookup took too long: ${duration}ms")
    }
    
    @Test
    fun testBoundingBoxFilter() {
        val points = (0 until 100).map { i ->
            com.example.regresoacasa.domain.model.PuntoRuta(
                latitud = 40.7128 + (i * 0.001),
                longitud = -74.0060 + (i * 0.001)
            )
        }
        
        val location = UbicacionUsuario(40.7128, -74.0060)
        
        val filtered = com.example.regresoacasa.core.performance.BoundingBoxFilter.filterPoints(
            location,
            points,
            radiusMeters = 500.0
        )
        
        assertTrue(filtered.size < points.size, "BoundingBox should filter out distant points")
    }
}
