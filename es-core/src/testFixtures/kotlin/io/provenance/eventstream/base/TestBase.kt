package io.provenance.eventstream.test.base

import io.provenance.eventstream.test.utils.Defaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
open class TestBase {

    fun scopedTest(block: suspend TestCoroutineScope.() -> Unit) =
        dispatcherProvider.runBlockingTest(block)

    val decoderEngine = Defaults.decoderEngine()
    val templates = Defaults.templates

    val dispatcherProvider = TestDispatcherProvider()

    open fun setup() {
        Dispatchers.setMain(dispatcherProvider.dispatcher)
    }

    open fun tearDown() {
        Dispatchers.resetMain()
    }
}
