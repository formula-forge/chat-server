package utilities

import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.Json
import kotlinx.coroutines.Job
import javax.crypto.SecretKey

object AuthUtility {
    val key = Keys.secretKeyFor(SignatureAlgorithm.HS256)

    fun generateToken(subject : JsonObject): String {
        return io.jsonwebtoken.Jwts.builder()
            .setSubject(subject.encode())
            .signWith(SignatureAlgorithm.HS256, key)
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
}