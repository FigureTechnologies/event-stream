package io.provenance.eventstream.test.base

import io.provenance.eventstream.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest

@ExperimentalCoroutinesApi
class TestDispatcherProvider : DispatcherProvider {

    @OptIn(ExperimentalCoroutinesApi::class)
    val dispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()

    override fun main(): CoroutineDispatcher = dispatcher
    override fun default(): CoroutineDispatcher = dispatcher
    override fun io(): CoroutineDispatcher = dispatcher
    override fun unconfined(): CoroutineDispatcher = dispatcher

    /**
     * Since all the dispatchers in provider share the same [TestCoroutineDispatcher], this is a shortcut to calling
     * the test dispatcher's [runBlockingTest] method
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun runBlockingTest(block: suspend TestCoroutineScope.() -> Unit): Unit {
        val testDispatcher = this.main() as TestCoroutineDispatcher
        return testDispatcher.runBlockingTest(block)
    }
}