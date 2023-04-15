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

        runBlocking {
            val it =  testUserDao.getElementByKey(pool,1)

            testContext.verify {
                assertEquals(1,it?.userId )
                assertEquals("test", it?.userName)
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
        runBlocking {
            val it = testUserDao.getAllElements(pool)

            assertNotEquals(it?.size,0)
            assertEquals(it?.get(1)?.userId,1)
            assertEquals(it?.get(1)?.userName,"test")
            testContext.completeNow()
        }
    }

    @Test
    fun getElementsByConditions(testContext: VertxTestContext) {
        runBlocking {
            val it = testUserDao.getElementsByConditions(pool,"username = \$1","test2")
            assertNotEquals(it?.size,0)
            assertEquals(it?.get(2)?.userId,2)
            assertEquals(it?.get(2)?.userName,"test2")

            val eNull = testUserDao.getElementsByConditions(pool,"username = \$1","Xi Jinping")
            assertNull(eNull)
            testContext.completeNow()
        }
    }

    @Test
    fun insert_delete_modify_Element(testContext: VertxTestContext) {
        runBlocking {
            val rand = Random.nextInt(1,10000)
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

            testUserDao.insertElement(pool, entity)

            val inserted = testUserDao.getElementsByConditions(pool,"username = $1",entity.userName!!)

            assertNotNull(inserted)

            var insertedKey:Int? = null

            for (x in inserted){
                if (x.value.registerTime?.withNano(0)?.withOffsetSameInstant(ZoneOffset.UTC) == entity.registerTime?.withNano(0)?.withOffsetSameInstant(
                        ZoneOffset.UTC)){
                    insertedKey = x.key
                }
            }
            assertNotNull(insertedKey)

            val modEntity = UserEntity(
                userName = "modified" + rand,
                userDetail = JsonObject("{\"email\": \"test$rand@example.com\", \"hometown\" : \"Liang Jia He\"}"),
                friendList = JsonArray()
                    .add(JsonObject("{\"id\": ${Random.nextInt()}, \"name\": \"Mod${Random.nextInt()}\"}"))
                    .add(JsonObject("{\"id\": ${Random.nextInt()}, \"name\": \"Mod${Random.nextInt()}\"}")),
            )

            testUserDao.updateElementByConditions(pool,"userid=\$%d",modEntity,insertedKey)

            val modified = testUserDao.getElementByKey(pool, insertedKey)
            assertNotNull(modified)
            assertEquals(modEntity.userName,modified.userName)
            assertEquals(modEntity.userDetail,modified.userDetail)
            assertEquals(modEntity.friendList,modified.friendList)

            testUserDao.deleteElementByKey(pool,insertedKey)

            val deleted = testUserDao.getElementByKey(pool,insertedKey)

            assertNull(deleted)

            testContext.completeNow()
        }
    }
}