package io.provenance.eventstream.config

/**
 * An enumeration encoding various runtime environments.
 */
enum class Environment {
    local {
        override fun isLocal(): Boolean = true
    },
    development {
        override fun isDevelopment(): Boolean = true
    },
    production {
        override fun isProduction() = true
    };

    open fun isLocal(): Boolean = false
    open fun isDevelopment(): Boolean = false
    open fun isProduction(): Boolean = false
}
