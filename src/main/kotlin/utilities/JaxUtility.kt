package utilities

import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetSocket
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.coroutines.await
import org.slf4j.LoggerFactory

class JaxUtility(vertx: Vertx, port: Int, host: String) {
    val vertx = vertx
    val host = host
    val port = port

    suspend fun parseFormula(formula: String, socket: NetSocket? = null): String {
        val promise = Promise.promise<String>()
        val workSocket = socket ?: vertx.createNetClient().connect(port, host).await()

        try {
            workSocket.write(formula + Char(0))
        } catch (e : Exception){
            if (socket != null) {
                socket.close()
                return parseFormula(formula, null)
            }
            else{
                throw e
            }
        }

        val builder = StringBuilder()

        workSocket.handler {
            builder.append(it.toString())
            if (it.toString().endsWith(Char(0))) {
                promise.complete(builder.substring(0, builder.length - 1))
                if (socket == null) {
                    workSocket.close()
                }
            }
        }

        return promise.future().await()
    }

    suspend fun transformFav(favFormula : JsonObject) : JsonObject{
        val logger = LoggerFactory.getLogger(this::class.java)
        logger.info("transform fav formula, host: $host, port: $port")
        val socket = try {
            vertx.createNetClient().connect(port, host).await()
        } catch (e : Exception){
            logger.error("connect to jax failed", e)
            return favFormula
        }
        logger.info("connect to jax success")
        try {
            favFormula.forEach { formulaClass->
                val formulas = formulaClass.value as JsonArray
                formulas.forEach { formulaRaw->
                    val formula = formulaRaw as JsonObject?
                    if (formula == null || formula.getString("format") == "svg")
                        return@forEach
                    val face = formula.getString("face")
                    formula.put("face",parseFormula(face, socket))
                    formula.put("format", "svg")
                }
            }
        } catch (e : Exception) {
            logger.error("transform fav formula error", e)
        }
        socket.close()
        return favFormula
    }
}