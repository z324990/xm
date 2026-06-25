// AI Chat Platform - Service Worker
const CACHE_NAME = 'ai-chat-v1';

// Resources to cache on install
const PRECACHE = [
  '/',
  '/css/style.css',
  '/js/chat.js',
  '/login',
  '/register',
  '/manifest.json'
];

// Install: cache core resources
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => {
      return cache.addAll(PRECACHE);
    }).then(() => self.skipWaiting())
  );
});

// Activate: clean old caches
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => {
      return Promise.all(
        keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))
      );
    }).then(() => self.clients.claim())
  );
});

// Fetch: network first, then cache
self.addEventListener('fetch', event => {
  // Skip non-GET requests
  if (event.request.method !== 'GET') return;

  // For API calls, network only (no cache)
  if (event.request.url.includes('/api/')) {
    event.respondWith(
      fetch(event.request).catch(() => {
        return new Response(JSON.stringify({ code: 500, message: '网络不可用' }), {
          headers: { 'Content-Type': 'application/json' }
        });
      })
    );
    return;
  }

  // For static assets and pages: network first, fallback to cache
  event.respondWith(
    fetch(event.request).then(response => {
      // Cache successful responses
      if (response.ok) {
        const clone = response.clone();
        caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
      }
      return response;
    }).catch(() => {
      return caches.match(event.request).then(cached => {
        return cached || new Response('离线模式', { status: 503 });
      });
    })
  );
});
