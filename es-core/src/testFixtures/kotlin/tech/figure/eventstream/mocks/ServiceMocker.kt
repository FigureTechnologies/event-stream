package tech.figure.eventstream.mocks

typealias Action = (Array<out Any?>) -> Any?

interface ServiceMock {
    suspend fun <T> respondWith(method: String, vararg args: Any?): T
}

class ServiceMocker private constructor(
    private val actions: Map<String, Action>,
) : ServiceMock {

    private val calls: MutableMap<String, Int> = mutableMapOf()

    data class Builder(
        private val buildActions: MutableMap<String, Action> = mutableMapOf(),
    ) {
        /**
         * Register an action to run for a given method with the arguments passed to the calling API method that is
         * being mocked
         */
        fun doFor(method: String, action: Action): Builder = apply {
            buildActions[method] = action
        }

        fun build(): ServiceMocker = ServiceMocker(buildActions)

        fun <T> build(clazz: Class<T>): T = clazz.getDeclaredConstructor(ServiceMock::class.java).newInstance(build())
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> respondWith(method: String, vararg args: Any?): T {
        val action = actions[method] ?: throw IllegalArgumentException("Bad method: $method")
        val result = action(args) as T
        calls.putIfAbsent(method, 0)
        calls.computeIfPresent(method) { _, v -> v + 1 }
        return result
    }
}
