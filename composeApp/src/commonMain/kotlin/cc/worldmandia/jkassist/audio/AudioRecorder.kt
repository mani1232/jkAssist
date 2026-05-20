package cc.worldmandia.jkassist.audio

expect fun startAudioRecording(onSuccess: (String) -> Unit, onError: (String) -> Unit)
expect fun stopAudioRecording()
expect fun playAudio(base64Audio: String, onEnded: () -> Unit)
expect fun stopAudio()
