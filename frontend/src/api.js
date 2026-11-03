const TOKEN_KEY = 'fintrack.token';

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // storage unavailable (private mode); the session just won't survive a reload
  }
}

/**
 * Calls the backend. Pass `body` for JSON or `form` for a FormData upload.
 * Throws an Error carrying the server's message when the request fails.
 */
export async function api(path, { method = 'GET', body, form } = {}) {
  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  let payload;
  if (form) {
    payload = form;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  const res = await fetch(`/api${path}`, { method, headers, body: payload });
  // An expired token, or an account that was deleted or locked while signed in: sign out.
  if (token && (res.status === 401 || res.status === 423)) {
    setToken(null);
    window.dispatchEvent(new Event('fintrack:signed-out'));
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    let code;
    try {
      const data = await res.json();
      if (data?.message) message = data.message;
      code = data?.code;
    } catch {
      // no JSON body
    }
    const error = new Error(message);
    error.code = code;
    throw error;
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}
