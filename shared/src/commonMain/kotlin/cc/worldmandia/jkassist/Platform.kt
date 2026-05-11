package cc.worldmandia.jkassist

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform