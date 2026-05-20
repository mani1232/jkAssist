package cc.worldmandia.jkassist.storage

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

actual class SessionStorage {
    actual fun getSessionId(): String? {
        return localStorage["chat_session_id"]
    }

    actual fun saveSessionId(id: String) {
        localStorage["chat_session_id"] = id
    }
}