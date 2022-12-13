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

import com.exactpro.th2.common.metrics.liveness
import com.exactpro.th2.common.metrics.readiness
import com.exactpro.th2.common.schema.factory.CommonFactory
import com.exactpro.th2.cradle.cache.db.Arango
import com.exactpro.th2.cradle.cache.entities.configuration.Configuration
import com.exactpro.th2.cradle.cache.http.HttpServer
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

class Main(args: Array<String>) {
    private val configurationFactory: CommonFactory
    private val context: Context

    private val resources: Deque<AutoCloseable> = ConcurrentLinkedDeque()
    private val lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()

    init {
        configureShutdownHook(resources, lock, condition)
        configurationFactory = CommonFactory.createFromArguments(*args)
        resources += configurationFactory
        val configuration =
            Configuration(configurationFactory.getCustomConfiguration(Configuration::class.java))
        val arango = Arango()
        resources += arango
        context = Context(
            configuration,
            arango = arango
        )
    }

    fun run() {
        logger.info { "Starting the box" }

        liveness = true

        HttpServer(context).run()

        readiness = true

        awaitShutdown(lock, condition)
    }

    private fun configureShutdownHook(resources: Deque<AutoCloseable>, lock: ReentrantLock, condition: Condition) {
        Runtime.getRuntime().addShutdownHook(thread(
            start = false,
            name = "Shutdown hook"
        ) {
            logger.info { "Shutdown start" }
            readiness = false
            try {
                lock.lock()
                condition.signalAll()
            } finally {
                lock.unlock()
            }
            resources.descendingIterator().forEachRemaining { resource ->
                try {
                    resource.close()
                } catch (e: Exception) {
                    logger.error(e) { "Cannot close resource ${resource::class}" }
                }
            }
            liveness = false
            logger.info { "Shutdown end" }
        })
    }

    @Throws(InterruptedException::class)
    private fun awaitShutdown(lock: ReentrantLock, condition: Condition) {
        try {
            lock.lock()
            logger.info { "Wait shutdown" }
            condition.await()
            logger.info { "App shutdown" }
        } finally {
            lock.unlock()
        }
    }
}

fun main(args: Array<String>) {
    try {
        Main(args).run()
    } catch (ex: Exception) {
        logger.error(ex) { "Cannot start the box" }
        exitProcess(1)
    }
}
