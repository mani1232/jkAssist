package cc.worldmandia.jkassist.service

import cc.worldmandia.jkassist.MockDatabase
import cc.worldmandia.jkassist.jsonFormat
import com.interaso.webpush.WebPushService
import com.interaso.webpush.VapidKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class WebPushService(private val privateKey: String) {
    private val logger = LoggerFactory.getLogger("JkWebPushService")

    private val publicKey = "BGPrMtUDx7_ZC7nYCyInlei_0gDhutY3dsjTCbeWs8by9SuDnvgQgk3Ry1PLB-71g_VyRzg-lkuLdtKmiYFg3S0"


    private val pushService = WebPushService(
        subject = "mailto:support@worldmandia.cc",
        vapidKeys = VapidKeys.fromUncompressedBytes(
            publicKey = publicKey,
            privateKey = privateKey,
        )
    )

    suspend fun sendPushNotification(userId: String, title: String, body: String) = withContext(Dispatchers.IO) {
        val subJsonString = MockDatabase.pushSubscriptions[userId] ?: return@withContext

        try {
            val subDto = jsonFormat.decodeFromString<PushSubDto>(subJsonString)

            val payload = PushMessagePayload(title, body)

            pushService.send(
                payload = jsonFormat.encodeToString(payload),
                endpoint = subDto.endpoint,
                p256dh = subDto.keys.p256dh,
                auth = subDto.keys.auth
            )

            logger.info("✅ Push успішно відправлено для $userId")
        } catch (e: Exception) {
            logger.error("❌ Помилка при відправці Push: ${e.message}", e)
        }
    }
}