package io.bitrise.bitkot.utils

import java.util.logging.Logger

fun <T: Any> T.logger(): Logger {
    return Logger.getLogger(this.javaClass.name)
}