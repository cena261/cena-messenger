import apiClient from './client'

export async function getConversations() {
  const response = await apiClient.get('/conversations')
  return response.data
}

export async function getConversation(conversationId) {
  const response = await apiClient.get(`/conversations/${conversationId}`)
  return response.data
}

export async function createDirectConversation(targetUserId) {
  const response = await apiClient.post('/conversations/direct', {
    targetUserId
  })
  const apiResponse = response.data

  if (apiResponse.status === 'error') {
    const error = new Error(apiResponse.message || 'Failed to create conversation')
    error.response = { data: apiResponse }
    throw error
  }

  return apiResponse
}

export async function createGroupConversation(name, memberIds, avatarUrl = null) {
  const payload = { name, memberIds }
  if (avatarUrl) {
    payload.avatarUrl = avatarUrl
  }
  const response = await apiClient.post('/conversations/group', payload)
  const apiResponse = response.data

  if (apiResponse.status === 'error') {
    const error = new Error(apiResponse.message || 'Failed to create group conversation')
    error.response = { data: apiResponse }
    throw error
  }

  return apiResponse
}

export async function markConversationAsRead(conversationId) {
  const response = await apiClient.post(`/conversations/${conversationId}/read`)
  return response.data
}
