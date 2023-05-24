package utilities

import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext

object AuthUtility {
    private var symmetrical = true

    var key = Keys.secretKeyFor(SignatureAlgorithm.HS256)

    fun generateToken(subject : JsonObject): String {
        return io.jsonwebtoken.Jwts.builder()
            .setSubject(subject.encode())
            .signWith(key)
            .compact()
    }

    fun verifyToken(token : String): JsonObject? {
        return try {
            val subject = io.jsonwebtoken.Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body
                .subject
            JsonObject(subject)
        }
        catch (e : io.jsonwebtoken.security.SecurityException){
            null
        }
    }

    fun getUserId(routingContext: RoutingContext) : Int{
        val token = routingContext.request().getCookie("token")!!
        val subject = AuthUtility.verifyToken(token.value)!!
        val me = subject.getInteger("userId")!!
        return me
    }
}