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

package com.exactpro.th2.cradle.cache.entities.response.messsage

import com.exactpro.th2.cache.common.message.BodyWrapper
import com.exactpro.th2.cache.common.message.Message
import com.exactpro.th2.cache.common.message.MessageMetadata
import com.exactpro.th2.cache.common.toInstant
import java.time.Instant

data class MessageResponse(
    val id: String,
    val timestamp: Instant,
    val sessionId: String,
    val attachedEventIds: Set<String>,
    val parsedMessageGroup: List<BodyWrapper>?,
    @Suppress("ArrayInDataClass")
    val rawMessageBody: ByteArray,
    val imageType: String?,
    val metadata: MessageMetadata
) {
    constructor(message: Message) : this(message.id, toInstant(message.timestamp), message.sessionId, message.attachedEventIds,
        message.parsedMessageGroup, message.rawMessageBody, message.imageType, message.metadata)
}
