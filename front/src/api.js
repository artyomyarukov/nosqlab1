const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

async function request(path, options = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...options.headers,
    },
  })

  const contentType = response.headers.get('content-type') || ''
  const data = contentType.includes('application/json')
    ? await response.json()
    : await response.text()

  if (!response.ok) {
    const error = new Error(
      typeof data === 'object' && data?.message
        ? data.message
        : `Ошибка запроса (${response.status})`,
    )
    error.status = response.status
    throw error
  }

  return data
}

function authHeaders(token) {
  return token ? { 'X-Session-Token': token } : {}
}

export const api = {
  login: (username) =>
    request('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username }),
    }),

  session: (token) =>
    request('/api/auth/session', { headers: authHeaders(token) }),

  logout: (token) =>
    request('/api/auth/logout', {
      method: 'POST',
      headers: authHeaders(token),
    }),

  users: () => request('/api/users'),

  profile: (id) => request(`/api/users/${id}`),

  updateProfile: (id, payload, token) =>
    request(`/api/users/${id}`, {
      method: 'PUT',
      headers: authHeaders(token),
      body: JSON.stringify(payload),
    }),

  notify: (id, payload, token) =>
    request(`/api/users/${id}/notify`, {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify(payload),
    }),

  history: (id) => request(`/api/users/${id}/history`),
}

export { API_URL }
