(() => {
    if ("serviceWorker" in navigator) {
        window.addEventListener("load", () => {
            navigator.serviceWorker.register("serviceWorker.js")
                .then((registration) => {
                    console.log("Service Worker registered!");

                    registration.addEventListener('updatefound', () => {
                        const installingWorker = registration.installing;
                        if (installingWorker == null) return;
                        installingWorker.onstatechange = () => {
                            if (installingWorker.state === 'installed' && navigator.serviceWorker.controller) {
                                window.dispatchEvent(new Event("app-update-available"));
                            }
                        };
                    });
                })
                .catch((error) => console.error("Service Worker registration failed:", error));

            window.addEventListener("apply-app-update", () => {
                if ('caches' in window) {
                    caches.keys().then((names) => {
                        Promise.all(names.map(name => caches.delete(name)))
                            .then(() => window.location.reload());
                    });
                } else {
                    window.location.reload();
                }
            });
        });
    }

    let deferredPrompt;
    window.addEventListener('beforeinstallprompt', (e) => {
        e.preventDefault();
        deferredPrompt = e;
        window.dispatchEvent(new Event("app-install-available"));
    });

    window.addEventListener('prompt-app-install', () => {
        if (deferredPrompt) {
            deferredPrompt.prompt();
            deferredPrompt.userChoice.then((choiceResult) => {
                if (choiceResult.outcome === 'accepted') {
                    console.log('Користувач встановив PWA');
                }
                deferredPrompt = null;
            });
        }
    });

    window.addEventListener('appinstalled', () => {
        window.dispatchEvent(new Event("app-installed-success"));
    });
})();