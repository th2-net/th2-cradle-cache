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
import com.exactpro.th2.cradle.cache.db.Arango
import com.exactpro.th2.cradle.cache.entities.sse.EventType
import com.exactpro.th2.cradle.cache.entities.sse.HttpWriter
import com.exactpro.th2.cradle.cache.entities.sse.SseEvent
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.nio.channels.ClosedChannelException

/**
 * Netty-specific access to information about user request aliveness
 */
suspend fun checkContext(context: ApplicationCall, checkRequestsAliveDelay: Long) {
    context.javaClass.getDeclaredField("call").also {
        it.trySetAccessible()
        val nettyApplicationRequest = it.get(context) as NettyApplicationCall

        while (kotlin.coroutines.coroutineContext.isActive) { // While current coroutine is not closed
            if (nettyApplicationRequest.context.isRemoved) // If user interrupt the request
                throw ClosedChannelException()

            delay(checkRequestsAliveDelay)
        }
    }
}

var checkRequestsAliveDelay: Long = 0
//var jacksonMapper: ObjectMapper = TODO()
//var messageFiltersPredicateFactory: Int = TODO()
//var searchMessagesHandler: Int = TODO()

fun Application.configureRouting(db: Arango, appCtx: Context) {
    checkRequestsAliveDelay = appCtx.configuration.checkRequestsAliveDelay
    val notModifiedCacheControl = appCtx.cacheControlNotModified
    val rarelyModifiedCacheControl = appCtx.cacheControlRarelyModified
    val jacksonMapper = appCtx.jacksonMapper

    routing {
        get("/event/{book}/{scope}/{id}") {
            val book = call.parameters.getOrFail("book")
            val scope = call.parameters.getOrFail("scope")
            val id = call.parameters.getOrFail("id")
            val probe = call.parameters["probe"]?.toBoolean() ?: false
            // If probe=true then do not throw errors for empty result
            handleRestApiRequest(call, context, notModifiedCacheControl) { db.getEvent(book, scope, id, probe) }
        }
        get("/eventChildren") {
            val queryParametersMap = call.request.queryParameters.toMap()
            val eventId = call.parameters["id"]
            val offset = call.parameters["offset"]?.toLong()
            val limit = call.parameters["limit"]?.toLong()
            val searchDepth = call.parameters["search-depth"]?.toLong() ?: 1
            val probe = call.parameters["probe"]?.toBoolean() ?: false
            handleRestApiRequest(call, context, notModifiedCacheControl) {
                db.getEventChildren(queryParametersMap, eventId, offset, limit, searchDepth, probe)
            }
        }
        get("/messageStream") {
            handleRestApiRequest(call, context, rarelyModifiedCacheControl) { db.getMessageStreams() }
        }
        get("/message/{id}") {
            val messageId = call.parameters.getOrFail("id")
            val probe = call.parameters["probe"]?.toBoolean() ?: false
            handleRestApiRequest(call, context, rarelyModifiedCacheControl) { db.getMessage(messageId, probe) }
        }
        get("/search/sse/messages") {
            val queryParametersMap = call.request.queryParameters.toMap()
            val probe = call.parameters["probe"]?.toBoolean() ?: false
            val sessions: List<String> = call.parameters.getAll("stream")
                ?: throw MissingRequestParameterException("stream")
            handleSseRequest(call, context, jacksonMapper) { writer ->
                db.searchMessages(queryParametersMap, sessions, probe) {
                    writer.eventWrite(SseEvent(it, EventType.MESSAGE))
                }
            }
        }
        get("/messageBody/{id}") {
            val messageId = call.parameters.getOrFail("id")
            val probe = call.parameters["probe"]?.toBoolean() ?: false
            handleRestApiRequest(call, context, rarelyModifiedCacheControl) { db.getMessageBody(messageId, probe) }
        }
    }
}

@OptIn(InternalAPI::class)
private suspend fun handleSseRequest(
    call: ApplicationCall,
    context: ApplicationCall,
    jacksonMapper: ObjectMapper,
    calledFun: suspend (HttpWriter) -> Any
) {
    coroutineScope {
        launch {
            val job = launch {
                checkContext(context, checkRequestsAliveDelay)
            }
            call.response.headers.append(HttpHeaders.CacheControl, "no-cache, no-store, no-transform")
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                val httpWriter = HttpWriter(this, jacksonMapper)

                try {
                    calledFun.invoke(httpWriter)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    httpWriter.eventWrite(SseEvent.build(jacksonMapper, e))
                    throw e
                } finally {
                    kotlin.runCatching {
                        httpWriter.eventWrite(SseEvent(event = EventType.CLOSE))
                        httpWriter.closeWriter()
                        job.cancel()
                    }.onFailure { e -> throw e }
                }
            }
        }.join()
    }
}

private suspend fun handleRestApiRequest(
    call: ApplicationCall,
    context: ApplicationCall,
    cacheControl: CacheControl?,
    calledFun: suspend () -> Any?
) {
    coroutineScope {
        launch {
            checkContext(context, checkRequestsAliveDelay)
        }
        cacheControl?.let { call.response.cacheControl(it) }

        call.respond(HttpStatusCode.OK, calledFun.invoke() ?: "null")

        coroutineContext.cancelChildren()
        // Stop checkContext coroutine (all inside coroutineScope) because we successfully completed the request
    }
}
