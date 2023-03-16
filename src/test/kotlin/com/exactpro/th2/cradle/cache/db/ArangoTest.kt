/*
 * Copyright 2023 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.cradle.cache.db

import com.exactpro.th2.cache.common.event.Event
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ArangoTest {
    private val arangoMock = mockk<com.exactpro.th2.cache.common.Arango> (relaxed = true)
    private val arango = Arango(arangoMock)

    @Test
    fun `check 'message'{id} works properly`() {
        val expected = """FOR message IN parsed_messages
            |FILTER message._key == "abc-def"
            |LIMIT 1
            |RETURN message""".trimMargin()

        arango.getMessage("abc-def", true)

        val query = slot<String>()
        verify { arango.arango.executeAqlQuery(capture(query), String::class.java) }
        assertEquals(expected, query.captured)
    }

    @Test
    fun `check 'event'{book}'{scope}'{id} works properly`() {
        val expected = """FOR doc IN events
            |FILTER doc.book == "book" AND doc.scope == "scope" AND doc.id == "id"
            |LIMIT 1
            |RETURN doc""".trimMargin()

        arango.getEvent("book", "scope", "id", true)

        val query = slot<String>()
        verify { arango.arango.executeAqlQuery(capture(query), Event::class.java) }
        assertEquals(expected, query.captured)
    }

    @Test
    fun `check 'eventParents'{book}'{scope} works properly`() {
        val expected = """
                |FOR event IN events
                |   FILTER event.book == "book" AND event.scope == "scope"
                |   LET results = []
                |   FOR vertex
                |       IN 0..100
                |       INBOUND event
                |       GRAPH event_graph
                |       COLLECT r = event._key INTO results = vertex
                |       RETURN DISTINCT(results[-1])""".trimMargin()

        arango.getEventParents("book", "scope", mapOf(), true)

        val query = slot<String>()
        verify { arango.arango.executeAqlQuery(capture(query), any(), Event::class.java) }
        assertEquals(expected, query.captured)
    }

    @Test
    fun `check 'attachedEvents'{id} works properly`() {
        val expected = """FOR message IN parsed_messages
                |FILTER message._key == "id"
                |RETURN message.attachedEventIds
                """.trimMargin()

        arango.getAttachedEvents("id", true)

        val query = slot<String>()
        verify { arango.arango.executeAqlQuery(capture(query), String::class.java) }
        assertEquals(expected, query.captured)
    }

    @Test
    fun `check 'events'{book}'{scope} works properly`() {
        val expected = """FOR doc IN events
            |FILTER doc.book == "book" AND doc.scope == "scope" AND doc.id == "id"
            |LIMIT 1
            |RETURN doc""".trimMargin()

        arango.getEvent("book", "scope", "id", true)

        val query = slot<String>()
        verify { arango.arango.executeAqlQuery(capture(query), Event::class.java) }
        assertEquals(expected, query.captured)
    }

    @Test
    fun `check 'search'sse'messages works properly`() {
        val expected = """FOR message IN parsed_messages
            |FILTER message.timestamp >= 1000 AND message.timestamp <= 2000 AND message.alias IN ["session-1"]
            |LIMIT 20, 10
            |RETURN message
            """.trimMargin()

        val parametersMap = mapOf(
            "start-timestamp" to listOf("1000"),
            "end-timestamp" to listOf("2000"),
            "page-size" to listOf("10"),
            "page-number" to listOf("3")
        )
        val sessions = listOf("session-1")
        arango.searchMessages(parametersMap, sessions, true) { }

        val query = slot<String>()
        verify { arango.arango.executeAqlQuery(capture(query), String::class.java, any()) }
        assertEquals(expected, query.captured)
    }

    @Test
    fun `check 'messageBody'{id} works properly`() {
        val expexted = """FOR message IN parsed_messages
            |FILTER message._key == "id"
            |LIMIT 1
            |RETURN message.body""".trimMargin()

        arango.getMessageBody("id", true)

        val query = slot<String>()
        verify { arango.arango.executeAqlQuery(capture(query), String::class.java) }
        assertEquals(expexted, query.captured)
    }
}
