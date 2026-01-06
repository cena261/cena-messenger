import apiClient from './client'
import axios from 'axios'

export async function getCurrentUser() {
  const response = await apiClient.get('/users/me')
  const apiResponse = response.data

  if (apiResponse.status === 'error') {
    const error = new Error(apiResponse.message || 'Failed to get user profile')
    error.response = { data: apiResponse }
    throw error
  }

  return apiResponse
}

export async function requestAvatarPresignedUrl(fileName, fileSize, mimeType) {
  const response = await apiClient.post('/users/me/avatar/presigned-url', {
    fileName,
    fileSize,
    mimeType
  })

  const apiResponse = response.data

  if (apiResponse.status === 'error') {
    const error = new Error(apiResponse.message || 'Failed to get presigned URL')
    error.response = { data: apiResponse }
    throw error
  }

  return apiResponse
}

export async function uploadAvatarToObjectStorage(presignedUrl, file, mimeType) {
  const response = await axios.put(presignedUrl, file, {
    headers: {
      'Content-Type': mimeType
    }
  })
  return response
}

export async function updateProfile(displayName, avatarUrl) {
  const requestBody = {}

  if (displayName !== undefined && displayName !== null) {
    requestBody.displayName = displayName
  }

  if (avatarUrl !== undefined && avatarUrl !== null) {
    requestBody.avatarUrl = avatarUrl
  }

  const response = await apiClient.put('/users/me', requestBody)
  const apiResponse = response.data

  if (apiResponse.status === 'error') {
    const error = new Error(apiResponse.message || 'Failed to update profile')
    error.response = { data: apiResponse }
    throw error
  }

  return apiResponse
}

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
