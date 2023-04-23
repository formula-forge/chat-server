package dao

import dao.entities.UserEntity
import io.vertx.core.Future
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals

import io.vertx.kotlin.coroutines.*
import kotlinx.coroutines.runBlocking

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.*
