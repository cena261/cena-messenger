import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as blockingApi from '../api/blocking'

export const useBlockingStore = defineStore('blocking', () => {
  const blockedUsers = ref([])
  const isLoading = ref(false)
  const error = ref(null)

  async function fetchBlockedUsers() {
    isLoading.value = true
    error.value = null

    try {
      const response = await blockingApi.getBlockedUsers()
      blockedUsers.value = response.data || []
      return blockedUsers.value
    } catch (err) {
      error.value = err.response?.data?.message || 'Failed to fetch blocked users'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function blockUser(userId) {
    error.value = null

    try {
      await blockingApi.blockUser(userId)
      await fetchBlockedUsers()
    } catch (err) {
      error.value = err.response?.data?.message || 'Failed to block user'
      throw err
    }
  }

  async function unblockUser(userId) {
    error.value = null

    try {
      await blockingApi.unblockUser(userId)
      blockedUsers.value = blockedUsers.value.filter(u => u.userId !== userId)
    } catch (err) {
      error.value = err.response?.data?.message || 'Failed to unblock user'
      throw err
    }
  }

  function isUserBlocked(userId) {
    return blockedUsers.value.some(u => u.userId === userId)
  }

  return {
    blockedUsers,
    isLoading,
    error,
    fetchBlockedUsers,
    blockUser,
    unblockUser,
    isUserBlocked
  }
})
