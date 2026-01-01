import apiClient from './client'

export async function login(username, password) {
  const response = await apiClient.post('/auth/login', {
    username,
    password
  })
  return response.data
}

export async function register(username, password, email, displayName, phone) {
  const response = await apiClient.post('/auth/register', {
    username,
    password,
    email,
    displayName,
    phone
  })
  return response.data
}

export async function refresh() {
  const response = await apiClient.post('/auth/refresh', {}, {
    withCredentials: true
  })
  return response.data
}

export async function logout() {
  const response = await apiClient.post('/auth/logout')
  return response.data
}

export async function getCurrentUser() {
  const response = await apiClient.get('/users/me')
  return response.data
}

export async function requestPasswordReset(email) {
  const response = await apiClient.post('/auth/forgot-password', {
    email
  })
  return response.data
}

export async function resetPassword(email, code, newPassword) {
  const response = await apiClient.post('/auth/reset-password', {
    email,
    code,
    newPassword
  })
  return response.data
}
