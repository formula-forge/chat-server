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

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.contracts.contract
import kotlin.random.Random
import kotlin.test.*

@ExtendWith(VertxExtension::class)
class UserDaoTest {

    private val testUserDao = UserDao()
    private val pool = ConnectionPool.getPool()
    private lateinit var fut_id : Future<Int>

    @Test
    fun getElementByKey(testContext: VertxTestContext) {
        testContext.assertComplete(
            testUserDao.getElementByKey(pool,1)
        ).onSuccess{
                testContext.verify {
                    assertEquals(1,it?.userId )
                    assertEquals("test", it?.userName)
//                    assertEquals("123456",it?.passWord)
                    assertEquals("156446",it?.telephone)
                    assertEquals(OffsetDateTime.of(2023,4,12,14,12,31,864947000, ZoneOffset.UTC)
                        ,it?.registerTime)
                    assertEquals("test@example.com",it?.userDetail?.getString("email"))
                    assertEquals("1926-8-17",it?.userDetail?.getString("birthday"))
                    assertEquals("Nine Slap Navy",it?.userDetail?.getString("hometown"))
                    assertEquals(2,it?.friendList?.getJsonObject(0)?.getInteger("id"))
                    assertEquals("T2",it?.friendList?.getJsonObject(0)?.getString("name"))
                    assertEquals(4,it?.friendList?.getJsonObject(1)?.getInteger("id"))
                    assertEquals("Wang", it?.friendList?.getJsonObject(1)?.getString("name"))
                    assertEquals(1,it?.groupList?.getJsonObject(0)?.getInteger("id"))
                    assertEquals("",it?.groupList?.getJsonObject(0)?.getString("name"))
                    assertEquals(2,it?.groupList?.getJsonObject(1)?.getInteger("id"))
                    assertEquals("Foo",it?.groupList?.getJsonObject(1)?.getString("name"))
                    testContext.completeNow()
                }

        }
    }

    @Test
    fun getAllElements(testContext: VertxTestContext) {
        testUserDao.getAllElements(pool).onSuccess{
            testContext.verify {
                assertNotEquals(it?.size,0)
                assertEquals(it?.get(1)?.userId,1)
                assertEquals(it?.get(1)?.userName,"test")
                testContext.completeNow()
            }
        }
    }

    @Test
    fun getElementsByConditions(testContext: VertxTestContext) {
        testUserDao.getElementsByConditions(pool,"username = \$1","test2").onSuccess{
            testContext.verify {
                assertNotEquals(it?.size,0)
                assertEquals(it?.get(2)?.userId,2)
                assertEquals(it?.get(2)?.userName,"test2")
            }
        }.onFailure {
            fail(it.message)
        }

        testUserDao.getElementsByConditions(pool,"username = \$1","siqi").onSuccess{
            testContext.verify {
                assertNull(it)
                testContext.completeNow()
            }
        }.onFailure {
            testContext.failNow(it)
        }

    }

    @Test
    fun insertElement(testContext: VertxTestContext) {
        val rand = Random.nextInt(1,10000)

        fut_id = Future.future{

        }

        val entity = UserEntity(
            userId = null,
            userName = "test" + rand,
            userDetail = JsonObject("{\"email\": \"test$rand@example.com\"}"),
            friendList = JsonArray()
                .add(JsonObject("{\"id\": ${Random.nextInt()}, \"name\": \"Foo${Random.nextInt()}\"}"))
                .add(JsonObject("{\"id\": ${Random.nextInt()}, \"name\": \"Foo${Random.nextInt()}\"}")),
            groupList = JsonArray()
                .add(JsonObject("{\"id\": ${Random.nextInt()}, \"name\": \"Foo${Random.nextInt()}\"}"))
                .add(JsonObject("{\"id\": ${Random.nextInt()}, \"name\": \"Foo${Random.nextInt()}\"}")),
            registerTime = OffsetDateTime.now(),
            telephone = "${Random.nextInt(10000000,100000000)}${Random.nextInt(100,1000)}",
            passWord = "P#AS${Random.nextInt(10000000,100000000)}"
        )

        testUserDao.insertElement(pool, entity).onSuccess{
            testUserDao.getElementsByConditions(pool,"username = $1",entity.userName!!).onSuccess{
                testContext.verify {
                    var flag = false
                    assertNotNull(it)
                    for (x in it){
                        if (x.value.registerTime?.withNano(0)?.withOffsetSameInstant(ZoneOffset.UTC) == entity.registerTime?.withNano(0)?.withOffsetSameInstant(
                                ZoneOffset.UTC)){
                            flag = true
                        }
                    }
                    assertTrue(flag)
                    testContext.completeNow()
                }
            }.onFailure {
                testContext.failNow(it)
            }
        }.onFailure {
            testContext.failNow(it)
        }
    }

    @Test
    fun deleteElementByKey(testContext: VertxTestContext) {

    }

    @Test
    fun updateElementByConditions(testContext: VertxTestContext) {

    }
}