package io.provenance.eventstream.utils

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * TODO: Remove
 *
 * HACK! HACK! HACK!
 *
 * This interface is needed to be able to inject delay() functionality into a class without directly using Kotlin's
 * coroutine context sleep-specific delay() function.
 *
 * The issue arises when using delay() with `runBlocking` during tests. Using `runBlocking`, even when using test
 * dispatchers, will cause the test to hang.
 *
 * To get around this, Kotlin's authors introduced the `kotlinx-coroutines-test` package, which provides a blocking
 * runner `runBlockingTest`, which implements its own no-op `delay()`. The problem is that `runBlockingTest` is not
 * well behaved and is broken in a lot of use cases, especially when using it with 3rd party libraries built on top of
 * `CompleteableFuture` and code adapted to work with Kotlin's coroutines via the `kotlinx-coroutines-jdk8` and
 * `kotlinx-coroutines-reactive` compatibility packages. Using `runBlockingTest` with these packages will result
 * in an error: "This job has not completed yet"
 *
 * There is hope however! It looks like a fix is being worked on as we speak (10/13/2021)
 * https://github.com/Kotlin/kotlinx.coroutines/pull/2978
 *
 * @see https://github.com/Kotlin/kotlinx.coroutines/issues/1204
 */
@OptIn(ExperimentalTime::class)
interface DelayShim {
    suspend fun doDelay(duration: Duration) {
        delay(duration)
    }

    suspend fun doDelay(duration: Long) {
        doDelay(Duration.milliseconds(duration))
    }
}