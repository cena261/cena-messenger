import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as messagesApi from '../api/messages'
import * as mediaApi from '../api/media'
import * as reactionsApi from '../api/reactions'

export const useMessagesStore = defineStore('messages', () => {
  // Messages grouped by conversation ID
  const messagesByConversation = ref({})
  const isLoading = ref(false)
  const error = ref(null)

  async function fetchMessages(conversationId, page = 0, size = 50) {
    isLoading.value = true
    error.value = null

    try {
      const response = await messagesApi.getMessages(conversationId, page, size)
      const messages = response.data

      console.log('Fetched messages:', messages)

      if (!messagesByConversation.value[conversationId]) {
        messagesByConversation.value[conversationId] = []
      }

      messagesByConversation.value[conversationId] = messages.reverse()

      return messages
    } catch (err) {
      error.value = err.response?.data?.message || 'Failed to fetch messages'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function sendMessage(conversationId, content, replyTo = null) {
    try {
      const response = await messagesApi.sendMessage(conversationId, content, replyTo)
      const newMessage = response.data

      addMessage(conversationId, newMessage)

      return newMessage
    } catch (err) {
      console.error('Failed to send message:', err)
      throw err
    }
  }

  async function sendMediaMessage(conversationId, file, mediaType, replyTo = null) {
    try {
      const presignedResponse = await mediaApi.requestPresignedUrl(
        conversationId,
        file.name,
        file.size,
        file.type
      )

      const { presignedUrl, fileKey } = presignedResponse.data

      await mediaApi.uploadToObjectStorage(presignedUrl, file, file.type)

      const mediaMetadata = {
        fileName: file.name,
        fileSize: file.size,
        mimeType: file.type
      }

      if (file.type.startsWith('image/')) {
        const img = await createImageBitmap(file)
        mediaMetadata.width = img.width
        mediaMetadata.height = img.height
      }

      const response = await mediaApi.createMediaMessage(
        conversationId,
        fileKey,
        mediaType,
        mediaMetadata,
        replyTo
      )

      const newMessage = response.data
      addMessage(conversationId, newMessage)

      return newMessage
    } catch (err) {
      console.error('Failed to send media message:', err)
      throw err
    }
  }

  async function editMessage(messageId, conversationId, content) {
    try {
      const response = await messagesApi.editMessage(messageId, content)
      const updateData = response.data

      updateMessageInStore(conversationId, updateData.messageId, {
        content: updateData.content,
        updatedAt: updateData.updatedAt
      })

      return updateData
    } catch (err) {
      console.error('Failed to edit message:', err)
      throw err
    }
  }

  async function deleteMessage(messageId, conversationId) {
    try {
      const response = await messagesApi.deleteMessage(messageId)
      const updateData = response.data

      updateMessageInStore(conversationId, updateData.messageId, {
        content: null,
        isDeleted: true,
        updatedAt: updateData.updatedAt
      })

      return updateData
    } catch (err) {
      console.error('Failed to delete message:', err)
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
      messagesByConversation.value[conversationId] = [
        ...messagesByConversation.value[conversationId],
        message
      ]
    }
  }

  function updateMessageInStore(conversationId, messageId, updates) {
    const messages = messagesByConversation.value[conversationId]
    if (!messages) return

    const messageIndex = messages.findIndex(m => m.id === messageId)
    if (messageIndex !== -1) {
      messages[messageIndex] = {
        ...messages[messageIndex],
        ...updates
      }
    }
  }

  async function toggleReaction(conversationId, messageId, reactionType) {
    try {
      const response = await reactionsApi.toggleReaction(messageId, reactionType)
      const reactionData = response.data

      updateMessageInStore(conversationId, messageId, {
        reactions: reactionData.allReactions || {}
      })

      return reactionData
    } catch (err) {
      console.error('Failed to toggle reaction:', err)
      throw err
    }
  }

  function handleMessageUpdate(updateData) {
    console.log('handleMessageUpdate called with:', updateData)
    const { conversationId, messageId, action, content, updatedAt, isDeleted, deleted } = updateData

    if (action === 'EDIT' || action === 'EDITED') {
      updateMessageInStore(conversationId, messageId, {
        content,
        updatedAt
      })
    } else if (action === 'DELETE' || action === 'DELETED') {
      updateMessageInStore(conversationId, messageId, {
        content: null,
        isDeleted: isDeleted !== undefined ? isDeleted : deleted,
        deleted: deleted !== undefined ? deleted : isDeleted,
        updatedAt
      })
    }
  }

  function handleReactionUpdate(reactionData) {
    const { conversationId, messageId, allReactions } = reactionData

    updateMessageInStore(conversationId, messageId, {
      reactions: allReactions || {}
    })
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
    sendMediaMessage,
    editMessage,
    deleteMessage,
    addMessage,
    toggleReaction,
    getMessages,
    clearMessages,
    handleMessageUpdate,
    handleReactionUpdate
  }
})
