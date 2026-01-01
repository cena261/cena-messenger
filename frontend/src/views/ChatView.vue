<template>
  <div v-if="!conversationsStore.activeConversationId" class="no-conversation">
    <p>Select a conversation to start chatting</p>
  </div>

  <div v-else class="chat-view">
    <div class="chat-header">
      <h3>{{ conversationName }}</h3>
      <div v-if="otherUserId" class="header-actions">
        <button
          v-if="!blockingStore.isUserBlocked(otherUserId)"
          @click="handleBlockUser"
          class="block-btn"
        >
          Block User
        </button>
        <button
          v-else
          @click="handleUnblockUser"
          class="unblock-btn"
        >
          Unblock User
        </button>
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
          <div v-if="message.replyTo" class="message-reply-context">
            Replying to {{ getReplyToUsername(message.replyTo) }}: {{ getReplyToPreview(message.replyTo) }}
          </div>

          <div class="message-sender">{{ message.senderDisplayName || message.senderUsername }}</div>

          <div v-if="editingMessageId === message.id" class="message-edit">
            <input v-model="editContent" @keyup.enter="saveEdit" @keyup.escape="cancelEdit" />
            <div class="edit-actions">
              <button @click="saveEdit" class="save-btn">Save</button>
              <button @click="cancelEdit" class="cancel-btn">Cancel</button>
            </div>
          </div>

          <div v-else class="message-content">
            <span v-if="message.isDeleted" class="deleted-message">This message was deleted</span>
            <span v-else>{{ message.content }}</span>
            <span v-if="!message.isDeleted && isEdited(message)" class="edited-indicator">(edited)</span>
          </div>

          <div class="message-footer">
            <div class="message-time">{{ formatTime(message.createdAt) }}</div>
            <div v-if="message.senderId === authStore.user?.id && !message.isDeleted" class="message-actions">
              <button @click="startEdit(message)" class="action-btn">Edit</button>
              <button @click="handleDeleteMessage(message.id)" class="action-btn">Delete</button>
            </div>
            <div v-if="!message.isDeleted" class="message-actions">
              <button @click="startReply(message)" class="action-btn">Reply</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="message-input-container">
      <div v-if="replyingTo" class="replying-to">
        <span>Replying to {{ replyingTo.senderDisplayName || replyingTo.senderUsername }}: {{ replyingTo.content?.substring(0, 50) }}...</span>
        <button @click="cancelReply" class="cancel-reply-btn">✕</button>
      </div>
      <form @submit.prevent="sendMessageHandler">
        <input
          v-model="newMessage"
          type="text"
          placeholder="Type a message..."
          ref="messageInput"
        />
        <button type="submit" :disabled="!newMessage.trim()">Send</button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useConversationsStore } from '../stores/conversations'
import { useMessagesStore } from '../stores/messages'
import { useRealtimeStore } from '../stores/realtime'
import { useBlockingStore } from '../stores/blocking'

const authStore = useAuthStore()
const conversationsStore = useConversationsStore()
const messagesStore = useMessagesStore()
const realtimeStore = useRealtimeStore()
const blockingStore = useBlockingStore()

const newMessage = ref('')
const messagesContainer = ref(null)
const messageInput = ref(null)
const editingMessageId = ref(null)
const editContent = ref('')
const replyingTo = ref(null)

const conversationName = computed(() => {
  const conversation = conversationsStore.activeConversation
  if (!conversation) return 'Conversation'

  if (conversation.type === 'GROUP') {
    return conversation.name || 'Unnamed Group'
  }

  const otherMember = conversation.members?.find(m => m.userId !== authStore.user?.id)
  return otherMember?.displayName || otherMember?.username || 'Direct Message'
})

const messages = computed(() => {
  return messagesStore.getMessages(conversationsStore.activeConversationId)
})

const otherUserId = computed(() => {
  const conversation = conversationsStore.activeConversation
  if (!conversation || conversation.type === 'GROUP') return null

  const otherMember = conversation.members?.find(m => m.userId !== authStore.user?.id)
  return otherMember?.userId || null
})

watch(() => conversationsStore.activeConversationId, async (newId, oldId) => {
  if (oldId) {
    realtimeStore.unsubscribeFromConversation(oldId)
  }

  if (newId) {
    cancelEdit()
    cancelReply()
    await loadMessages()
    realtimeStore.subscribeToConversation(newId)
    await nextTick()
    scrollToBottom()
  }
}, { immediate: true })

watch(messages, async () => {
  await nextTick()
  scrollToBottom()
}, { deep: true })

onMounted(async () => {
  if (conversationsStore.activeConversationId) {
    await loadMessages()
    realtimeStore.subscribeToConversation(conversationsStore.activeConversationId)
    await nextTick()
    scrollToBottom()
  }
})

onUnmounted(() => {
  if (conversationsStore.activeConversationId) {
    realtimeStore.unsubscribeFromConversation(conversationsStore.activeConversationId)
  }
})

async function loadMessages() {
  try {
    await messagesStore.fetchMessages(conversationsStore.activeConversationId)
  } catch (error) {
    console.error('Failed to load messages:', error)
  }
}

async function sendMessageHandler() {
  const content = newMessage.value.trim()
  if (!content) return

  try {
    await messagesStore.sendMessage(
      conversationsStore.activeConversationId,
      content,
      replyingTo.value?.id || null
    )
    newMessage.value = ''
    cancelReply()
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error('Failed to send message:', error)
  }
}

function startEdit(message) {
  if (message.type !== 'TEXT' || message.isDeleted) return
  editingMessageId.value = message.id
  editContent.value = message.content
}

async function saveEdit() {
  if (!editContent.value.trim()) {
    cancelEdit()
    return
  }

  try {
    await messagesStore.editMessage(
      editingMessageId.value,
      conversationsStore.activeConversationId,
      editContent.value
    )
    cancelEdit()
  } catch (error) {
    console.error('Failed to edit message:', error)
  }
}

function cancelEdit() {
  editingMessageId.value = null
  editContent.value = ''
}

async function handleDeleteMessage(messageId) {
  try {
    await messagesStore.deleteMessage(
      messageId,
      conversationsStore.activeConversationId
    )
  } catch (error) {
    console.error('Failed to delete message:', error)
  }
}

function startReply(message) {
  replyingTo.value = message
  messageInput.value?.focus()
}

function cancelReply() {
  replyingTo.value = null
}

function getReplyToUsername(replyToId) {
  const msg = messages.value.find(m => m.id === replyToId)
  return msg?.senderDisplayName || msg?.senderUsername || 'Unknown'
}

function getReplyToPreview(replyToId) {
  const msg = messages.value.find(m => m.id === replyToId)
  if (!msg) return ''
  if (msg.isDeleted) return 'Deleted message'
  return msg.content?.substring(0, 30) || ''
}

function isEdited(message) {
  if (!message.updatedAt || !message.createdAt) return false

  const created = new Date(message.createdAt).getTime()
  const updated = new Date(message.updatedAt).getTime()

  return updated - created > 1000
}

function scrollToBottom() {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

function formatTime(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

async function handleBlockUser() {
  if (!otherUserId.value) return

  const confirmBlock = confirm('Are you sure you want to block this user? You will not be able to send or receive messages.')
  if (!confirmBlock) return

  try {
    await blockingStore.blockUser(otherUserId.value)
  } catch (error) {
    console.error('Failed to block user:', error)
    alert('Failed to block user. Please try again.')
  }
}

async function handleUnblockUser() {
  if (!otherUserId.value) return

  try {
    await blockingStore.unblockUser(otherUserId.value)
  } catch (error) {
    console.error('Failed to unblock user:', error)
    alert('Failed to unblock user. Please try again.')
  }
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
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-header h3 {
  margin: 0;
  font-size: 1.25rem;
  color: #333;
}

.header-actions {
  display: flex;
  gap: 0.5rem;
}

.block-btn {
  padding: 0.5rem 1rem;
  background-color: #f44336;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
}

.block-btn:hover {
  background-color: #d32f2f;
}

.unblock-btn {
  padding: 0.5rem 1rem;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
}

.unblock-btn:hover {
  background-color: #45a049;
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

.message-reply-context {
  font-size: 0.75rem;
  color: #666;
  font-style: italic;
  margin-bottom: 0.25rem;
  padding: 0.25rem;
  background-color: rgba(0, 0, 0, 0.05);
  border-radius: 4px;
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

.deleted-message {
  font-style: italic;
  color: #999;
}

.edited-indicator {
  font-size: 0.75rem;
  color: #999;
  font-style: italic;
  margin-left: 0.5rem;
}

.message-edit {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.message-edit input {
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

.edit-actions {
  display: flex;
  gap: 0.5rem;
}

.save-btn,
.cancel-btn {
  padding: 0.25rem 0.75rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
}

.save-btn {
  background-color: #4CAF50;
  color: white;
}

.save-btn:hover {
  background-color: #45a049;
}

.cancel-btn {
  background-color: #f44336;
  color: white;
}

.cancel-btn:hover {
  background-color: #d32f2f;
}

.message-footer {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-top: 0.25rem;
}

.message-time {
  font-size: 0.75rem;
  color: #999;
}

.message-actions {
  display: flex;
  gap: 0.5rem;
}

.action-btn {
  font-size: 0.75rem;
  color: #2196F3;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}

.action-btn:hover {
  text-decoration: underline;
}

.message-input-container {
  border-top: 1px solid #ddd;
  background-color: white;
}

.replying-to {
  padding: 0.5rem 1rem;
  background-color: #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.875rem;
  border-bottom: 1px solid #ddd;
}

.cancel-reply-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 1.25rem;
  color: #666;
  padding: 0;
  margin-left: 0.5rem;
}

.cancel-reply-btn:hover {
  color: #333;
}

.message-input-container form {
  padding: 1rem;
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

.message-input-container button[type="submit"] {
  padding: 0.75rem 1.5rem;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 1rem;
  font-weight: 500;
}

.message-input-container button[type="submit"]:hover:not(:disabled) {
  background-color: #45a049;
}

.message-input-container button[type="submit"]:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}
</style>
