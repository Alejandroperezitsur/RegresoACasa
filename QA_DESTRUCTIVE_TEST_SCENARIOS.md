# QA Destructive Test Scenarios - Regreso Seguro

## Overview
These test scenarios are designed to validate the safety system under extreme conditions. Each scenario tests specific reliability features.

---

## Scenario 1: Extended Stop Detection
**Objective**: Validate alert triggering when user stops for extended period

### Test Steps
1. Start a trip with destination
2. Walk for 2 minutes
3. Stop completely for 5 minutes
4. Verify alert is triggered
5. Verify SMS is sent to contacts
6. Verify SMS delivery confirmation

### Expected Results
- Alert triggered after adaptive threshold (walking: ~2-5 min)
- SMS sent with location and stop duration
- Delivery confirmation received
- Database records alert status

### Validation Logs
```
[INFO] Trip started
[INFO] GPS updates: 120
[INFO] Stop detected at timestamp X
[WARNING] Stop duration exceeded threshold
[ALERT] Alert dispatched to contacts
[INFO] SMS SENT confirmation received
[INFO] SMS DELIVERED confirmation received
```

---

## Scenario 2: Route Deviation
**Objective**: Validate adaptive deviation detection based on speed

### Test Steps
1. Start trip in driving mode
2. Follow route for 1 km
3. Deviate 150m from expected route
4. Verify no alert (within threshold)
5. Deviate 250m from expected route
6. Verify alert triggered

### Expected Results
- No alert for 150m deviation (driving threshold ~200m)
- Alert triggered for 250m deviation
- SMS includes deviation distance and current location

### Validation Logs
```
[INFO] Trip started (DRIVING mode)
[INFO] Deviation: 150m - within threshold
[WARNING] Deviation: 250m - exceeded threshold
[ALERT] Deviation alert sent
```

---

## Scenario 3: Data Loss Simulation
**Objective**: Validate offline fallback and event caching

### Test Steps
1. Start trip with live tracking
2. Disable mobile data and WiFi
3. Continue walking for 5 minutes
4. Re-enable data
5. Verify offline summary sent to contacts

### Expected Results
- Events cached during offline period
- Offline summary SMS sent upon reconnection
- Summary includes all cached events with timestamps
- Live tracking resumes normally

### Validation Logs
```
[INFO] Live tracking started
[WARNING] Network connection lost
[INFO] Caching offline events
[INFO] Event cached: LOCATION_UPDATE
[INFO] Event cached: STOP_DETECTED
[INFO] Network connection restored
[INFO] Sending offline summary
[INFO] Offline summary sent to 2 contacts
```

---

## Scenario 4: Screen Lock & Background
**Objective**: Validate service continues when screen is locked

### Test Steps
1. Start trip
2. Lock screen
3. Keep phone in pocket for 10 minutes
4. Unlock and check app
5. Verify monitoring continued

### Expected Results
- Service remained active in background
- GPS updates continued
- Watchdog timestamps updated
- No alerts triggered falsely

### Validation Logs
```
[INFO] Service in foreground
[INFO] Screen locked - service continuing
[INFO] GPS update: timestamp X
[INFO] Watchdog cycle: timestamp Y
[INFO] Screen unlocked - service active
```

---

## Scenario 5: Force App Kill
**Objective**: Validate service restart and state recovery

### Test Steps
1. Start trip with monitoring
2. Force kill app from recent apps
3. Wait 30 seconds
4. Open app
5. Verify service restarted
6. Verify state recovered from DB

### Expected Results
- Service restarts automatically (START_STICKY)
- Pending alerts retried
- State rehydrated from database
- Monitoring resumes without user intervention

### Validation Logs
```
[INFO] Service active
[WARNING] App killed by system/user
[INFO] Service restart triggered
[INFO] Rehydrating state from DB
[INFO] Found 1 pending alert - retrying
[INFO] State recovered successfully
[INFO] Monitoring resumed
```

---

## Scenario 6: Battery Optimization Mode
**Objective**: Validate behavior under extreme battery saving

### Test Steps
1. Enable battery optimization for app
2. Start trip
3. Verify warning shown
4. Request battery optimization exclusion
5. Verify service continues normally

### Expected Results
- Warning displayed when optimization is active
- User prompted to exclude from optimization
- After exclusion, service runs normally
- No false alerts due to throttling

### Validation Logs
```
[WARNING] Battery optimization active
[INFO] Showing battery optimization warning
[INFO] User requested exclusion
[INFO] Battery optimization excluded
[INFO] Service running without restrictions
```

---

## Scenario 7: SMS Delivery Failure
**Objective**: Validate retry logic and fallback

### Test Steps
1. Put phone in airplane mode
2. Trigger alert
3. Verify SMS fails
4. Verify retry attempted (up to 3 times)
5. Disable airplane mode
6. Verify SMS sent successfully

### Expected Results
- Initial SMS send fails
- Retry logic triggers (3 attempts with 5s delay)
- After connection restored, SMS succeeds
- Database updated with final status

### Validation Logs
```
[ALERT] Attempting to send SMS
[ERROR] SMS send failed (RESULT_ERROR_NO_SERVICE)
[INFO] Retry 1/3 in 5s
[ERROR] SMS send failed (RESULT_ERROR_NO_SERVICE)
[INFO] Retry 2/3 in 5s
[ERROR] SMS send failed (RESULT_ERROR_NO_SERVICE)
[INFO] Retry 3/3 in 5s
[ERROR] SMS send failed (RESULT_ERROR_NO_SERVICE)
[INFO] All retries exhausted - marked as FAILED
[INFO] Connection restored
[INFO] Retrying pending alerts
[INFO] SMS sent successfully
[INFO] SMS delivered successfully
```

---

## Scenario 8: GPS Signal Loss
**Objective**: Validate behavior during GPS unavailability

### Test Steps
1. Start trip indoors (no GPS)
2. Move outdoors
3. Verify GPS acquisition
4. Return indoors
5. Verify signal loss handling

### Expected Results
- Signal loss detected and logged
- Risk score increases due to signal factor
- Alert sent if signal loss > 5 minutes
- Normal operation resumes when signal returns

### Validation Logs
```
[WARNING] GPS signal lost
[INFO] Last known location cached
[INFO] Signal loss duration: 30s
[WARNING] Signal loss duration: 2m
[ALERT] Signal loss alert sent (>5m)
[INFO] GPS signal restored
[INFO] Normal operation resumed
```

---

## Scenario 9: Low Battery Scenario
**Objective**: Validate low battery alerts and behavior

### Test Steps
1. Start trip with 100% battery
2. Drain battery to 20%
3. Verify low battery alert sent
4. Continue to 10%
5. Verify critical battery alert sent
6. Verify app continues functioning

### Expected Results
- Warning alert at 20%
- Critical alert at 10%
- App continues monitoring
- SMS includes battery level
- No service shutdown

### Validation Logs
```
[INFO] Battery: 50%
[WARNING] Battery: 20% - low battery alert sent
[ALERT] Battery: 10% - critical battery alert sent
[INFO] Service continuing despite low battery
```

---

## Scenario 10: Suspicious Silence Mode
**Objective**: Validate detection of non-responsive user

### Test Steps
1. Start trip
2. Trigger multiple alerts
3. Do not respond to any alerts
4. Continue moving irregularly
5. Verify risk escalation
6. Verify critical alert sent

### Expected Results
- Silence detector monitoring active
- Risk score increases with each unresponded alert
- Irregular movement detected
- Critical alert triggered when risk > 0.8
- SMS includes escalation reason

### Validation Logs
```
[INFO] Silence monitoring started
[ALERT] Alert 1 sent
[INFO] No user response recorded
[ALERT] Alert 2 sent
[WARNING] User unresponsive (ratio: 0.0)
[INFO] Irregular movement detected
[WARNING] Risk level: 0.6 (SUSPICIOUS)
[ALERT] Alert 3 sent
[ERROR] No user response
[CRITICAL] Risk level: 0.85 (CRITICAL)
[ALERT] Critical escalation alert sent
```

---

## Scenario 11: Watchdog Critical Detection
**Objective**: Validate watchdog detects app/system death

### Test Steps
1. Start trip with monitoring
2. Simulate system freeze (stop GPS updates)
3. Wait 2 minutes
4. Verify watchdog detects critical state
5. Verify automatic alert triggered
6. Verify service restart attempted

### Expected Results
- Watchdog detects no GPS updates for >2 min
- Critical state triggered
- Automatic alert sent to contacts
- Service restart attempted
- Logs show detection and recovery

### Validation Logs
```
[INFO] Watchdog monitoring active
[INFO] GPS update: timestamp X
[WARNING] No GPS update for 60s
[ERROR] No GPS update for 120s - CRITICAL
[CRITICAL] Watchdog critical alert triggered
[INFO] Service restart requested
[INFO] Attempting to recover state
```

---

## Scenario 12: Multiple Contact Alert
**Objective**: Validate alert delivery to multiple contacts

### Test Steps
1. Configure 3 emergency contacts
2. Trigger critical alert
3. Verify SMS sent to all contacts
4. Verify delivery confirmation for each
5. Simulate failure for one contact
6. Verify fallback to next contact

### Expected Results
- SMS sent to all 3 contacts
- Delivery confirmation received for 2
- 1 contact fails - marked as UNCERTAIN
- No fallback (alert already sent to others)
- Database records status for each contact

### Validation Logs
```
[ALERT] Sending alert to 3 contacts
[INFO] SMS sent to Contact 1
[INFO] SMS sent to Contact 2
[INFO] SMS sent to Contact 3
[INFO] Delivery confirmed: Contact 1
[INFO] Delivery confirmed: Contact 2
[WARNING] Delivery timeout: Contact 3 (marked UNCERTAIN)
[INFO] Alert delivery complete: 2/3 confirmed
```

---

## Validation Checklist

For each scenario, verify:

- [ ] App does not crash
- [ ] Service remains active or restarts
- [ ] SMS sent with correct format
- [ ] Delivery confirmation received
- [ ] Database records updated
- [ ] Logs show expected sequence
- [ ] UI reflects correct state
- [ ] Haptic feedback appropriate
- [ ] No false positives
- [ ] Recovery after failure

---

## Performance Metrics

Track these metrics during testing:

- **SMS Delivery Time**: < 30s for 90% of alerts
- **Service Restart Time**: < 10s after kill
- **GPS Update Frequency**: Every 3-5s
- **Battery Impact**: < 5% per hour
- **Memory Usage**: < 100MB
- **CPU Usage**: < 10% average

---

## Known Limitations

1. SMS delivery depends on carrier network
2. GPS accuracy varies by location
3. Battery optimization may vary by OEM
4. Doze mode behavior varies by Android version
5. Some devices may have aggressive task killers

---

## Test Environment

- **Device**: Various Android devices (API 24-35)
- **Network**: 4G/5G, WiFi, and no signal scenarios
- **Battery**: 100% to 5% range
- **Location**: Urban, suburban, indoor, outdoor
- **Time of Day**: Day and night testing
