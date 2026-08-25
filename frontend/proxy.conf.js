// Dev-server only (ng serve) — mirrors nginx.conf's containerized-build routing.
//
// /patients is both an Angular client-side route (patient-search/-create/-detail,
// app.routes.ts) and the patient-service REST API path (contracts/patient-api.yaml) at the
// exact same URLs (e.g. GET /patients/{id} is both the detail-fetch XHR and a full-page
// reload of that route) — unlike /auth, /accounts, /audit-log, which are pure API paths with
// no colliding Angular route. A `bypass` function is only available in this JS config format
// (not proxy.conf.json's plain JSON), so this file replaces that one. Browsers send
// `Accept: text/html...` on a top-level navigation (typed URL, link click, deep link, page
// reload); Angular's HttpClient does not — so bypassing the proxy (falling through to
// webpack-dev-server's SPA history fallback) whenever that header is present routes page loads
// to the Angular app and everything else to patient-service.
module.exports = {
  '/auth': {
    target: 'http://localhost:8080',
    secure: false,
  },
  '/accounts': {
    target: 'http://localhost:8080',
    secure: false,
  },
  '/audit-log': {
    target: 'http://localhost:8080',
    secure: false,
  },
  '/patients': {
    target: 'http://localhost:8081',
    secure: false,
    bypass: function (req) {
      const accept = req.headers['accept'] || '';
      if (accept.includes('text/html')) {
        return '/index.html';
      }
    },
  },
};
