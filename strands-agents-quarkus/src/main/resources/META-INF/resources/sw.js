const CACHE = 'strands-v1';
const OFFLINE_HTML = '<!DOCTYPE html><html lang="de"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0"><title>Offline</title><style>body{font-family:-apple-system,sans-serif;background:#1a1a2e;color:#e0e0e0;display:flex;height:100dvh;align-items:center;justify-content:center;text-align:center;padding:20px}h1{color:#e94560}p{color:#888}</style></head><body><div><h1>📡 Keine Verbindung</h1><p>Strands Agent ben&ouml;tigt eine Internetverbindung.<br>Bitte versuche es sp&auml;ter erneut.</p></div></body></html>';

self.addEventListener('install', function(e) {
  e.waitUntil(
    caches.open(CACHE).then(function(cache) {
      return cache.addAll(['/']);
    })
  );
});

self.addEventListener('fetch', function(e) {
  e.respondWith(
    fetch(e.request).catch(function() {
      if (e.request.mode === 'navigate') {
        return new Response(OFFLINE_HTML, {
          headers: { 'Content-Type': 'text/html; charset=utf-8' }
        });
      }
      return caches.match(e.request);
    })
  );
});
