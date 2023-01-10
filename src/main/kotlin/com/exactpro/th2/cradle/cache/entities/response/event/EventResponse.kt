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

package com.exactpro.th2.cradle.cache.entities.response.event

import com.exactpro.th2.cache.common.event.Event
import com.exactpro.th2.cache.common.toInstant
import java.time.Instant

data class EventResponse(
    val eventId: String,
    val book: String,
    val scope: String,
    val id: String,
    val batchId: String?,
    val isBatched: Boolean,
    val eventName: String,
    val eventType: String?,
    val startTimestamp: Instant,
    val endTimestamp: Instant?,
    val parentEventId: String?,
    val successful: Boolean,
    val attachedMessageIds: Set<String>?,
    val body: String?
) {
    constructor(event: Event) : this(
        event.eventId,
        event.book,
        event.scope,
        event.id,
        event.batchId,
        event.isBatched,
        event.eventName,
        event.eventType,
        toInstant(event.startTimestamp),
        event.endTimestamp?.let { toInstant(it) },
        event.parentEventId,
        event.successful,
        event.attachedMessageIds, event.body
    )
}
