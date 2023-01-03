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

package com.exactpro.th2.cradle.cache.db

import com.exactpro.th2.cache.common.Arango
import com.exactpro.th2.cache.common.Arango.Companion.PARSED_MESSAGE_COLLECTION
import com.exactpro.th2.cache.common.Arango.Companion.EVENT_COLLECTION
import com.exactpro.th2.cache.common.Arango.Companion.EVENT_GRAPH
import com.exactpro.th2.cache.common.ArangoCredentials
import com.exactpro.th2.cache.common.event.Event
import com.exactpro.th2.cradle.cache.entities.response.event.EventResponse
import com.exactpro.th2.cradle.cache.entities.exceptions.DataNotFoundException
import com.exactpro.th2.cradle.cache.entities.exceptions.InvalidRequestException
import java.util.function.Consumer

class Arango(credentials: ArangoCredentials) : AutoCloseable {
    val arango: Arango = Arango(credentials)

    fun getMessage(messageId: String, probe: Boolean): String? {
        val query = """FOR message IN $PARSED_MESSAGE_COLLECTION
            |FILTER message._key == "$messageId"
            |LIMIT 1
            |RETURN message""".trimMargin()
        return arango.executeAqlQuery(query, String::class.java)
            .ifEmpty { if (probe) null else throw DataNotFoundException("Message not found by id: $messageId") }
            ?.first()
    }

    fun getMessageBody(messageId: String, probe: Boolean): String? {
        val query = """FOR message IN $PARSED_MESSAGE_COLLECTION
            |FILTER message._key == "$messageId"
            |LIMIT 1
            |RETURN message.message""".trimMargin()
        return arango.executeAqlQuery(query, String::class.java)
            .ifEmpty { if (probe) null else throw DataNotFoundException("Message not found by id: $messageId") }
            ?.first()
    }

    fun getMessageStreams(): List<String> {
        val query = """FOR message IN $PARSED_MESSAGE_COLLECTION
            |RETURN FIRST(SPLIT(message._key, ":"))
            """.trimMargin()
        return arango.executeAqlQuery(query, String::class.java)
    }

    fun searchMessages(queryParametersMap: Map<String, List<String>>, sessions: List<String>, probe: Boolean, action: Consumer<String>) {
        val startTimestamp = queryParametersMap["start-timestamp"]?.get(0)?.let {
            "message.timestamp >= $it"
        }
        val pageSize = queryParametersMap["page-size"]?.get(0)
        val pageNumber = queryParametersMap["page-number"]?.get(0)
        val limitStatement =
            if (pageSize != null) {
                if (pageNumber == null) {
                    "LIMIT $pageSize"
                } else {
                    val offset = pageSize.toInt() * (pageNumber.toInt() - 1)
                    "LIMIT $offset, $pageSize"
                }
            } else {
                ""
            }
        val endTimestamp = queryParametersMap["end-timestamp"]?.get(0)?.let {
            "message.timestamp <= $it"
        }
        val session = sessions.let {
            val withQuotations = it.map { elem ->
                "\"" + elem + "\""
            }
            "message.alias IN $withQuotations"
        }
//        val searchDirection = queryParametersMap["search-direction"]?.get(0)?.let {
//            when (it) {
//                "next" -> {
//
//                }
//                "prev" -> {
//
//                }
//                else -> {
//                    throw InvalidRequestException("search-direction should have value either next or prev")
//                }
//            }
//        } TODO
        val filters = listOfNotNull(startTimestamp, endTimestamp, session)
        val filterStatement = filters.joinToString(" AND ")
        val query = """FOR message IN $PARSED_MESSAGE_COLLECTION
            |FILTER $filterStatement
            |$limitStatement
            |RETURN message
            """.trimMargin()
        arango.executeAqlQuery(query, String::class.java, action)
    }

    fun searchEvents(book: String, queryParametersMap: Map<String, List<String>>, limit: Long?, probe: Boolean): List<EventResponse>? {
        // TODO: add filtering on attachedMessageIds
        val bookFilter = "event.book == \"$book\""
        val searchDirection = queryParametersMap["search-direction"]?.get(0)?.let {
            if (queryParametersMap["start-timestamp"] == null) {
                throw InvalidRequestException("start-timestamp should be specified in order to use search-direction")
            }
            when (it) {
                "next" -> "ASC"
                "prev" -> "DESC"
                else -> throw InvalidRequestException("search-direction should be either next or prev")
            }
        }
        val startTimestamp = queryParametersMap["start-timestamp"]?.get(0)?.let {
                if (searchDirection == "DESC") {
                    "event.startTimestamp <= $it"
                } else {
                    "event.startTimestamp >= $it"
                }
            }
        val endTimestamp = queryParametersMap["end-timestamp"]?.get(0)?.let {
                if (searchDirection == "DESC") {
                    "event.startTimestamp >= $it"
                } else {
                    "event.startTimestamp <= $it"
                }
            }
        val parentId = queryParametersMap["parent-id"]?.get(0)?.let {
            "event.parentEventId == \"$it\""
        }
        val body = queryParametersMap["body"]?.get(0)?.let {
            "event.body == \"$it\""
        }
        val status = queryParametersMap["status"]?.get(0)?.let {
            "event.successful == $it"
        }
        val nameNegative = queryParametersMap["name-negative"]?.get(0)?.let {
            if (it == "true") "NOT" else ""
        } ?: ""
        val nameConjunct = queryParametersMap["name-conjunct"]?.get(0)?.let {
            if (it == "true") " AND " else " OR "
        } ?: " OR "
        val nameStrict = queryParametersMap["name-strict"]?.get(0)?.let {
            it == "true"
        } ?: false
        val nameValues = queryParametersMap["name-values"]?.let { namesList ->
            "($nameNegative ${namesList.joinToString(nameConjunct) { if (nameStrict) "event.eventName == \"$it\"" else "CONTAINS(event.eventName, \"$it\")" }})"
        }
        val typeNegative = queryParametersMap["type-negative"]?.get(0)?.let {
            if (it == "true") "NOT" else ""
        } ?: ""
        val typeConjunct = queryParametersMap["type-conjunct"]?.get(0)?.let {
            if (it == "true") " AND " else " OR "
        } ?: " OR "
        val typeStrict = queryParametersMap["type-strict"]?.get(0)?.let {
            it == "true"
        } ?: false
        val typeValues = queryParametersMap["type-values"]?.let { typesList ->
            "($typeNegative ${typesList.joinToString(typeConjunct) { if (typeStrict) "event.eventName == \"$it\"" else "CONTAINS(event.eventName, \"$it\")" }})"
        }
        val filters = listOfNotNull(bookFilter, startTimestamp, endTimestamp, parentId, nameValues, typeValues, body, status)
        val filterStatement = filters.joinToString(" AND ")
        val limitStatement = if (limit == null) "" else "LIMIT $limit"
        val sortStatement = "SORT event.startTimestamp $searchDirection"
        val query = """FOR event IN $EVENT_COLLECTION
            |FILTER $filterStatement
            |$limitStatement
            |$sortStatement
            |RETURN event""".trimMargin()
        return arango.executeAqlQuery(query, Event::class.java)
            .ifEmpty { if (probe) null else throw DataNotFoundException("Events not found by specified parameters") }
            ?.map { EventResponse(it) }
    }

    fun getEvent(book: String, scope: String, id: String, probe: Boolean): EventResponse? {
        val query = """FOR doc IN $EVENT_COLLECTION
            |FILTER doc.book == "$book" AND doc.scope == "$scope" AND doc.id == "$id"
            |LIMIT 1
            |RETURN doc""".trimMargin()
        return arango.executeAqlQuery(query, Event::class.java)
            .ifEmpty { if (probe) null else throw DataNotFoundException("Event not found by id: $id") }
            ?.first()?.let { EventResponse(it) }
    }

    fun getEventChildren(queryParametersMap: Map<String, List<String>>, eventId: String?, offset: Long?, limit: Long?, searchDepth: Long, probe: Boolean): List<String>? {
        val searchDirection = queryParametersMap["search-direction"]?.get(0)?.let {
            if (queryParametersMap["start-timestamp"] == null) {
                throw InvalidRequestException("start-timestamp should be specified in order to use search-direction")
            }
            when (it) {
                "next" -> "ASC"
                "prev" -> "DESC"
                else -> throw InvalidRequestException("search-direction should be either next or prev")
            }
        }
        val startTimestamp: (String) -> String? = { variableName->
            queryParametersMap["start-timestamp"]?.get(0)?.let {
                if (searchDirection == "DESC") {
                    "$variableName.startTimestamp <= $it"
                } else {
                    "$variableName.startTimestamp >= $it"
                }
            }
        }
        val endTimestamp: (String) -> String? = { variableName ->
            queryParametersMap["end-timestamp"]?.get(0)?.let {
                if (searchDirection == "DESC") {
                    "$variableName.startTimestamp >= $it"
                } else {
                    "$variableName.startTimestamp <= $it"
                }
            }
        }
        val nameNegative = queryParametersMap["name-negative"]?.get(0)?.let {
            if (it == "true") "NOT" else ""
        } ?: ""
        val nameConjunct = queryParametersMap["name-conjunct"]?.get(0)?.let {
            if (it == "true") " AND " else " OR "
        } ?: " OR "
        val nameStrict = queryParametersMap["name-strict"]?.get(0)?.let {
            it == "true"
        } ?: false
        val nameValues: (String) -> String? = { variableName ->
            queryParametersMap["name-values"]?.let { namesList ->
                "($nameNegative ${namesList.joinToString(nameConjunct) { if (nameStrict) "$variableName.eventName == \"$it\"" else "CONTAINS($variableName.eventName, \"$it\")" }})"
            }
        }
        val typeNegative = queryParametersMap["type-negative"]?.get(0)?.let {
            if (it == "true") "NOT" else ""
        } ?: ""
        val typeConjunct = queryParametersMap["type-conjunct"]?.get(0)?.let {
            if (it == "true") " AND " else " OR "
        } ?: " OR "
        val typeStrict = queryParametersMap["type-strict"]?.get(0)?.let {
            it == "true"
        } ?: false
        val typeValues: (String) -> String? = { variableName ->
            queryParametersMap["type-values"]?.let { typesList ->
                "($typeNegative ${typesList.joinToString(typeConjunct) { if (typeStrict) "$variableName.eventType == \"$it\"" else "CONTAINS($variableName.eventType, \"$it\")" }})"
            }
        }
        val filters: (String) -> List<String> = { variableName ->
            listOfNotNull(nameValues(variableName), typeValues(variableName), startTimestamp(variableName), endTimestamp(variableName))
        }
        val filterStatement: (String) -> String = { variableName ->
            val filtersJoined = filters(variableName).joinToString(" AND ")
            if (filtersJoined == "") {
                "true"
            } else {
                filtersJoined
            }
        }
        val limitStatement =
            if (limit != null)
                if (offset != null) "LIMIT $offset, $limit" else "LIMIT $limit"
            else ""
        val sortStatement: (String) -> String = { variableName ->
            "SORT $variableName.startTimestamp $searchDirection"
        }
        val query = if (eventId == null) {
            """FOR doc IN $EVENT_COLLECTION     
               |    FILTER doc.parentEventId == "" AND ${filterStatement("doc")}
               |    $limitStatement
               |    ${sortStatement("doc")}
               |    RETURN doc._key""".trimMargin()
        } else {
            if (queryParametersMap["name-values"] == null && queryParametersMap["type-values"] == null) {
                """LET eventNode = (
               |   FIRST(
               |       FOR doc IN $EVENT_COLLECTION
               |           FILTER doc._key == "$eventId" 
               |           RETURN doc
               |   )
               |)
               |FOR vertex
               |   IN 1..$searchDepth
               |   OUTBOUND eventNode
               |   GRAPH $EVENT_GRAPH
               |   FILTER ${filterStatement("vertex")}
               |   $limitStatement
               |   ${sortStatement("vertex")}
               |   RETURN vertex._key""".trimMargin()
            } else {
                """LET id = (
               |   FOR doc IN $EVENT_COLLECTION
               |       FILTER doc._key == "$eventId"
               |       LIMIT 1
               |       RETURN doc._id
               |    )
               |LET vertexArrays = (
               |    FOR doc IN $EVENT_COLLECTION
               |        FILTER ${filterStatement("doc")}
               |        $limitStatement
               |        FOR path
               |            IN 1..$searchDepth
               |            OUTBOUND K_PATHS
               |            id[0] TO doc._id
               |            GRAPH $EVENT_GRAPH
               |            RETURN COUNT(path.vertices[*]) > 2 ? POP(SHIFT(path.vertices[*])) : SHIFT(path.vertices[*])
               |    )
               |LET result = (
               |    FOR vertexArray IN vertexArrays
               |        FILTER COUNT(vertexArray) == 1 
               |            OR POP(vertexArray.vertices)[* FILTER ${filterStatement("CURRENT")}] NONE == true
               |        RETURN vertexArray[*]._key
               |    )    
               |FOR doc IN FLATTEN(UNIQUE(result))
               |    ${sortStatement("doc")}
               |    RETURN doc""".trimMargin()
            }
        }
        return arango.executeAqlQuery(
            query,
            String::class.java
        ).ifEmpty { if (probe) null else throw DataNotFoundException("Event's children not found by id: $eventId and with specified query parameters") }
    }

    override fun close() {
        arango.close()
    }
}
