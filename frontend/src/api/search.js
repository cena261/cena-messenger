import apiClient from './client'

export async function searchConversations(query) {
  const response = await apiClient.get('/search/conversations', {
    params: { query }
  })

  const apiResponse = response.data

  if (apiResponse.status === 'error') {
    const error = new Error(apiResponse.message || 'Failed to search conversations')
    error.response = { data: apiResponse }
    throw error
  }

  return apiResponse
}

export async function searchMessages(conversationId, query, page = 0, size = 20) {
  const response = await apiClient.get('/search/messages', {
    params: {
      conversationId,
      query,
      page,
      size
    }
  })

  const apiResponse = response.data

  if (apiResponse.status === 'error') {
    const error = new Error(apiResponse.message || 'Failed to search messages')
    error.response = { data: apiResponse }
    throw error
  }

  return apiResponse
}
