package services

import io.vertx.core.buffer.Buffer
import io.vertx.core.file.FileSystem
import io.vertx.core.file.OpenOptions
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.multipart.MultipartForm
import io.vertx.kotlin.core.Vertx
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import utilities.ServerUtility
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.security.MessageDigest
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

class Image(fileSystem: FileSystem, path : Path, vertx: io.vertx.core.Vertx) {
    private val md5 = MessageDigest.getInstance("MD5")

    @OptIn(DelicateCoroutinesApi::class)
    val upload = fun(routingContext: RoutingContext) {
        routingContext.response().putHeader("Content-Type", "application/json")
        val req = routingContext.request()
        if (!req.getHeader("Content-Type").startsWith("image")) {
            ServerUtility.responseError(routingContext, 400, 9, "不是图片")
            return
        }
        req.bodyHandler { buff->
            GlobalScope.launch(vertx.dispatcher()) {
                try {
                    val digestBytes = md5.digest(buff.bytes)
                    val fileNameBuilder = StringBuilder()
                    for(b in digestBytes){
                        fileNameBuilder.append(String.format("%02x", b))
                    }
                    fileNameBuilder.append(".png")
                    val file = path.resolve(fileNameBuilder.toString() )
                    if (!fileSystem.exists(file.toString()).await()) {
                        val inpBuff = ByteArrayInputStream(buff.bytes)
                        val image = try { ImageIO.read(inpBuff) }
                        catch (e : Exception){
                            ServerUtility.responseError(routingContext, 400, 9, "图片解析失败")
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
                        writer.write(null, javax.imageio.IIOImage(result, null, null), writeParam)
                        val writeBuff = Buffer.buffer(outBuff.toByteArray())
                        fileSystem.writeFile(file.toString(), writeBuff).await()
                    }
                    ServerUtility.responseSuccess(routingContext, 201, json {
                        obj(
                            "url" to "/img/${fileNameBuilder.toString()}"
                        )
                    })
                } catch (e : Exception){
                    ServerUtility.responseError(routingContext, 500, 30, "服务器错误")
                    e.printStackTrace()
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    val download = fun(routingContext: RoutingContext){
        GlobalScope.launch {
            try {
                val fileName = routingContext.pathParam("file")
                if (fileName == null) {
                    ServerUtility.responseError(routingContext, 400, 1, "参数错误")
                    return@launch
                }
                var file = path.resolve(fileName)
                if (!fileSystem.exists(file.toString()).await()) {
                    routingContext.response().setStatusCode(404)
                    file = path.resolve("default.png")
                }

                val resource = fileSystem.readFile(
                    file.toString(),
                ).await()

                routingContext.response().putHeader("Content-Type", "image/png")
                routingContext.response().end(resource)
            } catch (e : Exception){
                ServerUtility.responseError(routingContext, 500, 30, "服务器错误")
            }
        }
    }
}