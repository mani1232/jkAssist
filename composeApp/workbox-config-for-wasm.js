module.exports = {
    globDirectory: "build/dist/wasmJs/productionExecutable/",
    globPatterns: [
        "**/*.{js,wasm,html,css,json}"
    ],
    globIgnores: [
        "serviceWorker.js",
        "registerServiceWorker.js"
    ],
    maximumFileSizeToCacheInBytes: 25 * 1024 * 1024,
    swDest: "build/dist/wasmJs/productionExecutable/serviceWorker.js",
    ignoreURLParametersMatching: [/^v/],

    runtimeCaching: [
        {
            urlPattern: /\.(?:png|jpg|jpeg|svg|gif|webp)$/,
            handler: 'CacheFirst',
            options: {
                cacheName: 'images-cache',
                expiration: {
                    maxEntries: 50,
                    maxAgeSeconds: 30 * 24 * 60 * 60,
                },
            },
        },
        {
            urlPattern: /^https:\/\/hack\.worldmandia\.cc\/.*/i,
            handler: 'NetworkFirst',
            options: {
                cacheName: 'api-cache',
                networkTimeoutSeconds: 3,
            },
        },
        {
            urlPattern: /.+/,
            handler: "StaleWhileRevalidate",
        }
    ]
};