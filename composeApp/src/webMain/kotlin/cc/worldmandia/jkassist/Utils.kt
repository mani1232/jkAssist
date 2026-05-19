package cc.worldmandia.jkassist

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("function(publicKey) { window.subscribeToPush(publicKey); }")
external fun subscribeToPushJS(publicKey: String)