package services

import dao.ConnectionPool
import dao.GroupDao
import dao.UserDao
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.FileSystem
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import utilities.ServerUtility
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.security.MessageDigest


import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

object Image {
    lateinit var fileSystem: FileSystem
    lateinit var path: Path

    private val md5 = MessageDigest.getInstance("MD5")
    private val userDao = UserDao()
    private val groupDao = GroupDao()

    private var default: Buffer? = null

    private suspend fun responseDefault(routingContext: RoutingContext, code: Int) {
        if (default == null) {
            val file = path.resolve("default.png")
            default = fileSystem.readFile(file.toString()).await()
        }
        routingContext.response().setStatusCode(code)
        routingContext.response().putHeader("Content-Type", "image/png")
        routingContext.response().end(default)
    }

    private suspend fun responseImage(routingContext: RoutingContext, fileName: String) {
        val file = path.resolve(fileName)
        if (!fileSystem.exists(file.toString()).await()) {
            responseDefault(routingContext, 404)
            return
        }
        val buffer = fileSystem.readFile(file.toString()).await()
        routingContext.response().setStatusCode(200)
        routingContext.response().putHeader("Content-Type", "image/png")
        routingContext.response().end(buffer)
    }

    @OptIn(DelicateCoroutinesApi::class)
    val upload = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "upload")
        routingContext.response().putHeader("Content-Type", "application/json")
        val req = routingContext.request()
        if (!req.getHeader("Content-Type").startsWith("image")) {
            ServerUtility.responseError(routingContext, 400, 9, "不是图片", logger)
            return
        }
        req.bodyHandler { buff ->
            GlobalScope.launch(routingContext.vertx().dispatcher()) {
                logger.info("Receiving image")
                try {
                    val digestBytes = md5.digest(buff.bytes)
                    val fileNameBuilder = StringBuilder()
                    for (b in digestBytes) {
                        fileNameBuilder.append(String.format("%02x", b))
                    }

                    val fileHash = fileNameBuilder.toString()

                    fileNameBuilder.append(".png")

                    val file = path.resolve(fileNameBuilder.toString())
                    if (!fileSystem.exists(file.toString()).await()) {
                        val inpBuff = ByteArrayInputStream(buff.bytes)
                        val image = try {
                            ImageIO.read(inpBuff)
                        } catch (e: Exception) {
                            ServerUtility.responseError(routingContext, 400, 9, "图片解析失败", logger)
                            return@launch
                        }
                        val resized = image.getScaledInstance(128, 128, Image.SCALE_SMOOTH)
                        val result = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
                        val g2d = result.createGraphics()
                        g2d.drawImage(resized, 0, 0, null)
                        val outBuff = java.io.ByteArrayOutputStream()
                        val writer = ImageIO.getImageWritersByFormatName("png").next()
                        val writeParam = writer.defaultWriteParam
                        writeParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
                        writeParam.compressionQuality = 0.5f
                        writer.output = MemoryCacheImageOutputStream(outBuff)
                        logger.info("Writing image $file")
                        writer.write(null, javax.imageio.IIOImage(result, null, null), writeParam)
                        val writeBuff = Buffer.buffer(outBuff.toByteArray())
                        fileSystem.writeFile(file.toString(), writeBuff).await()
                    }
                    ServerUtility.responseSuccess(routingContext, 201, json {
                        obj(
                            "url" to fileHash
                        )
                    }, logger)
                } catch (e: Exception) {
                    ServerUtility.responseError(routingContext, 500, 30, "服务器错误", logger)
                    logger.warn(e.message, e)
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    val download = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "download")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                val fileName = routingContext.pathParam("file")
                if (fileName == null) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误", logger)
                    return@launch
                }
                responseImage(routingContext, fileName)
            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器错误", logger)
                logger.warn(e.message, e)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    val avatar = fun(routingContext: RoutingContext) {
        val logger = LoggerFactory.getLogger(this::class.qualifiedName + "avatar")
        GlobalScope.launch(routingContext.vertx().dispatcher()) {
            try {
                val type = routingContext.pathParam("type")
                val id = routingContext.pathParam("id")?.toIntOrNull()
                if (id == null) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误", logger)
                    return@launch
                }
                logger.info("Getting avatar of $type $id")
                val fileName: String? = when (type) {
                    "user" -> {
                        userDao.getElementByKey(ConnectionPool.getPool(routingContext.vertx()), id)?.avatar
                    }

                    "group" -> {
                        groupDao.getElementByKey(ConnectionPool.getPool(routingContext.vertx()), id)?.avatar
                    }

                    else -> {
                        ServerUtility.responseError(routingContext, 400, 1, "参数错误", logger)
                        return@launch
                    }
                }

                if (fileName == null) ServerUtility.responseError(routingContext, 404, 1, "对象不存在", logger)
                else responseImage(routingContext, "$fileName.png")

            } catch (e: Exception) {
                ServerUtility.responseError(routingContext, 500, 30, "服务器错误", logger)
                logger.warn(e.message, e)
            }
        }
    }
}