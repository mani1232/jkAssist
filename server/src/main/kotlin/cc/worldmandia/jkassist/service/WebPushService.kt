package cc.worldmandia.jkassist.service

import kotlinx.serialization.Serializable

@Serializable
data class PushSubDto(
    val endpoint: String,
    val keys: PushKeys
)

@Serializable
data class PushKeys(
    val p256dh: String,
    val auth: String
)