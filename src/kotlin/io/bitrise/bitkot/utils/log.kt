package io.bitrise.bitkot.utils

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

val <T: Any> T.L: KLogger
    get() = KotlinLogging.logger(this.javaClass.name)

