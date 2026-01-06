import { defineStore } from 'pinia'
import { ref } from 'vue'
import websocketService from '../services/websocket'
import { useConversationsStore } from './conversations'
import { useMessagesStore } from './messages'
import { useAuthStore } from './auth'

export const useRealtimeStore = defineStore('realtime', () => {
  const typingUsers = ref({})
  const seenReceipts = ref({})
  const subscriptions = ref([])

  function initializeSubscriptions() {
    const authStore = useAuthStore()
    const conversationsStore = useConversationsStore()
    const messagesStore = useMessagesStore()

    if (!authStore.user) {
      console.warn('Cannot initialize subscriptions: user not authenticated')
      return
    }

    const userId = authStore.user.id

    // Subscribe to unread count updates
    websocketService.subscribe(`/user/queue/unread`, async (data) => {
      console.log('Unread update received:', data)

      const conversation = conversationsStore.conversations.find(
        c => c.id === data.conversationId
      )

      if (!conversation) {
        console.log('Conversation not found in list, refreshing...')
        await conversationsStore.fetchConversations()
      } else {
        conversationsStore.updateConversationUnreadCount(
          data.conversationId,
          data.unreadCount
        )
      }
    })

    websocketService.subscribe(`/user/queue/seen`, (data) => {
      console.log('Seen event received:', data)
      handleSeenEvent(data)
    })

    websocketService.subscribe(`/user/queue/typing`, (data) => {
      console.log('Typing event received:', data)
      handleTypingEvent(data)
    })

    websocketService.subscribe(`/user/queue/message-updates`, (data) => {
      console.log('Message update event received:', data)
      messagesStore.handleMessageUpdate(data)
    })

    websocketService.subscribe(`/user/queue/group-events`, async (data) => {
      console.log('Group event received:', data)
      await handleGroupEvent(data)
    })

    websocketService.subscribe(`/user/queue/reactions`, (data) => {
      console.log('Reaction event received:', data)
      messagesStore.handleReactionUpdate(data)
    })
  }

  async function handleGroupEvent(data) {
    const conversationsStore = useConversationsStore()

    console.log('Handling group event:', data.eventType, 'for conversation:', data.conversationId)

    await conversationsStore.fetchConversations()
  }

  function subscribeToConversation(conversationId) {
    const messagesStore = useMessagesStore()
    const destination = `/topic/conversation.${conversationId}`

    console.log('subscribeToConversation called for:', conversationId)

    const existingSubscription = subscriptions.value.find(s => s.conversationId === conversationId)
    if (existingSubscription) {
      console.log('Already subscribed to conversation:', conversationId)
      return
    }

    const unsubscribeFn = websocketService.subscribe(destination, async (message) => {
      console.log('Message received via WebSocket:', message)
      messagesStore.addMessage(conversationId, message)

      const conversationsStore = useConversationsStore()
      conversationsStore.updateConversationLastMessage(conversationId, message)

      const authStore = useAuthStore()
      if (message.senderId !== authStore.user?.id && conversationsStore.activeConversationId === conversationId) {
        try {
          const conversationsApi = await import('../api/conversations')
          await conversationsApi.markConversationAsRead(conversationId)
          console.log('Auto-marked conversation as read:', conversationId)
        } catch (error) {
          console.error('Failed to auto-mark conversation as read:', error)
        }
      }
    })

    subscriptions.value.push({ conversationId, destination, unsubscribe: unsubscribeFn })
    console.log('Subscribed to conversation:', conversationId, 'Total subscriptions:', subscriptions.value.length)
  }

  function unsubscribeFromConversation(conversationId) {
    const destination = `/topic/conversation.${conversationId}`
    console.log('unsubscribeFromConversation called for:', conversationId)

    websocketService.unsubscribe(destination)

    const index = subscriptions.value.findIndex(s => s.conversationId === conversationId)
    if (index !== -1) {
      subscriptions.value.splice(index, 1)
      console.log('Unsubscribed from conversation:', conversationId, 'Remaining subscriptions:', subscriptions.value.length)
    }
  }

  function sendTypingStart(conversationId) {
    websocketService.send('/app/typing/start', { conversationId })
  }

  function sendTypingStop(conversationId) {
    websocketService.send('/app/typing/stop', { conversationId })
  }

  function handleTypingEvent(data) {
    const authStore = useAuthStore()
    const { conversationId, userId, isTyping, typing } = data

    const typingStatus = isTyping !== undefined ? isTyping : typing

    if (userId === authStore.user?.id) {
      return
    }

    if (!typingUsers.value[conversationId]) {
      typingUsers.value[conversationId] = []
    }

    if (typingStatus) {
      if (!typingUsers.value[conversationId].includes(userId)) {
        typingUsers.value[conversationId] = [...typingUsers.value[conversationId], userId]
      }
    } else {
      typingUsers.value[conversationId] = typingUsers.value[conversationId].filter(id => id !== userId)
    }
  }

  function handleSeenEvent(data) {
    const { conversationId, userId, lastReadMessageId } = data

    if (!seenReceipts.value[conversationId]) {
      seenReceipts.value[conversationId] = {}
    }

    seenReceipts.value[conversationId][userId] = lastReadMessageId
  }

  function getTypingUsers(conversationId) {
    return typingUsers.value[conversationId] || []
  }

  function getTypingUsersDisplay(conversationId) {
    const conversationsStore = useConversationsStore()
    const conversation = conversationsStore.conversations.find(c => c.id === conversationId)

    if (!conversation) {
      return []
    }

    const typingUserIds = typingUsers.value[conversationId] || []

    return typingUserIds.map(userId => {
      const member = conversation.members?.find(m => m.userId === userId)
      const displayName = member?.displayName || member?.username || 'Unknown'
      return {
        userId,
        displayName
      }
    })
  }

  function clearTypingUsers(conversationId) {
    if (conversationId) {
      delete typingUsers.value[conversationId]
    } else {
      typingUsers.value = {}
    }
  }

  function clearSeenReceipts(conversationId) {
    if (conversationId) {
      delete seenReceipts.value[conversationId]
    } else {
      seenReceipts.value = {}
    }
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('auth:sessionExpired', () => {
      clearTypingUsers()
      clearSeenReceipts()
    })
  }

  function getSeenReceipts(conversationId) {
    return seenReceipts.value[conversationId] || {}
  }

  function isMessageSeenBy(conversationId, messageId, userId) {
    const receipts = seenReceipts.value[conversationId]
    if (!receipts) return false
    return receipts[userId] === messageId
  }

  function getMessageSeenByUsers(conversationId, message, allMessages) {
    const messagesStore = useMessagesStore()
    const authStore = useAuthStore()
    const conversationsStore = useConversationsStore()

    if (!message || !conversationId) return []

    const receipts = seenReceipts.value[conversationId] || {}
    const seenByUserIds = []

    for (const [userId, lastReadMessageId] of Object.entries(receipts)) {
      if (userId === authStore.user?.id) {
        continue
      }

      const lastReadMessage = allMessages.find(m => m.id === lastReadMessageId)
      if (!lastReadMessage) {
        continue
      }

      const messageTime = new Date(message.createdAt).getTime()
      const lastReadTime = new Date(lastReadMessage.createdAt).getTime()

      if (messageTime <= lastReadTime) {
        seenByUserIds.push(userId)
      }
    }

    const conversation = conversationsStore.conversations.find(c => c.id === conversationId)
    if (!conversation) return []

    return seenByUserIds.map(userId => {
      const member = conversation.members?.find(m => m.userId === userId)
      return {
        userId,
        displayName: member?.displayName || member?.username || 'Unknown'
      }
    })
  }

  return {
    typingUsers,
    seenReceipts,
    initializeSubscriptions,
    subscribeToConversation,
    unsubscribeFromConversation,
    sendTypingStart,
    sendTypingStop,
    getTypingUsers,
    getTypingUsersDisplay,
    clearTypingUsers,
    clearSeenReceipts,
    getSeenReceipts,
    isMessageSeenBy,
    getMessageSeenByUsers
  }
})
