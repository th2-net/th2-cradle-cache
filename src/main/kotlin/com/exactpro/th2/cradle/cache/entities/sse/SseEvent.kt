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

import com.exactpro.th2.cradle.cache.asStringSuspend
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.util.*

enum class EventType {
    MESSAGE, EVENT, CLOSE, ERROR, KEEP_ALIVE, MESSAGE_IDS, PIPELINE_STATUS;

    override fun toString(): String {
        return super.toString().toLowerCase()
    }
}

data class ExceptionInfo(val exceptionName: String, val exceptionCause: String)

/**
 * The data class representing a SSE Event that will be sent to the client.
 */

data class SseEvent(val data: String = "empty data", val event: EventType? = null, val metadata: String? = null) {
    companion object {

        @InternalAPI
        suspend fun build(jacksonMapper: ObjectMapper, e: Exception): SseEvent {
            return SseEvent(
                jacksonMapper.asStringSuspend(ExceptionInfo(e.javaClass.name, e.rootCause?.message ?: e.toString())),
                event = EventType.ERROR
            )
        }
    }
}
