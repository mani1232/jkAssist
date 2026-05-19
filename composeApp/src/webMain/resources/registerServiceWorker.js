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

window.subscribeToPush = async function(publicKeyBase64) {
    try {
        const registration = await navigator.serviceWorker.ready;
        let subscription = await registration.pushManager.getSubscription();

        if (!subscription) {
            const padding = '='.repeat((4 - publicKeyBase64.length % 4) % 4);
            const base64 = (publicKeyBase64 + padding).replace(/\-/g, '+').replace(/_/g, '/');
            const rawData = window.atob(base64);
            const outputArray = new Uint8Array(rawData.length);
            for (let i = 0; i < rawData.length; ++i) {
                outputArray[i] = rawData.charCodeAt(i);
            }

            subscription = await registration.pushManager.subscribe({
                userVisibleOnly: true,
                applicationServerKey: outputArray
            });
        }

        window.dispatchEvent(new CustomEvent('push-subscription', {
            detail: JSON.stringify(subscription)
        }));

    } catch (e) {
        console.error("Помилка підписки на Push: ", e);
    }
};