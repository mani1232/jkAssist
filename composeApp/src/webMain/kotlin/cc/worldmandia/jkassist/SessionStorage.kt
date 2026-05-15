package cc.worldmandia.jkassist

interface SessionStorage {
    fun getSessionId(): String?
    fun saveSessionId(id: String)
}