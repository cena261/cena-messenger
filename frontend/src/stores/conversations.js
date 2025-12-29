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

  async function createConversation(type, memberUserIds, name = null) {
    isLoading.value = true
    error.value = null

    try {
      const response = await conversationsApi.createConversation(type, memberUserIds, name)
      const newConversation = response.data

      conversations.value.unshift(newConversation)
      return newConversation
    } catch (err) {
      error.value = err.response?.data?.message || 'Failed to create conversation'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function markAsRead(conversationId) {
    try {
      console.log('markAsRead called for conversationId:', conversationId)
      await conversationsApi.markConversationAsRead(conversationId)
      console.log('markAsRead API call successful')

      const conversation = conversations.value.find(c => c.id === conversationId)
      if (conversation) {
        console.log('Updating local unread count from', conversation.unreadCount, 'to 0')
        conversation.unreadCount = 0
      } else {
        console.log('Conversation not found in local store')
      }
    } catch (err) {
      console.error('Failed to mark conversation as read:', err)
      throw err
    }
  }

  function setActiveConversation(conversationId) {
    activeConversationId.value = conversationId
  }

  function updateConversationUnreadCount(conversationId, unreadCount) {
    const conversation = conversations.value.find(c => c.id === conversationId)
    if (conversation) {
      conversation.unreadCount = unreadCount
    }
  }

  function updateConversationLastMessage(conversationId, message) {
    const conversation = conversations.value.find(c => c.id === conversationId)
    if (conversation) {
      conversation.lastMessageContent = message.content
      conversation.lastMessageAt = message.createdAt
    }
  }

  return {
    conversations,
    activeConversationId,
    activeConversation,
    isLoading,
    error,
    fetchConversations,
    createConversation,
    markAsRead,
    setActiveConversation,
    updateConversationUnreadCount,
    updateConversationLastMessage
  }
})
