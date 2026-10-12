package com.example.homeops.ui.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant

/**
 * Emits the current time now and then every [periodMillis]. D(t) changes continuously, so
 * screens combine their data with this to keep due-ness fresh while open.
 * `flow { }` is a cold stream: it only runs while someone collects it.
 */
fun Clock.ticker(periodMillis: Long = 60_000): Flow<Instant> =
    flow {
        while (true) {
            emit(instant())
            delay(periodMillis)
        }
    }
