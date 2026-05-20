package cc.worldmandia.jkassist.push

actual fun subscribeToPush(publicKey: String) {
    subscribeToPushJS(publicKey)
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("function(publicKey) { window.subscribeToPush(publicKey); }")
external fun subscribeToPushJS(publicKey: String)