package cc.worldmandia.jkassist.storage

expect class SessionStorage() {
    fun getSessionId(): String?
    fun saveSessionId(id: String)
}