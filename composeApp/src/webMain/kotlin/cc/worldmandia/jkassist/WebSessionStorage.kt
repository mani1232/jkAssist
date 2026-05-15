package cc.worldmandia.jkassist

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

class WebSessionStorage : SessionStorage {

    override fun getSessionId(): String? {
        return localStorage["chat_session_id"]
    }

    override fun saveSessionId(id: String) {
        localStorage["chat_session_id"] = id
    }
}