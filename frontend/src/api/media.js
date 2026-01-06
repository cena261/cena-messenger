import apiClient from './client'
import axios from 'axios'

export async function requestPresignedUrl(conversationId, fileName, fileSize, mimeType) {
  const response = await apiClient.post('/media/presigned-url', {
    conversationId,
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

export async function uploadToObjectStorage(presignedUrl, file, mimeType) {
  const response = await axios.put(presignedUrl, file, {
    headers: {
      'Content-Type': mimeType
    }
  })
  return response
}

export async function createMediaMessage(conversationId, fileKey, type, mediaMetadata, replyTo = null) {
  const response = await apiClient.post('/media/messages', {
    conversationId,
    fileKey,
    type,
    mediaMetadata,
    replyTo
  })

  const apiResponse = response.data

  if (apiResponse.status === 'error') {
    const error = new Error(apiResponse.message || 'Failed to create media message')
    error.response = { data: apiResponse }
    throw error
  }

  return apiResponse
}

export async function getPresignedUrl(conversationId, fileName, fileSize, mimeType) {
  const response = await requestPresignedUrl(conversationId, fileName, fileSize, mimeType)
  return response.data
}
