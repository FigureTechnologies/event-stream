package tech.figure.eventstream.config

/**
 * An enumeration encoding various runtime environments.
 */
enum class Environment {
    Local {
        override fun isLocal(): Boolean = true
    },
    Development {
        override fun isDevelopment(): Boolean = true
    },
    Production {
        override fun isProduction() = true
    }, ;

    open fun isLocal(): Boolean = false
    open fun isDevelopment(): Boolean = false
    open fun isProduction(): Boolean = false
}
