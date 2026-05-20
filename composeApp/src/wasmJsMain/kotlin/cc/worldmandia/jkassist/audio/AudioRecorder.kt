package cc.worldmandia.jkassist.audio

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""
function(onSuccess, onError) {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        onError("Ваш браузер не підтримує запис аудіо");
        return;
    }
    navigator.mediaDevices.getUserMedia({ audio: true })
        .then(stream => {
            const mediaRecorder = new MediaRecorder(stream);
            const audioChunks = [];
            
            mediaRecorder.ondataavailable = e => {
                if (e.data.size > 0) audioChunks.push(e.data);
            };
            
            mediaRecorder.onstop = () => {
                const mimeType = mediaRecorder.mimeType || 'audio/webm';
                const blob = new Blob(audioChunks, { type: mimeType });
                
                const reader = new FileReader();
                reader.onloadend = () => {
                    onSuccess(reader.result);
                };
                reader.readAsDataURL(blob);
                
                stream.getTracks().forEach(track => track.stop());
            };
            
            mediaRecorder.start();
            window.currentMediaRecorder = mediaRecorder;
        })
        .catch(err => {
            onError(err.toString());
        });
}
""")
external fun startAudioRecordingJS(onSuccess: (String) -> Unit, onError: (String) -> Unit)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""
function() {
    if (window.currentMediaRecorder && window.currentMediaRecorder.state === "recording") {
        window.currentMediaRecorder.stop();
    }
}
""")
external fun stopAudioRecordingJS()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""
function(base64Audio, onEnded) {
    if (window.currentAudio) {
        window.currentAudio.pause();
        window.currentAudio.currentTime = 0;
        if (window.currentAudioOnEnded) {
            window.currentAudioOnEnded();
        }
    }
    
    try {
        var audio = new Audio(base64Audio);
        window.currentAudio = audio;
        window.currentAudioOnEnded = onEnded;
        
        audio.onended = function() {
            if (window.currentAudioOnEnded) window.currentAudioOnEnded();
            window.currentAudio = null;
            window.currentAudioOnEnded = null;
        };
        
        var playPromise = audio.play();
        if (playPromise !== undefined) {
            playPromise.catch(e => {
                console.error("Помилка відтворення аудіо:", e);
                if (window.currentAudioOnEnded) window.currentAudioOnEnded();
                window.currentAudio = null;
                window.currentAudioOnEnded = null;
            });
        }
    } catch (e) {
        console.error("Помилка ініціалізації аудіо:", e);
        onEnded();
    }
}
""")
external fun playAudioJS(base64Audio: String, onEnded: () -> Unit)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""
function() {
    if (window.currentAudio) {
        window.currentAudio.pause();
        window.currentAudio.currentTime = 0;
        if (window.currentAudioOnEnded) {
            window.currentAudioOnEnded();
        }
        window.currentAudio = null;
        window.currentAudioOnEnded = null;
    }
}
""")
external fun stopAudioJS()