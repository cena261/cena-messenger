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

    // Subscribe to typing indicators
    websocketService.subscribe(`/user/queue/typing`, (data) => {
      console.log('Typing event received:', data)
      handleTypingEvent(data)
    })
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

    const unsubscribeFn = websocketService.subscribe(destination, (message) => {
      console.log('Message received via WebSocket:', message)
      messagesStore.addMessage(conversationId, message)

      const conversationsStore = useConversationsStore()
      conversationsStore.updateConversationLastMessage(conversationId, message)
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
    console.log('sendTypingStart called, conversationId:', conversationId)
    websocketService.send('/app/typing/start', { conversationId })
  }

  function sendTypingStop(conversationId) {
    console.log('sendTypingStop called, conversationId:', conversationId)
    websocketService.send('/app/typing/stop', { conversationId })
  }

  function handleTypingEvent(data) {
    const authStore = useAuthStore()
    const { conversationId, userId, typing } = data

    console.log('handleTypingEvent - conversationId:', conversationId, 'userId:', userId, 'typing:', typing)

    if (userId === authStore.user?.id) {
      console.log('Ignoring own typing event')
      return
    }

    if (!typingUsers.value[conversationId]) {
      typingUsers.value[conversationId] = []
    }

    if (typing) {
      if (!typingUsers.value[conversationId].includes(userId)) {
        typingUsers.value[conversationId] = [...typingUsers.value[conversationId], userId]
        console.log('Added user to typing list:', typingUsers.value[conversationId])
      }
    } else {
      typingUsers.value[conversationId] = typingUsers.value[conversationId].filter(
        id => id !== userId
      )
      console.log('Removed user from typing list:', typingUsers.value[conversationId])
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

  function clearTypingUsers(conversationId) {
    if (conversationId) {
      delete typingUsers.value[conversationId]
    } else {
      typingUsers.value = {}
    }
  }

  function getSeenReceipts(conversationId) {
    return seenReceipts.value[conversationId] || {}
  }

  function isMessageSeenBy(conversationId, messageId, userId) {
    const receipts = seenReceipts.value[conversationId]
    if (!receipts) return false
    return receipts[userId] === messageId
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
    clearTypingUsers,
    getSeenReceipts,
    isMessageSeenBy
  }
})
