<template>
  <div v-if="!conversationsStore.activeConversationId" class="no-conversation">
    <svg width="120" height="120" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
      <path d="M9 10h6M9 14h4"/>
    </svg>
    <h2>Welcome to Chats</h2>
    <p>Select a conversation to start messaging</p>
  </div>

  <div v-else class="chat-view">
    <!-- Header -->
    <div class="chat-header">
      <div class="header-info">
        <div class="chat-avatar">
          <div class="avatar-placeholder">{{ conversationName.charAt(0) }}</div>
        </div>
        <div class="chat-details">
          <h2 class="chat-name">{{ conversationName }}</h2>
          <p v-if="typingUsersDisplay.length > 0" class="chat-status typing">
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
            <span v-if="typingUsersDisplay.length === 1">
              {{ typingUsersDisplay[0].displayName }} is typing
            </span>
            <span v-else>
              {{ typingUsersDisplay.length }} people are typing
            </span>
          </p>
        </div>
      </div>
      <div class="header-actions">
        <button
          v-if="conversationsStore.activeConversation?.type === 'GROUP'"
          @click="openGroupManagement"
          class="header-btn"
          title="Group Settings"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="3"/>
            <path d="M12 1v6m0 6v6m6-10l-4.5 4.5m-3 3L6 23m5-11l6 6m-12 0l6-6"/>
          </svg>
        </button>
        <button
          v-if="otherUserId && !blockingStore.isUserBlocked(otherUserId)"
          @click="handleBlockUser"
          class="header-btn"
          title="Block User"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/>
          </svg>
        </button>
        <button
          v-if="otherUserId && blockingStore.isUserBlocked(otherUserId)"
          @click="handleUnblockUser"
          class="header-btn primary"
          title="Unblock User"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 11l3 3L22 4"/>
            <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- Messages Container -->
    <div class="messages-container" ref="messagesContainer">
      <div v-if="messagesStore.isLoading" class="messages-loading">
        <div class="spinner"></div>
      </div>

      <div v-else-if="messages.length === 0" class="messages-empty">
        <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <p>No messages yet. Start the conversation!</p>
      </div>

      <div v-else class="messages-list">
        <div v-for="(message, index) in messages" :key="message.id" class="message-wrapper">
          <!-- Message Bubble -->
          <div :class="['message', message.senderId === authStore.user?.id ? 'own' : 'other']">
            <!-- Avatar for received messages -->
            <div v-if="message.senderId !== authStore.user?.id && shouldShowAvatar(index)" class="message-avatar">
              <div class="avatar-small">{{ getUserInitial(message.senderId) }}</div>
            </div>
            <div v-else-if="message.senderId !== authStore.user?.id" class="message-avatar-spacer"></div>

            <div class="message-content-wrapper">
              <!-- Sender name for received messages in groups -->
              <div
                v-if="message.senderId !== authStore.user?.id && conversationsStore.activeConversation?.type === 'GROUP' && shouldShowAvatar(index)"
                class="message-sender-name"
              >
                {{ message.senderDisplayName || message.senderUsername }}
              </div>

              <!-- Reply Context -->
              <div v-if="message.replyTo" class="message-reply-context">
                <div class="reply-bar"></div>
                <div class="reply-content">
                  <span class="reply-to">{{ getReplyToUsername(message.replyTo) }}</span>
                  <span class="reply-preview">{{ getReplyToPreview(message.replyTo) }}</span>
                </div>
              </div>

              <!-- Edit Mode -->
              <div v-if="editingMessageId === message.id" class="message-edit">
                <input
                  v-model="editContent"
                  @keyup.enter="saveEdit"
                  @keyup.escape="cancelEdit"
                  placeholder="Edit message..."
                  class="edit-input"
                />
                <div class="edit-actions">
                  <button @click="saveEdit" class="edit-btn save">Save</button>
                  <button @click="cancelEdit" class="edit-btn cancel">Cancel</button>
                </div>
              </div>

              <!-- Message Bubble -->
              <div v-else :class="['message-bubble', message.senderId === authStore.user?.id ? 'sent' : 'received']">
                <span v-if="isMessageDeleted(message)" class="deleted-text">This message was deleted</span>

                <div v-else-if="message.type === 'IMAGE'" class="media-content">
                  <img :src="message.mediaUrl" :alt="message.mediaMetadata?.fileName" class="media-image" />
                </div>

                <div v-else-if="message.type === 'VIDEO' || message.type === 'AUDIO'" class="media-placeholder">
                  <div class="media-icon">{{ message.type === 'VIDEO' ? '🎥' : '🎵' }}</div>
                  <div class="media-info">
                    <span class="media-name">{{ message.mediaMetadata?.fileName || message.type.toLowerCase() }}</span>
                    <a :href="message.mediaUrl" target="_blank" class="media-download">Download</a>
                  </div>
                </div>

                <div v-else-if="message.type === 'MEDIA'" class="media-placeholder">
                  <a :href="message.mediaUrl" target="_blank" class="media-link">
                    📎 {{ message.mediaMetadata?.fileName || 'Download file' }}
                  </a>
                </div>

                <div v-else class="message-text">
                  {{ message.content }}
                  <span v-if="isEdited(message)" class="edited-badge">edited</span>
                </div>

                <!-- Reactions -->
                <div v-if="!isMessageDeleted(message) && message.reactions && Object.keys(message.reactions).length > 0" class="message-reactions">
                  <button
                    v-for="(emoji, userId) in message.reactions"
                    :key="userId"
                    :class="['reaction-badge', { own: userId === authStore.user?.id }]"
                    @click="handleToggleReaction(message.id, emoji)"
                    :title="`${getUserDisplayName(userId)}`"
                  >
                    {{ emoji }}
                  </button>
                </div>
              </div>

              <!-- Message Footer -->
              <div class="message-footer">
                <span class="message-time">{{ formatTime(message.createdAt) }}</span>

                <!-- Seen indicator for own messages -->
                <span
                  v-if="message.senderId === authStore.user?.id && !isMessageDeleted(message)"
                  class="message-seen"
                >
                  {{ formatSeenIndicator(getSeenByUsers(message)) }}
                </span>

                <!-- Actions -->
                <div class="message-actions">
                  <button
                    v-if="!isMessageDeleted(message)"
                    @click="startReply(message)"
                    class="action-btn"
                    title="Reply"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M9 14L4 9l5-5"/>
                      <path d="M20 20v-7a4 4 0 0 0-4-4H4"/>
                    </svg>
                  </button>

                  <button
                    v-if="!isMessageDeleted(message)"
                    @click="toggleReactionPicker(message.id)"
                    class="action-btn"
                    title="React"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="10"/>
                      <path d="M8 14s1.5 2 4 2 4-2 4-2"/>
                      <line x1="9" y1="9" x2="9.01" y2="9"/>
                      <line x1="15" y1="9" x2="15.01" y2="9"/>
                    </svg>
                  </button>

                  <button
                    v-if="message.senderId === authStore.user?.id && message.type === 'TEXT' && !isMessageDeleted(message)"
                    @click="startEdit(message)"
                    class="action-btn"
                    title="Edit"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                    </svg>
                  </button>

                  <button
                    v-if="message.senderId === authStore.user?.id && !isMessageDeleted(message)"
                    @click="handleDeleteMessage(message.id)"
                    class="action-btn delete"
                    title="Delete"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                    </svg>
                  </button>
                </div>
              </div>

              <!-- Reaction Picker -->
              <div v-if="showReactionPicker === message.id" class="reaction-picker">
                <button
                  v-for="emoji in availableReactions"
                  :key="emoji"
                  class="reaction-option"
                  @click="handleToggleReaction(message.id, emoji)"
                >
                  {{ emoji }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Input Area -->
    <div class="input-container">
      <!-- Error Messages -->
      <div v-if="sendError" class="input-error">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        {{ sendError }}
      </div>

      <div v-if="editError" class="input-error">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        {{ editError }}
      </div>

      <div v-if="deleteError" class="input-error">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        {{ deleteError }}
      </div>

      <!-- Reply Preview -->
      <div v-if="replyingTo" class="reply-preview">
        <div class="reply-info">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 14L4 9l5-5"/>
            <path d="M20 20v-7a4 4 0 0 0-4-4H4"/>
          </svg>
          <div class="reply-details">
            <span class="reply-to-name">{{ replyingTo.senderDisplayName || replyingTo.senderUsername }}</span>
            <span class="reply-to-content">{{ replyingTo.content?.substring(0, 50) }}...</span>
          </div>
        </div>
        <button @click="cancelReply" class="reply-cancel">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>

      <!-- Upload Preview -->
      <div v-if="uploadingFile" class="upload-preview">
        <div class="upload-info">
          <div class="upload-spinner"></div>
          <span>Uploading {{ uploadingFile.name }}...</span>
        </div>
      </div>

      <!-- Input Form -->
      <form @submit.prevent="sendMessageHandler" class="input-form">
        <input
          type="file"
          ref="fileInput"
          @change="handleFileSelect"
          style="display: none"
          accept="image/*,video/*,audio/*"
        />

        <button
          type="button"
          @click="openFileDialog"
          class="input-btn"
          :disabled="!authStore.isAuthenticated"
          title="Attach file"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/>
          </svg>
        </button>

        <input
          v-model="newMessage"
          type="text"
          placeholder="Type a message..."
          ref="messageInput"
          @input="handleTyping"
          :disabled="isSendingMessage || !authStore.isAuthenticated"
          class="message-input"
        />

        <button
          type="submit"
          :disabled="!newMessage.trim() || isSendingMessage || !authStore.isAuthenticated"
          class="send-btn"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="22" y1="2" x2="11" y2="13"/>
            <polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
        </button>
      </form>
    </div>

    <!-- Group Management Modal -->
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
const sendError = ref(null)
const editError = ref(null)
const deleteError = ref(null)

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

function shouldShowAvatar(index) {
  if (index === 0) return true
  const prevMessage = messages.value[index - 1]
  const currentMessage = messages.value[index]
  return prevMessage.senderId !== currentMessage.senderId
}

function getUserInitial(userId) {
  const conversation = conversationsStore.activeConversation
  if (!conversation) return '?'

  const member = conversation.members?.find(m => m.userId === userId)
  const name = member?.displayName || member?.username || '?'
  return name.charAt(0).toUpperCase()
}

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
    return `Seen`
  } else {
    return `Seen by ${seenByUsers.length}`
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
    sendError.value = null
    editError.value = null
    deleteError.value = null
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
  sendError.value = null

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
    sendError.value = error.response?.data?.message || 'Failed to send message. Please try again.'
  } finally {
    isSendingMessage.value = false
  }
}

function startEdit(message) {
  if (message.type !== 'TEXT' || isMessageDeleted(message)) return
  editingMessageId.value = message.id
  editContent.value = message.content
  originalEditContent.value = message.content
  editError.value = null
}

async function saveEdit() {
  if (!editContent.value.trim()) {
    cancelEdit()
    return
  }

  const messageId = editingMessageId.value
  const conversationId = conversationsStore.activeConversationId
  editError.value = null

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
    editError.value = error.response?.data?.message || 'Failed to edit message.'
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

  deleteError.value = null

  try {
    await messagesStore.deleteMessage(
      messageId,
      conversationsStore.activeConversationId
    )
  } catch (error) {
    console.error('Failed to delete message:', error)
    deleteError.value = error.response?.data?.message || 'Failed to delete message. Please try again.'
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
  return date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
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

  sendError.value = null

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
/* Container */
.no-conversation {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
  color: var(--color-text-secondary);
}

.no-conversation svg {
  margin-bottom: 24px;
  opacity: 0.2;
}

.no-conversation h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 8px 0;
}

.no-conversation p {
  font-size: 15px;
  margin: 0;
}

.chat-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-bg-primary);
}

/* Header */
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: var(--color-bg-secondary);
  border-bottom: 1px solid var(--color-border);
}

.header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-avatar .avatar-placeholder {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--color-gradient-warm);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
}

.chat-details {
  display: flex;
  flex-direction: column;
}

.chat-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.chat-status {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}

.chat-status.typing {
  color: var(--color-primary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.typing-dot {
  width: 4px;
  height: 4px;
  background: var(--color-primary);
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-6px); }
}

.header-actions {
  display: flex;
  gap: 8px;
}

.header-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: none;
  background: var(--color-bg-hover);
  color: var(--color-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.header-btn:hover {
  background: var(--color-border);
  color: var(--color-text-primary);
}

.header-btn.primary {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

/* Messages Container */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column-reverse;
}

.messages-container::-webkit-scrollbar {
  width: 6px;
}

.messages-container::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 3px;
}

.messages-loading,
.messages-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
}

.messages-empty svg {
  margin-bottom: 16px;
  opacity: 0.2;
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* Message */
.message {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.message.own {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.avatar-small {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--color-accent);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.message-avatar-spacer {
  width: 32px;
  flex-shrink: 0;
}

.message-content-wrapper {
  display: flex;
  flex-direction: column;
  max-width: 65%;
  gap: 4px;
}

.message.own .message-content-wrapper {
  align-items: flex-end;
}

.message-sender-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}

.message-reply-context {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  background: var(--color-bg-hover);
  border-radius: 8px;
  font-size: 13px;
  margin-bottom: 4px;
}

.reply-bar {
  width: 3px;
  background: var(--color-primary);
  border-radius: 2px;
}

.reply-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.reply-to {
  font-weight: 600;
  color: var(--color-primary);
}

.reply-preview {
  color: var(--color-text-secondary);
}

/* Message Bubble */
.message-bubble {
  padding: 10px 14px;
  border-radius: 16px;
  font-size: 15px;
  line-height: 1.5;
  word-wrap: break-word;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.message-bubble.sent {
  background: var(--color-primary);
  color: white;
  border-bottom-right-radius: 4px;
}

.message-bubble.received {
  background: var(--color-bg-secondary);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
  border-bottom-left-radius: 4px;
}

.deleted-text {
  font-style: italic;
  opacity: 0.6;
}

.message-text {
  position: relative;
}

.edited-badge {
  font-size: 11px;
  opacity: 0.7;
  margin-left: 6px;
}

/* Media */
.media-content {
  margin: -4px;
}

.media-image {
  max-width: 320px;
  max-height: 320px;
  border-radius: 12px;
  display: block;
  cursor: pointer;
}

.media-placeholder {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--color-bg-hover);
  border-radius: 12px;
}

.media-icon {
  font-size: 32px;
}

.media-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.media-name {
  font-size: 14px;
  font-weight: 500;
}

.media-download,
.media-link {
  font-size: 13px;
  color: var(--color-primary);
  text-decoration: none;
}

.media-download:hover,
.media-link:hover {
  text-decoration: underline;
}

/* Reactions */
.message-reactions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.reaction-badge {
  min-width: 32px;
  height: 24px;
  padding: 0 8px;
  background: var(--color-bg-hover);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.reaction-badge:hover {
  background: var(--color-border);
  transform: scale(1.1);
}

.reaction-badge.own {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
}

/* Message Footer */
.message-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.message-time {
  flex-shrink: 0;
}

.message-seen {
  font-size: 11px;
  color: var(--color-primary);
}

.message-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.message:hover .message-actions {
  opacity: 1;
}

.action-btn {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: none;
  background: var(--color-bg-hover);
  color: var(--color-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.action-btn:hover {
  background: var(--color-border);
  color: var(--color-text-primary);
}

.action-btn.delete:hover {
  background: var(--color-error);
  color: white;
}

/* Reaction Picker */
.reaction-picker {
  display: flex;
  gap: 6px;
  padding: 8px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  margin-top: 4px;
}

.reaction-option {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 20px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.reaction-option:hover {
  background: var(--color-bg-hover);
  transform: scale(1.2);
}

/* Edit Mode */
.message-edit {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.edit-input {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 15px;
  outline: none;
}

.edit-input:focus {
  border-color: var(--color-primary);
}

.edit-actions {
  display: flex;
  gap: 8px;
}

.edit-btn {
  padding: 6px 16px;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.edit-btn.save {
  background: var(--color-primary);
  color: white;
}

.edit-btn.save:hover {
  background: var(--color-primary-dark);
}

.edit-btn.cancel {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.edit-btn.cancel:hover {
  background: var(--color-border);
}

/* Input Container */
.input-container {
  padding: 16px 24px;
  background: var(--color-bg-secondary);
  border-top: 1px solid var(--color-border);
}

.input-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #FEF2F2;
  color: var(--color-error);
  border-radius: 10px;
  font-size: 14px;
  margin-bottom: 12px;
}

.reply-preview {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: var(--color-bg-hover);
  border-radius: 10px;
  margin-bottom: 12px;
}

.reply-info {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--color-text-secondary);
}

.reply-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.reply-to-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
}

.reply-to-content {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.reply-cancel {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.reply-cancel:hover {
  background: var(--color-bg-secondary);
  color: var(--color-text-primary);
}

.upload-preview {
  padding: 10px 14px;
  background: var(--color-primary-light);
  border-radius: 10px;
  margin-bottom: 12px;
}

.upload-info {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--color-primary-dark);
  font-size: 14px;
}

.upload-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--color-primary-light);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* Input Form */
.input-form {
  display: flex;
  align-items: center;
  gap: 10px;
}

.input-btn {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  border: none;
  background: var(--color-bg-hover);
  color: var(--color-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.input-btn:hover:not(:disabled) {
  background: var(--color-border);
  color: var(--color-text-primary);
}

.input-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.message-input {
  flex: 1;
  padding: 10px 16px;
  border: 1px solid var(--color-border);
  border-radius: 20px;
  font-size: 15px;
  background: var(--color-bg-primary);
  outline: none;
  transition: all 0.2s ease;
}

.message-input:focus {
  border-color: var(--color-primary);
  background: var(--color-bg-secondary);
}

.message-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.send-btn {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  border: none;
  background: var(--color-primary);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.send-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: scale(1.05);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
