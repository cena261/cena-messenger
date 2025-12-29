import apiClient from './client'

export async function getMessages(conversationId, page = 0, size = 50) {
  const response = await apiClient.get('/messages', {
    params: {
      conversationId,
      page,
      size
    }
  })
  return response.data
}

export async function sendMessage(conversationId, content) {
  const response = await apiClient.post('/messages', {
    conversationId,
    content
  })
  return response.data
}
