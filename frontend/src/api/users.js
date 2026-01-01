import apiClient from './client'

export async function searchUser(query) {
  const response = await apiClient.get('/users/search', {
    params: { query }
  })
  const apiResponse = response.data

  if (apiResponse.status === 'error') {
    const error = new Error(apiResponse.message || 'Failed to search user')
    error.response = { data: apiResponse }
    throw error
  }

  return apiResponse
}
