import apiClient from './client'

export async function leaveGroup(conversationId) {
  const response = await apiClient.post('/groups/leave', {
    conversationId
  })
  return response.data
}

export async function kickMember(conversationId, userId) {
  const response = await apiClient.post('/groups/kick', {
    conversationId,
    userId
  })
  return response.data
}

export async function changeRole(conversationId, userId, newRole) {
  const response = await apiClient.put('/groups/role', {
    conversationId,
    userId,
    newRole
  })
  return response.data
}

export async function transferOwnership(conversationId, newOwnerId) {
  const response = await apiClient.post('/groups/transfer-ownership', {
    conversationId,
    newOwnerId
  })
  return response.data
}

export async function updateGroupInfo(conversationId, name, avatarUrl) {
  const response = await apiClient.put('/groups/info', {
    conversationId,
    name,
    avatarUrl
  })
  return response.data
}
