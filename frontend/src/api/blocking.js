import apiClient from './client'

export async function blockUser(userId) {
  const response = await apiClient.post('/blocking/block', {
    userId
  })
  return response.data
}

export async function unblockUser(userId) {
  const response = await apiClient.post('/blocking/unblock', {
    userId
  })
  return response.data
}

export async function getBlockedUsers() {
  const response = await apiClient.get('/blocking/blocked-users')
  return response.data
}
