<template>
  <div v-if="!conversationsStore.activeConversationId" class="no-conversation">
    <p>Select a conversation to start chatting</p>
  </div>

  <div v-else class="chat-view">
    <div class="chat-header">
      <h3>{{ conversationName }}</h3>
      <div class="header-actions">
        <button
          v-if="conversationsStore.activeConversation?.type === 'GROUP'"
          @click="openGroupManagement"
          class="group-settings-btn"
        >
          Group Settings
        </button>
        <button
          v-if="otherUserId && !blockingStore.isUserBlocked(otherUserId)"
          @click="handleBlockUser"
          class="block-btn"
        >
          Block User
        </button>
        <button
          v-if="otherUserId && blockingStore.isUserBlocked(otherUserId)"
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
            <span v-if="isMessageDeleted(message)" class="deleted-message">This message was deleted</span>
            <div v-else-if="message.type === 'IMAGE'" class="media-content">
              <img :src="message.mediaUrl" :alt="message.mediaMetadata?.fileName" class="media-image" />
            </div>
            <div v-else-if="message.type === 'VIDEO'" class="media-content media-placeholder">
              <div class="placeholder-icon">🎥</div>
              <div class="placeholder-text">Video: {{ message.mediaMetadata?.fileName || 'video file' }}</div>
              <a :href="message.mediaUrl" target="_blank" class="media-link">Download</a>
            </div>
            <div v-else-if="message.type === 'AUDIO'" class="media-content media-placeholder">
              <div class="placeholder-icon">🎵</div>
              <div class="placeholder-text">Audio: {{ message.mediaMetadata?.fileName || 'audio file' }}</div>
              <a :href="message.mediaUrl" target="_blank" class="media-link">Download</a>
            </div>
            <div v-else-if="message.type === 'MEDIA'" class="media-content">
              <a :href="message.mediaUrl" target="_blank" class="media-link">
                {{ message.mediaMetadata?.fileName || 'Download file' }}
              </a>
            </div>
            <span v-else>{{ message.content }}</span>
            <span v-if="!isMessageDeleted(message) && isEdited(message)" class="edited-indicator">(edited)</span>
          </div>

          <div v-if="!isMessageDeleted(message) && message.reactions && Object.keys(message.reactions).length > 0" class="message-reactions">
            <span
              v-for="(emoji, userId) in message.reactions"
              :key="userId"
              class="reaction-item"
              :class="{ 'own-reaction': userId === authStore.user?.id }"
              @click="handleToggleReaction(message.id, emoji)"
              :title="`${getUserDisplayName(userId)}`"
            >
              {{ emoji }}
            </span>
          </div>

          <div class="message-footer">
            <div class="message-time">{{ formatTime(message.createdAt) }}</div>
            <div v-if="message.senderId === authStore.user?.id && !isMessageDeleted(message)" class="message-seen">
              {{ formatSeenIndicator(getSeenByUsers(message)) }}
            </div>
            <div v-if="message.senderId === authStore.user?.id && !isMessageDeleted(message)" class="message-actions">
              <button v-if="message.type === 'TEXT'" @click="startEdit(message)" class="action-btn">Edit</button>
              <button @click="handleDeleteMessage(message.id)" class="action-btn">Delete</button>
            </div>
            <div v-if="!isMessageDeleted(message)" class="message-actions">
              <button @click="startReply(message)" class="action-btn">Reply</button>
              <button @click="toggleReactionPicker(message.id)" class="action-btn">React</button>
            </div>
          </div>

          <div v-if="showReactionPicker === message.id" class="reaction-picker">
            <span
              v-for="emoji in availableReactions"
              :key="emoji"
              class="reaction-emoji"
              @click="handleToggleReaction(message.id, emoji)"
            >
              {{ emoji }}
            </span>
          </div>
        </div>
      </div>

      <div v-show="typingUsersDisplay.length > 0" class="typing-indicator">
        <span v-if="typingUsersDisplay.length === 1">
          {{ typingUsersDisplay[0].displayName }} is typing...
        </span>
        <span v-else-if="typingUsersDisplay.length === 2">
          {{ typingUsersDisplay[0].displayName }} and {{ typingUsersDisplay[1].displayName }} are typing...
        </span>
        <span v-else>
          {{ typingUsersDisplay.length }} people are typing...
        </span>
      </div>
    </div>

    <div class="message-input-container">
      <div v-if="replyingTo" class="replying-to">
        <span>Replying to {{ replyingTo.senderDisplayName || replyingTo.senderUsername }}: {{ replyingTo.content?.substring(0, 50) }}...</span>
        <button @click="cancelReply" class="cancel-reply-btn">✕</button>
      </div>
      <div v-if="uploadingFile" class="uploading-indicator">
        Uploading {{ uploadingFile.name }}...
      </div>
      <form @submit.prevent="sendMessageHandler">
        <input
          type="file"
          ref="fileInput"
          @change="handleFileSelect"
          style="display: none"
          accept="image/*,video/*,audio/*"
        />
        <button type="button" @click="openFileDialog" class="attach-btn">📎</button>
        <input
          v-model="newMessage"
          type="text"
          placeholder="Type a message..."
          ref="messageInput"
          @input="handleTyping"
          :disabled="isSendingMessage"
        />
        <button type="submit" :disabled="!newMessage.trim() || isSendingMessage">Send</button>
      </form>
    </div>

    <GroupManagementModal
      :isOpen="isGroupManagementOpen"
      :conversation="conversationsStore.activeConversation"
      @close="closeGroupManagement"
      @groupUpdated="handleGroupUpdated"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useConversationsStore } from '../stores/conversations'
import { useMessagesStore } from '../stores/messages'
import { useRealtimeStore } from '../stores/realtime'
import { useBlockingStore } from '../stores/blocking'
import * as conversationsApi from '../api/conversations'
import GroupManagementModal from '../components/GroupManagementModal.vue'

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
const originalEditContent = ref('')
const replyingTo = ref(null)
const fileInput = ref(null)
const uploadingFile = ref(null)
const isGroupManagementOpen = ref(false)
const showReactionPicker = ref(null)
const availableReactions = ['👍', '❤️', '😂', '😮', '😢', '🙏']
const typingTimeout = ref(null)
const isTyping = ref(false)
const isSendingMessage = ref(false)

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

const typingUsersDisplay = computed(() => {
  if (!conversationsStore.activeConversationId) return []

  const conversationId = conversationsStore.activeConversationId
  const typingUserIds = realtimeStore.typingUsers[conversationId] || []

  if (typingUserIds.length === 0) return []

  const conversation = conversationsStore.activeConversation
  if (!conversation) return []

  return typingUserIds.map(userId => {
    const member = conversation.members?.find(m => m.userId === userId)
    const displayName = member?.displayName || member?.username || 'Unknown'
    return {
      userId,
      displayName
    }
  })
})

function isMessageDeleted(message) {
  return message.isDeleted === true || message.deleted === true
}

function getUserDisplayName(userId) {
  const conversation = conversationsStore.activeConversation
  if (!conversation) return 'Unknown'

  const member = conversation.members?.find(m => m.userId === userId)
  return member?.displayName || member?.username || 'Unknown'
}

function getSeenByUsers(message) {
  if (!conversationsStore.activeConversationId) return []
  if (message.senderId !== authStore.user?.id) return []
  if (isMessageDeleted(message)) return []

  return realtimeStore.getMessageSeenByUsers(
    conversationsStore.activeConversationId,
    message,
    messages.value
  )
}

function formatSeenIndicator(seenByUsers) {
  if (!seenByUsers || seenByUsers.length === 0) return ''

  if (seenByUsers.length === 1) {
    return `Seen by ${seenByUsers[0].displayName}`
  } else if (seenByUsers.length === 2) {
    return `Seen by ${seenByUsers[0].displayName} and ${seenByUsers[1].displayName}`
  } else {
    return `Seen by ${seenByUsers.length} people`
  }
}

watch(() => conversationsStore.activeConversationId, async (newId, oldId) => {
  if (oldId) {
    handleStopTyping()
    realtimeStore.unsubscribeFromConversation(oldId)
  }

  if (newId) {
    cancelEdit()
    cancelReply()
    showReactionPicker.value = null
    await loadMessages()
    realtimeStore.subscribeToConversation(newId)
    await nextTick()
    scrollToBottom()
  }
}, { immediate: true })

watch(messages, async () => {
  await nextTick()
  scrollToBottomIfNearBottom()
}, { deep: true })

watch(typingUsersDisplay, async () => {
  await nextTick()
  scrollToBottomIfNearBottom()
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
  handleStopTyping()
  if (conversationsStore.activeConversationId) {
    realtimeStore.unsubscribeFromConversation(conversationsStore.activeConversationId)
  }
})

async function loadMessages() {
  try {
    await messagesStore.fetchMessages(conversationsStore.activeConversationId)
    await conversationsApi.markConversationAsRead(conversationsStore.activeConversationId)
  } catch (error) {
    console.error('Failed to load messages:', error)
  }
}

async function sendMessageHandler() {
  const content = newMessage.value.trim()
  if (!content || isSendingMessage.value) return

  handleStopTyping()
  isSendingMessage.value = true

  try {
    await messagesStore.sendMessage(
      conversationsStore.activeConversationId,
      content,
      replyingTo.value?.id || null
    )
    newMessage.value = ''
    cancelReply()
    await nextTick()
    scrollToBottomIfNearBottom()
  } catch (error) {
    console.error('Failed to send message:', error)
  } finally {
    isSendingMessage.value = false
  }
}

function startEdit(message) {
  if (message.type !== 'TEXT' || isMessageDeleted(message)) return
  editingMessageId.value = message.id
  editContent.value = message.content
  originalEditContent.value = message.content
}

async function saveEdit() {
  if (!editContent.value.trim()) {
    cancelEdit()
    return
  }

  const messageId = editingMessageId.value
  const conversationId = conversationsStore.activeConversationId

  try {
    await messagesStore.editMessage(
      messageId,
      conversationId,
      editContent.value
    )
    cancelEdit()
  } catch (error) {
    console.error('Failed to edit message:', error)
    const messages = messagesStore.getMessages(conversationId)
    const message = messages.find(m => m.id === messageId)
    if (message && originalEditContent.value) {
      message.content = originalEditContent.value
    }
    cancelEdit()
  }
}

function cancelEdit() {
  editingMessageId.value = null
  editContent.value = ''
  originalEditContent.value = ''
}

async function handleDeleteMessage(messageId) {
  const confirmed = confirm('Are you sure you want to delete this message?')
  if (!confirmed) return

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
  if (isMessageDeleted(msg)) return 'Deleted message'
  return msg.content?.substring(0, 30) || ''
}

function isEdited(message) {
  if (!message.updatedAt || !message.createdAt) return false

  const created = new Date(message.createdAt).getTime()
  const updated = new Date(message.updatedAt).getTime()

  return updated - created > 1000
}

function isNearBottom() {
  if (!messagesContainer.value) return true
  const threshold = 150
  const scrollTop = messagesContainer.value.scrollTop
  const scrollHeight = messagesContainer.value.scrollHeight
  const clientHeight = messagesContainer.value.clientHeight
  return scrollHeight - scrollTop - clientHeight < threshold
}

function scrollToBottom() {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

function scrollToBottomIfNearBottom() {
  if (isNearBottom()) {
    scrollToBottom()
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

function openFileDialog() {
  fileInput.value?.click()
}

async function handleFileSelect(event) {
  const file = event.target.files?.[0]
  if (!file) return

  uploadingFile.value = file

  try {
    let mediaType = 'MEDIA'

    if (file.type.startsWith('image/')) {
      mediaType = 'IMAGE'
    } else if (file.type.startsWith('video/')) {
      mediaType = 'VIDEO'
    } else if (file.type.startsWith('audio/')) {
      mediaType = 'AUDIO'
    }

    await messagesStore.sendMediaMessage(
      conversationsStore.activeConversationId,
      file,
      mediaType,
      replyingTo.value?.id || null
    )

    cancelReply()
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error('Failed to send media message:', error)
    alert('Failed to send media file. Please try again.')
  } finally {
    uploadingFile.value = null
    if (fileInput.value) {
      fileInput.value.value = ''
    }
  }
}

async function openGroupManagement() {
  await conversationsStore.fetchConversations()
  isGroupManagementOpen.value = true
}

function closeGroupManagement() {
  isGroupManagementOpen.value = false
}

async function handleGroupUpdated() {
  await conversationsStore.fetchConversations()
}

function toggleReactionPicker(messageId) {
  showReactionPicker.value = showReactionPicker.value === messageId ? null : messageId
}

async function handleToggleReaction(messageId, emoji) {
  showReactionPicker.value = null
  try {
    await messagesStore.toggleReaction(
      conversationsStore.activeConversationId,
      messageId,
      emoji
    )
  } catch (error) {
    console.error('Failed to toggle reaction:', error)
  }
}

function handleTyping() {
  if (!conversationsStore.activeConversationId) return

  if (!newMessage.value.trim()) {
    handleStopTyping()
    return
  }

  if (!isTyping.value) {
    isTyping.value = true
    realtimeStore.sendTypingStart(conversationsStore.activeConversationId)
  }

  if (typingTimeout.value) {
    clearTimeout(typingTimeout.value)
  }

  typingTimeout.value = setTimeout(() => {
    handleStopTyping()
  }, 3000)
}

function handleStopTyping() {
  if (!conversationsStore.activeConversationId) return

  if (isTyping.value) {
    isTyping.value = false
    realtimeStore.sendTypingStop(conversationsStore.activeConversationId)
  }

  if (typingTimeout.value) {
    clearTimeout(typingTimeout.value)
    typingTimeout.value = null
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

.group-settings-btn {
  padding: 0.5rem 1rem;
  background-color: #2196F3;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
}

.group-settings-btn:hover {
  background-color: #1976D2;
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

.message-seen {
  font-size: 0.7rem;
  color: #2196F3;
  font-style: italic;
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

.media-content {
  margin-top: 0.5rem;
}

.media-image {
  max-width: 300px;
  max-height: 300px;
  border-radius: 8px;
  cursor: pointer;
}

.media-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem;
  background-color: #f5f5f5;
  border-radius: 8px;
  border: 1px solid #ddd;
}

.placeholder-icon {
  font-size: 2rem;
}

.placeholder-text {
  font-size: 0.875rem;
  color: #666;
  text-align: center;
}

.media-link {
  color: #2196F3;
  text-decoration: none;
  font-weight: 500;
}

.media-link:hover {
  text-decoration: underline;
}

.attach-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  padding: 0.5rem;
}

.attach-btn:hover {
  opacity: 0.7;
}

.uploading-indicator {
  padding: 0.5rem 1rem;
  background-color: #e3f2fd;
  border-bottom: 1px solid #ddd;
  font-size: 0.875rem;
  color: #666;
}

.typing-indicator {
  padding: 0.75rem 1rem;
  background-color: #f5f5f5;
  border-top: 1px solid #ddd;
  font-size: 0.875rem;
  color: #666;
  font-style: italic;
}

.message-reactions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  margin-top: 0.5rem;
}

.reaction-item {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.5rem;
  background-color: #f0f0f0;
  border-radius: 12px;
  font-size: 1rem;
  cursor: pointer;
  transition: background-color 0.2s;
}

.reaction-item:hover {
  background-color: #e0e0e0;
}

.reaction-item.own-reaction {
  background-color: #d4edda;
  border: 1px solid #4CAF50;
}

.reaction-picker {
  display: flex;
  gap: 0.5rem;
  padding: 0.5rem;
  background-color: #fff;
  border: 1px solid #ddd;
  border-radius: 8px;
  margin-top: 0.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.reaction-emoji {
  font-size: 1.5rem;
  cursor: pointer;
  padding: 0.25rem;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.reaction-emoji:hover {
  background-color: #f0f0f0;
}
</style>
