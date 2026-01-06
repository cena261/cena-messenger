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

export async function sendMessage(conversationId, content, replyTo = null) {
  const response = await apiClient.post('/messages', {
    conversationId,
    content,
    ...(replyTo && { replyTo })
  })
  return response.data
}

export async function editMessage(messageId, content) {
  const response = await apiClient.put(`/messages/${messageId}`, {
    content
  })
  return response.data
}

export async function deleteMessage(messageId) {
  const response = await apiClient.delete(`/messages/${messageId}`)
  return response.data
}

export async function searchMessages(conversationId, query, page = 0, size = 50) {
  const response = await apiClient.get('/search/messages', {
    params: {
      conversationId,
      query,
      page,
      size
    }
  })
  return response.data
}
