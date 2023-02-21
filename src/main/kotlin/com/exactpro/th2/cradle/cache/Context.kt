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

package com.exactpro.th2.cradle.cache

import com.exactpro.th2.cradle.cache.db.Arango
import com.exactpro.th2.cradle.cache.configuration.Configuration
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*

@Suppress("MemberVisibilityCanBePrivate")
class Context(
    val configuration: Configuration,

    val arango: Arango,

    val jacksonMapper: ObjectMapper = jacksonObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false),

    val responseTimeout: Long = configuration.responseTimeout.toLong(),

    private val enableCaching: Boolean = configuration.enableCaching,

    /**
     * Client-side cache will be stored by client for a long time. For objects which edit hardly ever
     */
    val cacheControlNotModified: CacheControl = cacheControlConfig(configuration.notModifiedObjectsLifetime, enableCaching),

    /**
     * Client-side cache will be stored by client for a middle time. For objects which edit often
     */
    val cacheControlRarelyModified: CacheControl = cacheControlConfig(configuration.rarelyModifiedObjects, enableCaching),
) {
    companion object {
        private fun cacheControlConfig(timeout: Int, enableCaching: Boolean): CacheControl {
            return if (enableCaching) {
                CacheControl.MaxAge(
                    visibility = CacheControl.Visibility.Public,
                    maxAgeSeconds = timeout,
                    mustRevalidate = false,
                    proxyRevalidate = false,
                    proxyMaxAgeSeconds = timeout
                )
            } else {
                CacheControl.NoCache(
                    visibility = CacheControl.Visibility.Public
                )
            }
        }
    }
}
