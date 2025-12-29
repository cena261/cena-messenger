<template>
  <div v-if="!conversationId" class="no-conversation">
    <p>Select a conversation to start chatting</p>
  </div>

  <div v-else class="chat-view">
    <div class="chat-header">
      <h3>{{ conversationName }}</h3>
      <div v-if="typingUsersDisplay" class="typing-indicator">
        {{ typingUsersDisplay }}
      </div>
    </div>

    <div class="messages-container" ref="messagesContainer">
      <div v-if="messagesStore.isLoading" class="loading">
        Loading messages...
      </div>

      <div v-else-if="messages.length === 0" class="no-messages">
        No messages yet. Start the conversation!
      </div>

      <div v-else class="messages-list">
        <div
          v-for="message in messages"
          :key="message.id"
          class="message"
          :class="{ 'own-message': message.senderId === authStore.user?.id }"
        >
          <div class="message-sender">{{ message.senderDisplayName || message.senderUsername }}</div>
          <div class="message-content">{{ message.content }}</div>
          <div class="message-footer">
            <div class="message-time">{{ formatTime(message.createdAt) }}</div>
            <div v-if="message.senderId === authStore.user?.id && getSeenByUsers(message.id).length > 0" class="seen-by">
              ✓ Seen by {{ getSeenByUsers(message.id).join(', ') }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="message-input-container">
      <form @submit.prevent="sendMessage">
        <input
          v-model="newMessage"
          type="text"
          placeholder="Type a message..."
          @input="handleTyping"
          @blur="handleStopTyping"
        />
        <button type="submit" :disabled="!newMessage.trim()">Send</button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useConversationsStore } from '../stores/conversations'
import { useMessagesStore } from '../stores/messages'
import { useRealtimeStore } from '../stores/realtime'

const route = useRoute()
const authStore = useAuthStore()
const conversationsStore = useConversationsStore()
const messagesStore = useMessagesStore()
const realtimeStore = useRealtimeStore()

const newMessage = ref('')
const messagesContainer = ref(null)
const typingTimeout = ref(null)

const conversationId = computed(() => route.params.id)

const conversationName = computed(() => {
  const conversation = conversationsStore.conversations.find(
    c => c.id === conversationId.value
  )
  return conversation?.name || 'Direct Message'
})

const messages = computed(() => {
  return messagesStore.getMessages(conversationId.value)
})

const typingUsers = computed(() => {
  return realtimeStore.getTypingUsers(conversationId.value)
})

const typingUsersDisplay = computed(() => {
  const users = typingUsers.value
  console.log('typingUsersDisplay computed - users:', users, 'conversationId:', conversationId.value)

  if (users.length === 0) {
    console.log('No typing users')
    return ''
  }

  const conversation = conversationsStore.conversations.find(
    c => c.id === conversationId.value
  )

  console.log('Conversation found:', conversation)

  if (!conversation) {
    console.log('No conversation found, using fallback')
    return 'Someone is typing...'
  }

  const names = users.map(userId => {
    const member = conversation.members?.find(m => m.userId === userId)
    const name = member?.displayName || member?.username || 'Someone'
    console.log('User', userId, 'mapped to name:', name)
    return name
  })

  if (names.length === 1) {
    return `${names[0]} is typing...`
  } else if (names.length === 2) {
    return `${names[0]} and ${names[1]} are typing...`
  } else {
    return `${names.slice(0, -1).join(', ')}, and ${names[names.length - 1]} are typing...`
  }
})

watch(conversationId, async (newId, oldId) => {
  if (oldId) {
    realtimeStore.unsubscribeFromConversation(oldId)
  }

  if (newId) {
    await loadMessages()
    realtimeStore.subscribeToConversation(newId)
    await conversationsStore.markAsRead(newId)
    await nextTick()
    scrollToBottom()
  }
})

watch(messages, async (newMessages, oldMessages = []) => {
  console.log('Messages changed - old:', oldMessages.length, 'new:', newMessages.length)
  if (newMessages.length > oldMessages.length) {
    console.log('New message detected, scrolling and marking as read')
    await nextTick()
    setTimeout(() => {
      scrollToBottom()
    }, 100)

    if (conversationId.value) {
      console.log('Marking conversation as read:', conversationId.value)
      await conversationsStore.markAsRead(conversationId.value)
    }
  }
}, { deep: true })

onMounted(async () => {
  if (conversationId.value) {
    await loadMessages()
    realtimeStore.subscribeToConversation(conversationId.value)
    await conversationsStore.markAsRead(conversationId.value)
    await nextTick()
    scrollToBottom()
  }
})

onUnmounted(() => {
  if (conversationId.value) {
    realtimeStore.unsubscribeFromConversation(conversationId.value)
  }
})

async function loadMessages() {
  try {
    await messagesStore.fetchMessages(conversationId.value)
  } catch (error) {
    console.error('Failed to load messages:', error)
  }
}

async function sendMessage() {
  const content = newMessage.value.trim()
  if (!content) return

  try {
    await messagesStore.sendMessage(conversationId.value, content)
    newMessage.value = ''
    handleStopTyping()
  } catch (error) {
    console.error('Failed to send message:', error)
  }
}

function handleTyping() {
  console.log('Sending typing start for conversation:', conversationId.value)
  realtimeStore.sendTypingStart(conversationId.value)

  if (typingTimeout.value) {
    clearTimeout(typingTimeout.value)
  }

  typingTimeout.value = setTimeout(() => {
    handleStopTyping()
  }, 3000)
}

function handleStopTyping() {
  console.log('Sending typing stop for conversation:', conversationId.value)
  if (typingTimeout.value) {
    clearTimeout(typingTimeout.value)
    typingTimeout.value = null
  }
  realtimeStore.sendTypingStop(conversationId.value)
}

function scrollToBottom() {
  if (messagesContainer.value) {
    const scrollHeight = messagesContainer.value.scrollHeight
    const clientHeight = messagesContainer.value.clientHeight
    console.log('Scrolling to bottom - scrollHeight:', scrollHeight, 'clientHeight:', clientHeight)
    messagesContainer.value.scrollTop = scrollHeight
  } else {
    console.log('messagesContainer not available for scrolling')
  }
}

function formatTime(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function getSeenByUsers(messageId) {
  const receipts = realtimeStore.getSeenReceipts(conversationId.value)
  const conversation = conversationsStore.conversations.find(
    c => c.id === conversationId.value
  )
  if (!conversation) return []

  const seenByUserIds = Object.entries(receipts)
    .filter(([userId, lastReadMessageId]) => {
      if (userId === authStore.user?.id) return false
      const messageIndex = messages.value.findIndex(m => m.id === messageId)
      const lastReadIndex = messages.value.findIndex(m => m.id === lastReadMessageId)
      return lastReadIndex >= messageIndex
    })
    .map(([userId]) => userId)

  return seenByUserIds.map(userId => {
    const member = conversation.members?.find(m => m.userId === userId)
    return member?.displayName || member?.username || 'Unknown'
  })
}
</script>

<style scoped>
.no-conversation {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  color: #666;
  font-size: 1.125rem;
}

.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-header {
  padding: 1rem;
  border-bottom: 1px solid #ddd;
  background-color: #f9f9f9;
}

.chat-header h3 {
  margin: 0;
  font-size: 1.25rem;
  color: #333;
}

.typing-indicator {
  margin-top: 0.5rem;
  font-size: 0.875rem;
  color: #4CAF50;
  font-style: italic;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
  background-color: #fafafa;
}

.loading,
.no-messages {
  text-align: center;
  color: #666;
  padding: 2rem;
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.message {
  display: flex;
  flex-direction: column;
  max-width: 70%;
  padding: 0.75rem;
  border-radius: 8px;
  background-color: white;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.message.own-message {
  align-self: flex-end;
  background-color: #e3f2fd;
}

.message-sender {
  font-size: 0.75rem;
  font-weight: 600;
  color: #666;
  margin-bottom: 0.25rem;
}

.message-content {
  font-size: 1rem;
  color: #333;
  word-wrap: break-word;
}

.message-footer {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  margin-top: 0.25rem;
}

.message-time {
  font-size: 0.75rem;
  color: #999;
  text-align: right;
}

.seen-by {
  font-size: 0.65rem;
  color: #4CAF50;
  text-align: right;
  font-style: italic;
}

.message-input-container {
  padding: 1rem;
  border-top: 1px solid #ddd;
  background-color: white;
}

.message-input-container form {
  display: flex;
  gap: 0.5rem;
}

.message-input-container input {
  flex: 1;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

.message-input-container input:focus {
  outline: none;
  border-color: #4CAF50;
}

.message-input-container button {
  padding: 0.75rem 1.5rem;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 1rem;
  font-weight: 500;
}

.message-input-container button:hover:not(:disabled) {
  background-color: #45a049;
}

.message-input-container button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}
</style>
