import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as messagesApi from '../api/messages'

export const useMessagesStore = defineStore('messages', () => {
  // Messages grouped by conversation ID
  const messagesByConversation = ref({})
  const isLoading = ref(false)
  const error = ref(null)

  async function fetchMessages(conversationId, page = 0, size = 50) {
    isLoading.value = true
    error.value = null

    try {
      console.log('Fetching messages for conversation:', conversationId)
      const response = await messagesApi.getMessages(conversationId, page, size)
      const messages = response.data
      console.log('Fetched messages:', messages)

      // Store messages for this conversation
      if (!messagesByConversation.value[conversationId]) {
        messagesByConversation.value[conversationId] = []
      }

      // Messages come in reverse chronological order, reverse them for display
      messagesByConversation.value[conversationId] = messages.reverse()
      console.log('Messages stored for conversation:', conversationId, messagesByConversation.value[conversationId])

      return messages
    } catch (err) {
      console.error('Error fetching messages:', err)
      error.value = err.response?.data?.message || 'Failed to fetch messages'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function sendMessage(conversationId, content) {
    try {
      const response = await messagesApi.sendMessage(conversationId, content)
      const newMessage = response.data

      return newMessage
    } catch (err) {
      console.error('Failed to send message:', err)
      throw err
    }
  }

  function addMessage(conversationId, message) {
    if (!messagesByConversation.value[conversationId]) {
      messagesByConversation.value[conversationId] = []
    }

    const exists = messagesByConversation.value[conversationId].some(
      m => m.id === message.id
    )

    if (!exists) {
      console.log('Adding new message to conversation', conversationId, 'messageId:', message.id)
      messagesByConversation.value[conversationId] = [
        ...messagesByConversation.value[conversationId],
        message
      ]
      console.log('Updated message count:', messagesByConversation.value[conversationId].length)
    } else {
      console.log('Message already exists, skipping:', message.id)
    }
  }

  function getMessages(conversationId) {
    return messagesByConversation.value[conversationId] || []
  }

  function clearMessages(conversationId) {
    if (conversationId) {
      delete messagesByConversation.value[conversationId]
    } else {
      messagesByConversation.value = {}
    }
  }

  return {
    messagesByConversation,
    isLoading,
    error,
    fetchMessages,
    sendMessage,
    addMessage,
    getMessages,
    clearMessages
  }
})
