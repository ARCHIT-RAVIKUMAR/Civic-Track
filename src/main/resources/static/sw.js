// CivicTrack service worker: makes the site installable and shows a friendly page when offline.
// It deliberately does NOT cache dynamic pages, so private complaint data is never stored on the device.
var CACHE = 'civictrack-offline-v1';
var OFFLINE_URL = '/offline.html';

self.addEventListener('install', function (event) {
    event.waitUntil(caches.open(CACHE).then(function (cache) {
        return cache.addAll([OFFLINE_URL, '/icons/icon-192.png']);
    }));
    self.skipWaiting();
});

self.addEventListener('activate', function (event) {
    event.waitUntil(caches.keys().then(function (keys) {
        return Promise.all(keys.filter(function (k) { return k !== CACHE; })
            .map(function (k) { return caches.delete(k); }));
    }));
    self.clients.claim();
});

self.addEventListener('fetch', function (event) {
    if (event.request.mode === 'navigate') {
        event.respondWith(fetch(event.request).catch(function () { return caches.match(OFFLINE_URL); }));
    }
});
