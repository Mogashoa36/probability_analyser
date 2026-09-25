// Relative so the dev server can proxy /api to the backend (see proxy.conf.json).
// This matches environment.prod.ts and keeps the browser on a single origin, so
// the backend's CORS config never comes into play during development.
export const environment = {
  production: false,
  apiBaseUrl: '/api',
};
