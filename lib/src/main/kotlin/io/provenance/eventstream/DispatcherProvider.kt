package io.provenance.eventstream

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * An interface that defines a way of programmatically injecting coroutine dispatchers into code.
 *
 * The idea of using of injectable dispatchers was taken from
 * https://craigrussell.io/2019/11/unit-testing-coroutine-suspend-functions-using-testcoroutinedispatcher/
 *
 * This approach is needed to override the main, IO, default, and unconfined dispatchers that coroutines run on
 * during testing so [kotlinx.coroutines.test.runBlockingTest] can be used (which skips delay(), advances time, etc.).
 *
 * If not, tests will fail with:
 *
 * ```
 * java.lang.IllegalStateException: This job has not completed yet
 * ```
 */
interface DispatcherProvider {
    fun main(): CoroutineDispatcher = Dispatchers.Main
    fun default(): CoroutineDispatcher = Dispatchers.Default
    fun io(): CoroutineDispatcher = Dispatchers.IO
    fun unconfined(): CoroutineDispatcher = Dispatchers.Unconfined
}

class DefaultDispatcherProvider : DispatcherProvider