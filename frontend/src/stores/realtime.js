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

      handleTypingEvent(data)
    })

    websocketService.subscribe(`/user/queue/message-updates`, (data) => {

      messagesStore.handleMessageUpdate(data)
    })

    websocketService.subscribe(`/user/queue/group-events`, async (data) => {

      await handleGroupEvent(data)
    })

    websocketService.subscribe(`/user/queue/reactions`, (data) => {

      messagesStore.handleReactionUpdate(data)
    })
  }

  async function handleGroupEvent(data) {
    const conversationsStore = useConversationsStore()



    await conversationsStore.fetchConversations()
  }

  function subscribeToConversation(conversationId) {
    const messagesStore = useMessagesStore()
    const destination = `/topic/conversation.${conversationId}`



    const existingSubscription = subscriptions.value.find(s => s.conversationId === conversationId)
    if (existingSubscription) {
      return
    }

    const unsubscribeFn = websocketService.subscribe(destination, async (message) => {

      messagesStore.addMessage(conversationId, message)

      const conversationsStore = useConversationsStore()
      conversationsStore.updateConversationLastMessage(conversationId, message)

      const authStore = useAuthStore()
      if (message.senderId !== authStore.user?.id && conversationsStore.activeConversationId === conversationId) {
        try {
          const conversationsApi = await import('../api/conversations')
          await conversationsApi.markConversationAsRead(conversationId)

        } catch (error) {
          console.error('Failed to auto-mark conversation as read:', error)
        }
      }
    })

    subscriptions.value.push({ conversationId, destination, unsubscribe: unsubscribeFn })

  }

  function unsubscribeFromConversation(conversationId) {
    const index = subscriptions.value.findIndex(s => s.conversationId === conversationId)

    if (index !== -1) {
      const subscription = subscriptions.value[index]
      if (subscription.unsubscribe && typeof subscription.unsubscribe === 'function') {
        subscription.unsubscribe()
      } else {
        const destination = `/topic/conversation.${conversationId}`
        websocketService.unsubscribe(destination)
      }
      subscriptions.value.splice(index, 1)
    }
  }

  function sendTypingStart(conversationId) {
    websocketService.send('/app/typing/start', { conversationId })
  }

  function sendTypingStop(conversationId) {
    websocketService.send('/app/typing/stop', { conversationId })
  }

  function handleTypingEvent(data) {
    const { conversationId, userId, typing } = data
    const authStore = useAuthStore()

    if (userId === authStore.user?.id) {
      return
    }

    if (!typingUsers.value[conversationId]) {
      typingUsers.value[conversationId] = {}
    }

    if (typing) {
      typingUsers.value[conversationId][userId] = Date.now()

      setTimeout(() => {
        if (typingUsers.value[conversationId] && typingUsers.value[conversationId][userId]) {
          delete typingUsers.value[conversationId][userId]
          if (Object.keys(typingUsers.value[conversationId]).length === 0) {
            delete typingUsers.value[conversationId]
          }
        }
      }, 5000)
    } else {
      if (typingUsers.value[conversationId]) {
        delete typingUsers.value[conversationId][userId]
        if (Object.keys(typingUsers.value[conversationId]).length === 0) {
          delete typingUsers.value[conversationId]
        }
      }
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
    return typingUsers.value[conversationId] || {}
  }

  function getTypingUsersDisplay(conversationId) {
    const conversationsStore = useConversationsStore()
    const authStore = useAuthStore()
    const conversation = conversationsStore.conversations.find(c => c.id === conversationId)
    if (!conversation) return []

    const currentUserId = authStore.user?.id
    const typingUserIds = Object.keys(typingUsers.value[conversationId] || {})
      .filter(uid => uid !== currentUserId)

    return conversation.members
      ?.filter(m => typingUserIds.includes(m.userId))
      .map(m => ({ userId: m.userId, displayName: m.displayName || m.username })) || []
  }

  function clearTypingUsers(conversationId) {
    if (conversationId && typingUsers.value[conversationId]) {
      delete typingUsers.value[conversationId]
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
