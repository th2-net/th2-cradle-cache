/*
 * Copyright 2022 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.cradle.cache.http

import com.exactpro.th2.cradle.cache.entities.exceptions.DataNotFoundException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.nio.channels.ClosedChannelException

private val logger = KotlinLogging.logger {}
//private val logger = LoggerFactory.getLogger(javaClass)

private suspend fun sendErrorCode(call: ApplicationCall, cause: Throwable, code: HttpStatusCode) {
    withContext(NonCancellable) {
        call.respondText(getMessagesFromStackTrace(cause), ContentType.Text.Plain, code)
    }
}

fun StatusPagesConfig.setup() {
    exception<Throwable> { call, cause ->
        when (getCauseEscapeCoroutineException(cause) ?: cause) {
            is DataNotFoundException -> {
                sendErrorCode(call, cause, HttpStatusCode.NotFound)
                logger.error(cause) { "unable to handle request '${call.request.uri}' with parameters '${call.parameters}' - missing data" }
            }
            is ClosedChannelException -> {
                logger.info { "request '${call.request.uri}' with parameters '${call.parameters}' has been cancelled by a client" }
            }
            else -> {
                sendErrorCode(call, cause, HttpStatusCode.InternalServerError)
            }
        }
    }
}

fun getCauseEscapeCoroutineException(ex: Throwable): Throwable? {
    var rootCause: Throwable? = ex
    while (rootCause?.cause != null && rootCause is CancellationException) {
        rootCause = rootCause.cause
    }
    return rootCause
}

fun getMessagesFromStackTrace(ex: Throwable): String {
    val stringBuilder = StringBuilder()
    var rootCause: Throwable? = ex
    while (rootCause?.cause != null) {
        stringBuilder.append(rootCause.message ?: "", "\n")
        rootCause = rootCause.cause
    }
    stringBuilder.append(rootCause?.message ?: "")

    return String(stringBuilder)
}
