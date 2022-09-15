package tech.figure.eventstream.test.base

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import tech.figure.eventstream.base.TestDispatcherProvider
import tech.figure.eventstream.utils.Defaults

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
