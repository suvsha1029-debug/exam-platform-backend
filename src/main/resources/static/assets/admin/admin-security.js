(function () {
  'use strict';
  const originalFetch = window.fetch.bind(window);
  const protectedPrefixes = [
    '/api/master-admin/',
    '/api/exams',
    '/api/students',
    '/api/notifications',
    '/api/responses',
    '/api/evaluation',
    '/api/exam-access'
  ];
  let csrfToken = '';
  let csrfPromise = null;

  function needsToken(url, method) {
    if (!['POST', 'PATCH', 'PUT', 'DELETE'].includes(method)) return false;
    const path = new URL(url, window.location.origin).pathname;
    if (path === '/api/students/register' || path === '/api/students/register-with-files') return false;
    return protectedPrefixes.some((prefix) => path.startsWith(prefix));
  }

  async function loadToken() {
    if (csrfToken) return csrfToken;
    if (!csrfPromise) {
      csrfPromise = originalFetch('/api/admin/csrf-token', {
        credentials: 'same-origin',
        cache: 'no-store',
        headers: { Accept: 'application/json' }
      }).then(async (response) => {
        if (!response.ok) throw new Error('Admin security session is not available.');
        const data = await response.json();
        csrfToken = String(data.token || '');
        if (!csrfToken) throw new Error('Admin security token was not issued.');
        return csrfToken;
      }).finally(() => { csrfPromise = null; });
    }
    return csrfPromise;
  }

  window.fetch = async function secureFetch(input, init) {
    const options = { ...(init || {}) };
    const url = typeof input === 'string' ? input : input.url;
    const method = String(options.method || (typeof input === 'object' && input.method) || 'GET').toUpperCase();
    if (needsToken(url, method)) {
      const headers = new Headers(options.headers || (typeof input === 'object' ? input.headers : undefined) || {});
      headers.set('X-Admin-CSRF', await loadToken());
      options.headers = headers;
      options.credentials = 'same-origin';
      options.cache = 'no-store';
    }
    return originalFetch(input, options);
  };

  window.refreshAdminSecurityToken = function () {
    csrfToken = '';
    return loadToken();
  };
})();
