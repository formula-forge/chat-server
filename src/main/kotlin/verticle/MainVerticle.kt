package verticle

import io.vertx.core.AbstractVerticle
import io.vertx.ext.web.Router

class MainVerticle : AbstractVerticle() {
    override fun start() {
        vertx.createHttpServer()
            .listen(
                config().getInteger("http.port",1926),
            )

        val router = Router.router(vertx)

        router.get()
    }
}