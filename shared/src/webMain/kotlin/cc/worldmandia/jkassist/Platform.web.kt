package cc.worldmandia.jkassist

class WebPlatform : Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WebPlatform()