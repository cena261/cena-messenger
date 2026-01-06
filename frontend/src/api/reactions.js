import apiClient from './client'

export async function toggleReaction(messageId, reactionType) {
  const response = await apiClient.post('/messages/reactions', {
    messageId,
    reactionType
  })

  const apiResponse = response.data

  if (apiResponse.status === 'error') {
    const error = new Error(apiResponse.message || 'Failed to toggle reaction')
    error.response = { data: apiResponse }
    throw error
  }

  return apiResponse
}
