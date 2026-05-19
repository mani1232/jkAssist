self.addEventListener('push', function(event) {
    const data = event.data ? event.data.json() : {};

    const title = data.title || 'AIHome';
    const options = {
        body: data.body || 'Вам відповів оператор',
        icon: 'icons/icon-192x192.png',
        badge: 'icons/icon-72x72.png',
        data: { url: data.url || './' },
        vibrate: [200, 100, 200]
    };

    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function(clientList) {
            let isAppVisibleAndFocused = false;

            for (let i = 0; i < clientList.length; i++) {
                let client = clientList[i];
                if (client.visibilityState === 'visible' && client.focused) {
                    isAppVisibleAndFocused = true;
                    break;
                }
            }

            if (!isAppVisibleAndFocused) {
                return self.registration.showNotification(title, options);
            }
        })
    );
});

self.addEventListener('notificationclick', function(event) {
    event.notification.close();

    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function(clientList) {
            for (let i = 0; i < clientList.length; i++) {
                let client = clientList[i];
                if (client.url.startsWith(self.location.origin) && 'focus' in client) {
                    return client.focus();
                }
            }
            if (clients.openWindow) {
                return clients.openWindow(event.notification.data.url);
            }
        })
    );
});

self.addEventListener('pushsubscriptionchange', function(event) {
    console.log('[Service Worker]: Подписка на Push изменена/истекла.');

    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function(clientList) {
            for (let i = 0; i < clientList.length; i++) {
                let client = clientList[i];
                client.postMessage({ type: 'UPDATE_PUSH_SUBSCRIPTION' });
            }
        })
    );
});