import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as conversationsApi from '../api/conversations'

export const useConversationsStore = defineStore('conversations', () => {
  const conversations = ref([])
  const activeConversationId = ref(null)
  const isLoading = ref(false)
  const error = ref(null)

  const activeConversation = computed(() => {
    if (!activeConversationId.value) return null
    return conversations.value.find(c => c.id === activeConversationId.value) || null
  })

  async function fetchConversations() {
    isLoading.value = true
    error.value = null

    try {
      const response = await conversationsApi.getConversations()
      conversations.value = response.data
      return response.data
    } catch (err) {
      error.value = err.response?.data?.message || 'Failed to fetch conversations'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function createDirectConversation(targetUserId) {
    isLoading.value = true
    error.value = null

    try {
      const response = await conversationsApi.createDirectConversation(targetUserId)

      if (!response || !response.data) {
        console.error('Invalid response from server:', response)
        throw new Error('Invalid response from server')
      }

      const newConversation = response.data

      const existing = conversations.value.find(c => c.id === newConversation.id)
      if (!existing) {
        conversations.value.unshift(newConversation)
      }

      return newConversation
    } catch (err) {
      console.error('Failed to create direct conversation:', err)
      error.value = err.response?.data?.message || err.message || 'Failed to create conversation'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function createGroupConversation(name, memberIds, avatarUrl = null) {
    isLoading.value = true
    error.value = null

    try {
      const response = await conversationsApi.createGroupConversation(name, memberIds, avatarUrl)

      if (!response || !response.data) {
        console.error('Invalid response from server:', response)
        throw new Error('Invalid response from server')
      }

      const newConversation = response.data

      conversations.value.unshift(newConversation)
      return newConversation
    } catch (err) {
      console.error('Failed to create group conversation:', err)
      error.value = err.response?.data?.message || err.message || 'Failed to create group'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function markAsRead(conversationId) {
    try {
      await conversationsApi.markConversationAsRead(conversationId)

      const conversation = conversations.value.find(c => c.id === conversationId)
      if (conversation) {
        conversation.unreadCount = 0
      }
    } catch (err) {
      console.error('Failed to mark conversation as read:', err)
      throw err
    }
  }

  async function selectConversation(conversationId) {
    activeConversationId.value = conversationId
    await markAsRead(conversationId)
  }

  function updateConversationUnreadCount(conversationId, unreadCount) {
    const conversation = conversations.value.find(c => c.id === conversationId)
    if (conversation) {
      conversation.unreadCount = unreadCount
    }
  }

  function handleSeenEvent(seenEvent) {
    console.log('Seen event received:', seenEvent)
  }

  function updateConversationLastMessage(conversationId, message) {
    const conversation = conversations.value.find(c => c.id === conversationId)
    if (conversation) {
      conversation.lastMessageAt = message.createdAt || new Date().toISOString()
    }
  }

  return {
    conversations,
    activeConversationId,
    activeConversation,
    isLoading,
    error,
    fetchConversations,
    createDirectConversation,
    createGroupConversation,
    selectConversation,
    updateConversationUnreadCount,
    handleSeenEvent,
    updateConversationLastMessage
  }
})
