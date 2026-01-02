import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

const apiClient = axios.create({
  baseURL: BASE_URL,
  withCredentials: true, // Required for HTTP-only refresh token cookies
  headers: {
    'Content-Type': 'application/json'
  }
})

let accessToken = null

export function setAccessToken(token) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

export function clearAccessToken() {
  accessToken = null
}

// Request interceptor to attach access token
apiClient.interceptors.request.use(
  (config) => {
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor for auth failures
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    // Skip refresh for /auth/refresh endpoint to prevent infinite loop
    if (originalRequest.url?.includes('/auth/refresh')) {
      clearAccessToken()
      window.dispatchEvent(new CustomEvent('auth:sessionExpired'))
      return Promise.reject(error)
    }

    // If 401 and not already retried, attempt token refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      try {
        // Try to refresh the token
        const refreshResponse = await axios.post(
          `${BASE_URL}/auth/refresh`,
          {},
          { withCredentials: true }
        )

        const newAccessToken = refreshResponse.data.data.accessToken
        setAccessToken(newAccessToken)

        // Retry original request with new token
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
        return apiClient(originalRequest)
      } catch (refreshError) {
        // Refresh failed, clear token and dispatch event
        clearAccessToken()
        window.dispatchEvent(new CustomEvent('auth:sessionExpired'))
        return Promise.reject(refreshError)
      }
    }

    return Promise.reject(error)
  }
)

export default apiClient
