package com.example.regresoacasa.domain.utils

import com.example.regresoacasa.domain.model.AppError
import kotlinx.coroutines.delay
import kotlin.math.pow

suspend fun <T> retryWithBackoff(
    times: Int = 3,
    initialDelay: Long = 1000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    var lastException: Exception? = null
    
    repeat(times) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            if (attempt < times - 1) {
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(10000)
            }
        }
    }
    
    throw lastException ?: Exception("Retry failed after $times attempts")
}

suspend fun <T> retryWithBackoffOnError(
    times: Int = 3,
    initialDelay: Long = 1000,
    factor: Double = 2.0,
    shouldRetry: (Exception) -> Boolean = { true },
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    var lastException: Exception? = null
    
    repeat(times) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            if (attempt < times - 1 && shouldRetry(e)) {
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(10000)
            } else {
                throw e
            }
        }
    }
    
    throw lastException ?: Exception("Retry failed after $times attempts")
}
