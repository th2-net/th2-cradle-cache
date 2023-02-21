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

package com.exactpro.th2.cradle.cache.entities.sse

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import java.io.Writer

class HttpWriter(private val writer: Writer, private val jacksonMapper: ObjectMapper) : StreamWriter {
    val logger = KotlinLogging.logger { }

    fun eventWrite(event: SseEvent) {
        val builder = StringBuilder()
        if (event.event != null) {
            builder.append("event: ").append(event.event).append("\n")
        }

        for (dataLine in event.data.lines()) {
            builder.append("data: ").append(dataLine).append("\n")
        }

        if (event.metadata != null) {
            builder.append("id: ").append(event.metadata).append("\n")
        }

        writer.write(builder.append("\n").toString())
        writer.flush()
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun closeWriter() {
        writer.close()
        logger.debug { "http sse writer has been closed" }
    }
}

interface StreamWriter {

}
