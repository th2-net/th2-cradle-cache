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

import com.exactpro.th2.cradle.cache.Context
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import mu.KotlinLogging

class HttpServer(private val appCtx: Context) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val cfg = appCtx.configuration

    fun run() {
        embeddedServer(
            Netty,
            port = cfg.port,
            configure = { responseWriteTimeoutSeconds = -1 }) {

            install(Compression)
            install(Timeouts) { requestTimeout = appCtx.responseTimeout }
            install(StatusPages) { setup() }
            install(ContentNegotiation) {
                val mapper = appCtx.jacksonMapper
                val converter = JacksonConverter(mapper)
                register(ContentType.Application.Json, converter)
            }

            configureRouting(appCtx.arango, appCtx)
        }.start(wait = false)

        logger.info { "serving on: http://${cfg.hostname}:${cfg.port}" }
    }
}
