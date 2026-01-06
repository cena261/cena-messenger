<template>
  <div v-if="!conversationsStore.activeConversationId" class="no-conversation">
    <svg width="120" height="120" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
      <path d="M9 10h6M9 14h4"/>
    </svg>
    <h2>Chào mừng đến với Cena</h2>
    <p>Chọn một cuộc trò chuyện để bắt đầu nhắn tin</p>
  </div>

  <div v-else class="chat-view">
    <header class="chat-header">
      <div class="header-info">
        <div class="chat-avatar">
          <div v-if="conversationAvatar" class="avatar-image">
            <img :src="conversationAvatar" :alt="conversationName">
          </div>
          <div v-else class="avatar-placeholder">{{ conversationName.charAt(0) }}</div>
          <div v-if="isOnline" class="online-indicator"></div>
        </div>
        <div class="chat-details">
          <h2 class="chat-name">{{ conversationName }}</h2>
          <p v-if="typingUsersDisplay.length > 0" class="chat-status typing">
            <span class="typing-dots">
              <span class="dot"></span>
              <span class="dot"></span>
              <span class="dot"></span>
            </span>
            <span v-if="typingUsersDisplay.length === 1">
              {{ typingUsersDisplay[0].displayName }} đang nhập...
            </span>
            <span v-else>
              {{ typingUsersDisplay.length }} người đang nhập...
            </span>
          </p>
          <p v-else-if="conversationsStore.activeConversation?.type === 'GROUP'" class="chat-status">
            {{ getMemberCount() }} thành viên
          </p>
        </div>
      </div>
      <div class="header-actions">
        <button @click="toggleMessageSearch" class="header-btn" title="Tìm tin nhắn">
          <Search :size="20" />
        </button>
        <button
          v-if="conversationsStore.activeConversation?.type === 'GROUP'"
          @click="openGroupManagement"
          class="header-btn"
          title="Thông tin nhóm"
        >
          <Info :size="20" />
        </button>
      </div>
    </header>

    <div v-if="isSearching" class="message-search-bar">
      <div class="search-input-wrapper">
        <Search :size="18" />
        <input 
          v-model="searchQuery"
          @input="handleSearch"
          @keydown.enter="nextResult"
          @keydown.shift.enter.prevent="previousResult"
          placeholder="Tìm kiếm tin nhắn..."
          class="search-input"
          ref="searchInput"
        />
        <button v-if="searchQuery" @click="clearMessageSearch" class="clear-btn">
          <X :size="16" />
        </button>
      </div>
      
      <div v-if="searchResults.length > 0" class="search-navigation">
        <span class="search-count">{{ currentResultIndex + 1}}/{{ searchResults.length }}</span>
        <button @click="nextResult" :disabled="currentResultIndex === searchResults.length - 1" class="nav-btn">
          <ChevronUp :size="18" />
        </button>
        <button @click="previousResult" :disabled="currentResultIndex === 0" class="nav-btn">
          <ChevronDown :size="18" />
        </button>
      </div>
      
      <button @click="closeMessageSearch" class="close-search-btn">
        <X :size="20" />
      </button>
    </div>

    <div class="messages-container custom-scrollbar" ref="messagesContainer">
      <div v-if="messagesStore.isLoading" class="messages-loading">
        <div class="spinner"></div>
      </div>

      <div v-else-if="messages.length === 0" class="messages-empty">
        <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <p>Chưa có tin nhắn. Hãy bắt đầu cuộc trò chuyện!</p>
      </div>

      <div v-else class="messages-list">
        <div class="date-separator">
          <div class="date-label">Hôm nay</div>
        </div>

        <div 
          v-for="(message, index) in messages" 
          :key="message.id" 
          :id="`message-${message.id}`"
          :class="['message-wrapper', { 'highlighted': highlightedMessageId === message.id }]"
        >
          <div :class="['message', message.senderId === authStore.user?.id ? 'own' : 'other']">
            <div v-if="message.senderId !== authStore.user?.id && shouldShowAvatar(index)" class="message-avatar">
              <div class="avatar-small">{{ getUserInitial(message.senderId) }}</div>
            </div>
            <div v-else-if="message.senderId !== authStore.user?.id" class="message-avatar-spacer"></div>

            <div class="message-content-wrapper">
              <div
                v-if="message.senderId !== authStore.user?.id && conversationsStore.activeConversation?.type === 'GROUP' && shouldShowAvatar(index)"
                class="message-sender-name"
              >
                {{ message.senderDisplayName || message.senderUsername }}
              </div>

              <div v-if="message.replyTo" class="message-reply-context">
                <div class="reply-bar"></div>
                <div class="reply-content">
                  <span class="reply-to">{{ getReplyToUsername(message.replyTo) }}</span>
                  <span class="reply-preview">{{ getReplyToPreview(message.replyTo) }}</span>
                </div>
              </div>

              <div v-if="editingMessageId === message.id" class="message-edit">
                <input
                  v-model="editContent"
                  @keyup.enter="saveEdit"
                  @keyup.escape="cancelEdit"
                  placeholder="Chỉnh sửa tin nhắn..."
                  class="edit-input"
                />
                <div class="edit-actions">
                  <button @click="saveEdit" class="edit-btn save">Lưu</button>
                  <button @click="cancelEdit" class="edit-btn cancel">Hủy</button>
                </div>
              </div>

              <div v-else :class="['message-bubble', message.senderId === authStore.user?.id ? 'sent' : 'received']">
                <span v-if="isMessageDeleted(message)" class="deleted-text">Tin nhắn đã bị xóa</span>

                <div v-else-if="message.type === 'IMAGE'" class="media-content">
                  <img :src="message.mediaUrl" :alt="message.mediaMetadata?.fileName" class="media-image" />
                </div>

                <div v-else class="message-text">
                  {{ message.content }}
                  <span v-if="isEdited(message)" class="edited-badge">đã chỉnh sửa</span>
                </div>

                <div v-if="!isMessageDeleted(message) && message.reactions && Object.keys(message.reactions).length > 0" class="message-reactions">
                  <button
                    v-for="(emoji, userId) in message.reactions"
                    :key="userId"
                    :class="['reaction-badge', { own: userId === authStore.user?.id }]"
                    @click="handleToggleReaction(message.id, emoji)"
                    :title="getUserDisplayName(userId)"
                  >
                    {{ emoji }}
                  </button>
                </div>
              </div>

              <div class="message-meta">
                <span class="message-time">{{ formatTime(message.createdAt) }}</span>

                <span
                  v-if="message.senderId === authStore.user?.id && !isMessageDeleted(message)"
                  class="message-seen"
                >
                  <svg v-if="getSeenByUsers(message).length > 0" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                    <polyline points="22 4 12 14.01 9 11.01"/>
                  </svg>
                  {{ formatSeenIndicator(getSeenByUsers(message)) }}
                </span>

                <div class="message-actions">
                  <button
                    v-if="!isMessageDeleted(message)"
                    @click="startReply(message)"
                    class="action-btn"
                    title="Trả lời"
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
                    title="Thả cảm xúc"
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
                    title="Chỉnh sửa"
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
                    title="Xóa"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                    </svg>
                  </button>
                </div>
              </div>

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

        <div v-if="typingUsersDisplay.length > 0" class="typing-indicator-message">
          <div class="message other">
            <div class="message-avatar">
              <div class="avatar-small">{{ typingUsersDisplay[0].displayName.charAt(0) }}</div>
            </div>
            <div class="message-content-wrapper">
              <div class="typing-bubble">
                <span class="typing-dot"></span>
                <span class="typing-dot"></span>
                <span class="typing-dot"></span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="input-container">
      <div v-if="sendError" class="input-error">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        {{ sendError }}
      </div>

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

      <form @submit.prevent="sendMessageHandler" class="input-form">
        <input
          type="file"
          ref="fileInput"
          @change="handleFileSelect"
          style="display: none"
          accept="image/*"
        />

        <button
          type="button"
          @click="openFileDialog"
          class="input-btn"
          :disabled="!authStore.isAuthenticated"
          title="Đính kèm file"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="16"/>
            <line x1="8" y1="12" x2="16" y2="12"/>
          </svg>
        </button>

        <div class="input-wrapper">
          <textarea
            v-model="newMessage"
            ref="messageInput"
            @input="handleTyping"
            @keydown.enter.exact.prevent="sendMessageHandler"
            :disabled="isSendingMessage || !authStore.isAuthenticated"
            placeholder="Nhập tin nhắn..."
            class="message-input custom-scrollbar"
            rows="1"
          ></textarea>
        </div>

        <button
          type="submit"
          :disabled="!newMessage.trim() || isSendingMessage || !authStore.isAuthenticated"
          class="send-btn"
          title="Gửi"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
          </svg>
        </button>
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
import * as conversationsApi from '../api/conversations'
import * as messagesApi from '../api/messages'
import GroupManagementModal from '../components/GroupManagementModal.vue'
import { Info, Search, X, ChevronUp, ChevronDown } from 'lucide-vue-next'

const authStore = useAuthStore()
const conversationsStore = useConversationsStore()
const messagesStore = useMessagesStore()
const realtimeStore = useRealtimeStore()

const newMessage = ref('')
const messagesContainer = ref(null)
const messageInput = ref(null)
const editingMessageId = ref(null)
const editContent = ref('')
const originalEditContent = ref('')
const replyingTo = ref(null)
const fileInput = ref(null)
const isGroupManagementOpen = ref(false)
const showReactionPicker = ref(null)

const isSearching = ref(false)
const searchQuery = ref('')
const searchResults = ref([])
const currentResultIndex = ref(0)
const highlightedMessageId = ref(null)
const searchInput = ref(null)
const availableReactions = ['👍', '❤️', '😂', '😮', '😢', '🙏']
const typingTimeout = ref(null)
const isTyping = ref(false)
const isSendingMessage = ref(false)
const sendError = ref(null)

const conversationName = computed(() => {
  const conversation = conversationsStore.activeConversation
  if (!conversation) return 'Cuộc trò chuyện'

  if (conversation.type === 'GROUP') {
    return conversation.name || 'Nhóm không tên'
  }

  const otherMember = conversation.members?.find(m => m.userId !== authStore.user?.id)
  return otherMember?.displayName || otherMember?.username || 'Tin nhắn trực tiếp'
})

const conversationAvatar = computed(() => {
  const conversation = conversationsStore.activeConversation
  if (!conversation) return null
  return conversation.avatarUrl || null
})

const isOnline = computed(() => {
  return false
})

const messages = computed(() => {
  return messagesStore.getMessages(conversationsStore.activeConversationId)
})

const typingUsersDisplay = computed(() => {
  if (!conversationsStore.activeConversationId) return []
  return realtimeStore.getTypingUsersDisplay(conversationsStore.activeConversationId)
})

function getMemberCount() {
  const conversation = conversationsStore.activeConversation
  return conversation?.members?.length || 0
}

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
  if (!conversation) return 'Không rõ'

  const member = conversation.members?.find(m => m.userId === userId)
  return member?.displayName || member?.username || 'Không rõ'
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
    return `Đã xem`
  } else {
    return `Đã xem bởi ${seenByUsers.length}`
  }
}

let scrollPending = false

function requestScrollCheck() {
  if (scrollPending) return
  scrollPending = true
  requestAnimationFrame(() => {
    scrollPending = false
    scrollToBottomIfNearBottom()
  })
}

watch(() => conversationsStore.activeConversationId, async (newId, oldId) => {
  if (oldId) {
    handleStopTyping()
    if (typingTimeout.value) {
      clearTimeout(typingTimeout.value)
      typingTimeout.value = null
    }
    realtimeStore.unsubscribeFromConversation(oldId)
  }

  if (newId) {
    cancelEdit()
    cancelReply()
    showReactionPicker.value = null
    sendError.value = null
    closeMessageSearch()
    isSearching.value = false
    await loadMessages()
    realtimeStore.subscribeToConversation(newId)
    await nextTick()
    scrollToBottom()
  }
}, { immediate: true })

watch(() => messages.value.length, () => {
  nextTick(requestScrollCheck)
})

watch(() => typingUsersDisplay.value.length, () => {
  if (typingUsersDisplay.value.length > 0) {
    nextTick(requestScrollCheck)
  }
})

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
  if (typingTimeout.value) {
    clearTimeout(typingTimeout.value)
    typingTimeout.value = null
  }
  cleanupSearch()
  if (conversationsStore.activeConversationId) {
    realtimeStore.unsubscribeFromConversation(conversationsStore.activeConversationId)
    realtimeStore.clearTypingUsers(conversationsStore.activeConversationId)
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
    if (messageInput.value) {
      messageInput.value.style.height = 'auto'
    }
    cancelReply()
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error('Failed to send message:', error)
    sendError.value = error.response?.data?.message || 'Gửi tin nhắn thất bại. Vui lòng thử lại.'
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
  const confirmed = confirm('Bạn có chắc chắn muốn xóa tin nhắn này?')
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
  return msg?.senderDisplayName || msg?.senderUsername || 'Không rõ'
}

function getReplyToPreview(replyToId) {
  const msg = messages.value.find(m => m.id === replyToId)
  if (!msg) return ''
  if (isMessageDeleted(msg)) return 'Tin nhắn đã xóa'
  return msg.content?.substring(0, 30) || ''
}

function isEdited(message) {
  if (!message.updatedAt || !message.createdAt) return false

  const created = new Date(message.createdAt).getTime()
  const updated = new Date(message.updatedAt).getTime()

  return updated - created > 1000
}

function isNearBottom() {
  const container = messagesContainer.value
  if (!container) return true
  const threshold = 150
  const scrollTop = container.scrollTop
  const scrollHeight = container.scrollHeight
  const clientHeight = container.clientHeight
  return scrollHeight - scrollTop - clientHeight < threshold
}

function scrollToBottom() {
  requestAnimationFrame(() => {
    const container = messagesContainer.value
    if (container) {
      container.scrollTop = container.scrollHeight
    }
  })
}

function scrollToBottomIfNearBottom() {
  if (isNearBottom()) {
    scrollToBottom()
  }
}

function formatTime(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleTimeString('vi-VN', { hour: 'numeric', minute: '2-digit' })
}

function openFileDialog() {
  fileInput.value?.click()
}

async function handleFileSelect(event) {
  const file = event.target.files?.[0]
  if (!file) return

  try {
    const mediaType = file.type.startsWith('image/') ? 'IMAGE' : 'MEDIA'

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
    alert('Gửi file thất bại. Vui lòng thử lại.')
  } finally {
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

  autoResizeTextarea()

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

function autoResizeTextarea() {
  if (!messageInput.value) return
  
  messageInput.value.style.height = 'auto'
  messageInput.value.style.height = messageInput.value.scrollHeight + 'px'
}

function sendTypingIndicator() {
  if (!realtimeStore.stompClient || !conversationsStore.activeConversationId) return

  if (isTyping.value) {
    realtimeStore.stompClient.send(
      '/app/typing',
      {},
      JSON.stringify({ conversationId: conversationsStore.activeConversationId })
    )
  }
}

function toggleMessageSearch() {
  isSearching.value = !isSearching.value
  if (isSearching.value) {
    nextTick(() => {
      searchInput.value?.focus()
    })
  } else {
    searchQuery.value = ''
    searchResults.value = []
    highlightedMessageId.value = null
  }
}

let searchDebounce = null
async function handleSearch() {
  if (searchDebounce) clearTimeout(searchDebounce)
  
  if (!searchQuery.value.trim() || searchQuery.value.length < 2) {
    searchResults.value = []
    return
  }

  if (!conversationsStore.activeConversationId) {
    console.error('No active conversation')
    return
  }

  searchDebounce = setTimeout(async () => {
    try {
      const response = await messagesApi.searchMessages(
        conversationsStore.activeConversationId,
        searchQuery.value
      )
      searchResults.value = response.data.messages || []
      currentResultIndex.value = 0
      
      if (searchResults.value.length > 0) {
        scrollToMessage(searchResults.value[0].id)
      }
    } catch (error) {
      console.error('Search failed:', error)
      searchResults.value = []
    }
  }, 300)
}

function cleanupSearch() {
  if (searchDebounce) {
    clearTimeout(searchDebounce)
    searchDebounce = null
  }
  searchQuery.value = ''
  searchResults.value = []
  currentResultIndex.value = 0
  highlightedMessageId.value = null
}

function nextResult() {
  if (currentResultIndex.value < searchResults.value.length - 1) {
    currentResultIndex.value++
    scrollToMessage(searchResults.value[currentResultIndex.value].id)
  }
}

function previousResult() {
  if (currentResultIndex.value > 0) {
    currentResultIndex.value--
    scrollToMessage(searchResults.value[currentResultIndex.value].id)
  }
}

function scrollToMessage(messageId) {
  highlightedMessageId.value = messageId
  
  nextTick(() => {
    const element = document.getElementById(`message-${messageId}`)
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'center' })
      
      setTimeout(() => {
        if (highlightedMessageId.value === messageId) {
          highlightedMessageId.value = null
        }
      }, 2000)
    }
  })
}

function clearMessageSearch() {
  searchQuery.value = ''
  searchResults.value = []
  highlightedMessageId.value = null
}

function closeMessageSearch() {
  isSearching.value = false
  searchQuery.value = ''
  searchResults.value = []
  highlightedMessageId.value = null
}

function handleStopTyping() {
  if (isTyping.value && conversationsStore.activeConversationId) {
    realtimeStore.sendTypingStop(conversationsStore.activeConversationId)
    isTyping.value = false
  }

  if (typingTimeout.value) {
    clearTimeout(typingTimeout.value)
    typingTimeout.value = null
  }
}
</script>

<style scoped>
.no-conversation {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  text-align: center;
  color: var(--color-text-secondary);
}

.no-conversation svg {
  margin-bottom: 24px;
  opacity: 0.3;
}

.no-conversation h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 8px;
}

.no-conversation p {
  font-size: 16px;
  color: var(--color-text-secondary);
}

.chat-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.chat-header {
  height: 80px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--color-surface);
  flex-shrink: 0;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.chat-avatar {
  position: relative;
  flex-shrink: 0;
}

.avatar-image,
.avatar-placeholder {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  overflow: hidden;
}

.avatar-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
}

.online-indicator {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 12px;
  height: 12px;
  background: #10b981;
  border: 2px solid var(--color-surface);
  border-radius: 50%;
}

.chat-details {
  flex: 1;
  min-width: 0;
}

.chat-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 4px 0;
}

.chat-status {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-status.typing {
  color: var(--color-primary);
  font-weight: 500;
}

.typing-dots {
  display: flex;
  gap: 3px;
}

.typing-dots .dot {
  width: 4px;
  height: 4px;
  background: var(--color-primary);
  border-radius: 50%;
  animation: typing-bounce 1.4s infinite;
}

.typing-dots .dot:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dots .dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing-bounce {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-6px);
  }
}

.header-actions {
  display: flex;
  gap: 8px;
}

.header-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.header-btn:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background: var(--color-background);
}

.messages-loading,
.messages-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
}

.messages-empty svg {
  margin-bottom: 16px;
  opacity: 0.3;
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  user-select: none;
}

.date-separator {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 16px 0;
}

.date-label {
  background: var(--color-date-separator-bg);
  padding: 6px 12px;
  border-radius: 16px;
  font-size: 11px;
  font-weight: 700;
  color: var(--color-date-separator-text);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border: 1px solid var(--color-date-separator-border);
  user-select: none;
}

.message-wrapper {
  display: flex;
  flex-direction: column;
}

.message {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.message.own {
  flex-direction: row-reverse;
}

.message-avatar,
.avatar-small {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
  user-select: none;
}

.message-avatar-spacer {
  width: 36px;
  flex-shrink: 0;
}

.message-content-wrapper {
  display: flex;
  flex-direction: column;
  max-width: 60%;
  gap: 4px;
}

.message.own .message-content-wrapper {
  align-items: flex-end;
}

.message-sender-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-left: 12px;
  user-select: none;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 18px;
  position: relative;
  word-wrap: break-word;
  user-select: none;
  transition: transform 120ms ease, box-shadow 120ms ease;
}

.message-bubble.received {
  background: var(--color-message-received-bg);
  color: var(--color-message-received-text);
  border: 1px solid var(--color-message-received-border);
  border-top-left-radius: 4px;
}

.message-bubble.sent {
  background: var(--color-message-sent-bg);
  color: var(--color-message-sent-text);
  border-top-right-radius: 4px;
}

.message-text {
  font-size: 15px;
  line-height: 1.5;
}

.deleted-text {
  font-style: italic;
  opacity: 0.6;
}

.edited-badge {
  font-size: 11px;
  opacity: 0.6;
  margin-left: 6px;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 0;
  margin-top: 4px;
}

.message.own .message-meta {
  margin-left: 0;
  margin-right: 0;
  flex-direction: row-reverse;
}

.message-time {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.message-seen {
  font-size: 11px;
  color: var(--color-text-tertiary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.message-seen svg {
  color: #10b981;
}

.message-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.message:hover .message-actions {
  opacity: 1;
}

.action-btn {
  padding: 4px;
  background: transparent;
  border: none;
  color: var(--color-text-secondary);
  cursor: pointer;
  border-radius: 4px;
  transition: all var(--transition-fast);
}

.action-btn:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}

.action-btn.delete:hover {
  color: var(--color-error);
}

.message-reply-context {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 8px;
  margin-bottom: 4px;
  font-size: 13px;
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

.message-reactions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
  flex-wrap: wrap;
  user-select: none;
}

.reaction-badge {
  padding: 2px 8px;
  background: var(--color-surface-hover);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  font-size: 14px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.reaction-badge:hover {
  transform: scale(1.1);
}

.reaction-badge.own {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
}

.reaction-picker {
  display: flex;
  gap: 4px;
  padding: 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: var(--shadow-md);
  margin-top: 8px;
}

.reaction-option {
  padding: 6px 10px;
  background: transparent;
  border: none;
  font-size: 20px;
  cursor: pointer;
  border-radius: 8px;
  transition: all var(--transition-fast);
}

.reaction-option:hover {
  background: var(--color-surface-hover);
  transform: scale(1.2);
}

.message-edit {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.edit-input {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 15px;
  background: var(--color-input-bg);
  color: var(--color-text-primary);
}

.edit-actions {
  display: flex;
  gap: 8px;
}

.edit-btn {
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.edit-btn.save {
  background: var(--color-primary);
  color: white;
}

.edit-btn.cancel {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}

.typing-indicator-message {
  animation: fadeIn 0.3s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.typing-bubble {
  padding: 12px 16px;
  background: var(--color-message-received-bg);
  border: 1px solid var(--color-message-received-border);
  border-radius: 18px;
  border-top-left-radius: 4px;
  display: flex;
  gap: 4px;
  align-items: center;
}

.typing-dot {
  width: 8px;
  height: 8px;
  background: var(--color-text-tertiary);
  border-radius: 50%;
  animation: typing-bounce 1.4s infinite;
}

.typing-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dot:nth-child(3) {
  animation-delay: 0.4s;
}

.input-container {
  padding: 20px 24px;
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}

.input-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: #fef2f2;
  color: var(--color-error);
  border-radius: 8px;
  margin-bottom: 12px;
  font-size: 14px;
}

.reply-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: var(--color-surface-hover);
  border-left: 3px solid var(--color-primary);
  border-radius: 8px;
  margin-bottom: 12px;
}

.reply-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.reply-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.reply-to-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
}

.reply-to-content {
  font-size: 13px;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reply-cancel {
  padding: 4px;
  background: transparent;
  border: none;
  color: var(--color-text-secondary);
  cursor: pointer;
  border-radius: 4px;
  transition: all var(--transition-fast);
}

.reply-cancel:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}

.input-form {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.input-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
  flex-shrink: 0;
}

.input-btn:hover:not(:disabled) {
  background: var(--color-surface-hover);
  color: var(--color-primary);
}

.input-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-wrapper {
  flex: 1;
  background: var(--color-input-bg);
  border: 1px solid var(--color-input-border);
  border-radius: 20px;
  padding: 8px 16px;
  transition: all var(--transition-fast);
}

.input-wrapper:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.message-input {
  width: 100%;
  border: none;
  background: transparent;
  color: var(--color-text-primary);
  font-size: 15px;
  resize: none;
  outline: none;
  max-height: 120px;
  min-height: 24px;
  overflow-y: auto;
  line-height: 1.5;
}

.message-input::placeholder {
  color: var(--color-text-tertiary);
}

.send-btn {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  border: none;
  background: var(--color-primary);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: scale(1.05);
}

.send-btn:active:not(:disabled) {
  transform: scale(0.95);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.media-content {
  max-width: 100%;
}

.message-bubble.sent .media-content,
.message-bubble.received .media-content {
  background: transparent;
  padding: 0;
  margin: 0;
}

.message-bubble.sent:has(.media-content),
.message-bubble.received:has(.media-content) {
  background: transparent;
  border: none;
  padding: 0;
}

.media-image {
  max-width: 400px;
  max-height: 400px;
  width: auto;
  height: auto;
  border-radius: 8px;
  cursor: pointer;
  display: block;
  object-fit: contain;
}

.message-wrapper.highlighted {
  background: var(--color-primary-light);
  border-radius: 8px;
  animation: pulse 0.6s ease;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.message-search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.search-input-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--color-input-bg);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  transition: all var(--transition-fast);
}

.search-input-wrapper:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.search-input-wrapper .search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--color-text-primary);
}

.search-input-wrapper .search-input::placeholder {
  color: var(--color-text-tertiary);
}

.search-input-wrapper .clear-btn {
  background: transparent;
  border: none;
  padding: 4px;
  cursor: pointer;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  border-radius: 4px;
  transition: all var(--transition-fast);
}

.search-input-wrapper .clear-btn:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}

.search-navigation {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-count {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  min-width: 50px;
  text-align: center;
}

.nav-btn {
  background: transparent;
  border: 1px solid var(--color-border);
  width: 32px;
  height: 32px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--color-text-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.nav-btn:hover:not(:disabled) {
  background: var(--color-surface-hover);
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.nav-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.close-search-btn {
  background: transparent;
  border: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  cursor: pointer;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.close-search-btn:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}

.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
</style>
