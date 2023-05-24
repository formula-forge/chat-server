package utilities

import TooManyRequestException
import com.aliyun.auth.credentials.Credential
import com.aliyun.auth.credentials.provider.StaticCredentialProvider
import com.aliyun.sdk.service.dysmsapi20170525.AsyncClient
import com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsRequest
import com.google.common.cache.CacheBuilder
import darabonba.core.client.ClientOverrideConfiguration
import org.slf4j.Logger
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextUInt

object MessageUtility {
    var accessKeySecret = "Fxev"

    var accessKeyId = "LTAI"

    private val codeCache = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<String, code>()


    fun sendCode(phone : String, logger : Logger){
        val credentialProvider = StaticCredentialProvider.create(
            Credential
                .builder()
                .accessKeyId(accessKeyId)
                .accessKeySecret(accessKeySecret)
                .build()
        )

        val client = AsyncClient.builder()
            .region("cn-hangzhou")
            .credentialsProvider(credentialProvider)
            .overrideConfiguration(
                ClientOverrideConfiguration.create()
                    .setEndpointOverride("dysmsapi.aliyuncs.com")
                    .setConnectTimeout(Duration.ofSeconds(5))
            )
            .build()

        if(!CheckUtility.checkPhone(phone)){
            throw IllegalArgumentException("phone number is illegal")
        }
        val code = Random.nextUInt(100000u, 999999u)

        val existed = codeCache.getIfPresent(phone)

        if (existed!= null){
            codeCache.invalidate(phone)
            val wait = Duration.ofMinutes(1) - Duration.between(existed.time, LocalDateTime.now(ZoneOffset.ofHours(8)))

            if (wait > Duration.ZERO){
                throw TooManyRequestException("too many request", wait.toSeconds())
            }
        }

        val smsRequest = SendSmsRequest.builder()
            .signName("FormulaForge")
            .templateCode("SMS_460695263")
            .phoneNumbers(phone)
            .templateParam("{\"code\":\"$code\"}")
            .build()

        val response = client.sendSms(smsRequest)

        logger.info("send code $code to $phone with result ${response.get().toMap()}")

        codeCache.put(phone, code(phone, code.toInt(), LocalDateTime.now(ZoneOffset.ofHours(8))))
    }

    fun verifyCode(phone : String, code : Int) : Boolean {
        val existed = codeCache.getIfPresent(phone)

        if (existed == null){
            return false
        }

        if (existed.code == code){
            return true
        }

        return false
    }
}

data class code(
    val phone : String,
    val code : Int,
    val time : LocalDateTime
)