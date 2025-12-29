import apiClient from './client'

export async function getConversations() {
  const response = await apiClient.get('/conversations')
  return response.data
}

export async function getConversation(conversationId) {
  const response = await apiClient.get(`/conversations/${conversationId}`)
  return response.data
}

export async function createConversation(type, memberUserIds, name = null) {
  const endpoint = type === 'DIRECT' ? '/conversations/direct' : '/conversations/group'
  const payload = type === 'DIRECT'
    ? { targetUserId: memberUserIds[0] }
    : { memberUserIds, name }

  const response = await apiClient.post(endpoint, payload)
  return response.data
}

export async function markConversationAsRead(conversationId) {
  const response = await apiClient.post(`/conversations/${conversationId}/read`)
  return response.data
}
