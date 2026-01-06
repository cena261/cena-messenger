import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '../api/auth'
import { setAccessToken, clearAccessToken } from '../api/client'
import websocketService from '../services/websocket'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const isAuthenticated = ref(false)
  const isLoading = ref(false)
  const error = ref(null)

  async function login(username, password) {
    isLoading.value = true
    error.value = null

    try {
      const response = await authApi.login(username, password)
      const { accessToken, user: userData } = response.data

      setAccessToken(accessToken)
      user.value = userData
      isAuthenticated.value = true

      websocketService.connect()

      return userData
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'Tài khoản hoặc mật khẩu không đúng'
      // Override backend error messages with Vietnamese
      if (errorMsg === 'Invalid username or password' || errorMsg === 'Authentication failed') {
        error.value = 'Tài khoản hoặc mật khẩu không đúng'
      } else {
        error.value = errorMsg
      }
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function register(username, password, email, displayName, phone) {
    isLoading.value = true
    error.value = null

    try {
      const response = await authApi.register(username, password, email, displayName, phone)
      const { accessToken, user: userData } = response.data

      setAccessToken(accessToken)
      user.value = userData
      isAuthenticated.value = true

      websocketService.connect()

      return userData
    } catch (err) {
      error.value = err.response?.data?.message || 'Đăng ký thất bại'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function logout() {
    isLoading.value = true

    try {
      await authApi.logout()
    } catch (err) {
      console.error('Logout error:', err)
    } finally {
      clearAccessToken()
      user.value = null
      isAuthenticated.value = false
      websocketService.disconnect()
      isLoading.value = false
    }
  }

  async function fetchCurrentUser() {
    isLoading.value = true
    error.value = null

    try {
      const response = await authApi.getCurrentUser()
      user.value = response.data
      isAuthenticated.value = true
      return response.data
    } catch (err) {
      error.value = err.response?.data?.message || 'Không thể tải thông tin người dùng'
      clearAccessToken()
      user.value = null
      isAuthenticated.value = false
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function restoreSession() {
    isLoading.value = true
    error.value = null

    try {
      const refreshResponse = await authApi.refresh()
      const { accessToken } = refreshResponse.data

      setAccessToken(accessToken)

      const userResponse = await authApi.getCurrentUser()
      user.value = userResponse.data
      isAuthenticated.value = true

      websocketService.connect()

      return userResponse.data
    } catch (err) {
      clearAccessToken()
      user.value = null
      isAuthenticated.value = false
      throw err
    } finally {
      isLoading.value = false
    }
  }

  function handleSessionExpired() {
    clearAccessToken()
    user.value = null
    isAuthenticated.value = false
    websocketService.disconnect()
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('auth:sessionExpired', handleSessionExpired)
  }

  return {
    user,
    isAuthenticated,
    isLoading,
    error,
    login,
    register,
    logout,
    fetchCurrentUser,
    restoreSession
  }
})
