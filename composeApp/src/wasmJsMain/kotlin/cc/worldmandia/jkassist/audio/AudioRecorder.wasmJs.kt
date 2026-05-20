package cc.worldmandia.jkassist.audio

actual fun startAudioRecording(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
    startAudioRecordingJS(onSuccess, onError)
}

actual fun stopAudioRecording() {
    stopAudioRecordingJS()
}

actual fun playAudio(base64Audio: String, onEnded: () -> Unit) {
    playAudioJS(base64Audio, onEnded)
}

actual fun stopAudio() {
    stopAudioJS()
}