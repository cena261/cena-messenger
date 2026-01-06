<template>
  <div class="conversations-container">
    <aside class="conversations-sidebar">
      <div class="sidebar-content">
        <div class="sidebar-header-section">
          <div class="sidebar-top">
            <div class="brand">
              <div class="brand-icon">
                <svg viewBox="0 0 48 48" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
                  <path d="M4 4H17.3334V17.3334H30.6666V30.6666H44V44H4V4Z"/>
                </svg>
              </div>
              <h2>Cena</h2>
            </div>
            <ThemeToggle v-model="currentTheme" @update:modelValue="handleThemeChange" />
          </div>

          <div class="action-buttons">
            <button @click="openModal" class="action-btn">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                <path d="M9 10h6M9 14h4"/>
              </svg>
              Đoạn chat mới
            </button>
            <button @click="openGroupModal" class="action-btn">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
              Nhóm chat mới
            </button>
          </div>

          <div class="search-box">
            <Search :size="20" class="search-icon" />
            <input 
              v-model="searchQuery"
              type="text" 
              placeholder="Tìm kiếm cuộc trò chuyện..."
              class="search-input"
            >
            <button v-if="searchQuery" @click="clearSearch" class="clear-search-btn">
              <X :size="16" />
            </button>
          </div>
        </div>

        <div v-if="conversationsStore.isLoading" class="loading-state">
          <div class="spinner"></div>
          <p>Đang tải...</p>
        </div>

        <div v-else-if="conversationsStore.error" class="error-state">
          <p>{{ conversationsStore.error }}</p>
        </div>

        <div v-else-if="conversationsStore.conversations.length === 0" class="empty-state">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <p>Chưa có cuộc trò chuyện</p>
        </div>

        <div v-else class="conversations-list custom-scrollbar">
          <div
            v-for="conversation in filteredConversations"
            :key="conversation.id"
            :class="['conversation-item', { active: conversation.id === conversationsStore.activeConversationId }]"
            @click="handleSelectConversation(conversation.id)"
          >
            <div class="conversation-avatar">
              <img
                v-if="conversation.avatarUrl"
                :src="conversation.avatarUrl"
                :alt="getConversationDisplayName(conversation)"
              />
              <div v-else class="avatar-placeholder">
                {{ getConversationInitial(conversation) }}
              </div>
              <div v-if="isConversationOnline(conversation)" class="online-dot"></div>
            </div>

            <div class="conversation-details">
              <div class="conversation-header-row">
                <h4 class="conversation-name">{{ getConversationDisplayName(conversation) }}</h4>
                <span v-if="conversation.lastMessageAt" class="conversation-time">
                  {{ formatTime(conversation.lastMessageAt) }}
                </span>
              </div>

              <div class="conversation-footer-row">
                <p class="conversation-preview">{{ getLastMessagePreview(conversation) }}</p>
                <span v-if="conversation.unreadCount > 0" class="unread-badge">
                  {{ conversation.unreadCount > 9 ? '9+' : conversation.unreadCount }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="sidebar-profile">
        <div class="profile-content" @click="openProfileModal">
          <div class="profile-avatar">
            <img
              v-if="authStore.user?.avatarUrl"
              :src="authStore.user.avatarUrl"
              :alt="authStore.user.displayName || authStore.user.username"
            />
            <div v-else class="avatar-placeholder">
              {{ getHeaderInitial() }}
            </div>
          </div>
          <div class="profile-info">
            <div class="profile-name">{{ authStore.user?.displayName || authStore.user?.username || 'User' }}</div>
            <div class="profile-status">Online</div>
          </div>
          <button class="profile-settings" title="Cài đặt">
            <Settings :size="20" />
          </button>
        </div>
      </div>
    </aside>

    <div class="chat-main">
      <ChatView />
    </div>

    <ProfileModal
      :isOpen="isProfileModalOpen"
      @close="closeProfileModal"
    />

    <NewConversationModal
      :isOpen="isModalOpen"
      @close="closeModal"
      @conversationCreated="handleConversationCreated"
    />

    <NewGroupModal
      :isOpen="isGroupModalOpen"
      @close="closeGroupModal"
      @groupCreated="handleConversationCreated"
    />

    <BlockedUsersModal
      :isOpen="isBlockedModalOpen"
      @close="closeBlockedModal"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useConversationsStore } from '../stores/conversations'
import { useBlockingStore } from '../stores/blocking'
import { initTheme, setTheme } from '../utils/theme'
import websocketService from '../services/websocket'
import ChatView from './ChatView.vue'
import ThemeToggle from '../components/ThemeToggle.vue'
import ProfileModal from '../components/ProfileModal.vue'
import NewConversationModal from '../components/NewConversationModal.vue'
import NewGroupModal from '../components/NewGroupModal.vue'
import BlockedUsersModal from '../components/BlockedUsersModal.vue'
import { Settings, Search, X } from 'lucide-vue-next'

const router = useRouter()
const authStore = useAuthStore()
const conversationsStore = useConversationsStore()
const blockingStore = useBlockingStore()

const currentTheme = ref('light')
const isModalOpen = ref(false)
const isGroupModalOpen = ref(false)
const isProfileModalOpen = ref(false)
const isBlockedModalOpen = ref(false)
const searchQuery = ref('')
let unreadSubscription = null
let seenSubscription = null

const filteredConversations = computed(() => {
  if (!searchQuery.value) {
    return conversationsStore.conversations
  }
  const query = searchQuery.value.toLowerCase()
  return conversationsStore.conversations.filter(conversation => {
    const displayName = getConversationDisplayName(conversation).toLowerCase()
    return displayName.includes(query)
  })
})

function getConversationDisplayName(conversation) {
  if (conversation.type === 'GROUP') {
    return conversation.name || 'Nhóm không tên'
  }

  const otherMember = conversation.members?.find(m => m.userId !== authStore.user?.id)
  return otherMember?.displayName || otherMember?.username || 'Người dùng'
}

function getConversationInitial(conversation) {
  const name = getConversationDisplayName(conversation)
  return name.charAt(0).toUpperCase()
}

function getLastMessagePreview(conversation) {
  if (!conversation.lastMessageAt) {
    return 'Chưa có tin nhắn'
  }
  return 'Tin nhắn mới...'
}

function isConversationOnline(conversation) {
  return false
}

function formatTime(timestamp) {
  const date = new Date(timestamp)
  const now = new Date()
  const diffInHours = (now - date) / (1000 * 60 * 60)

  if (diffInHours < 1) {
    const diffInMinutes = Math.floor((now - date) / (1000 * 60))
    return diffInMinutes < 1 ? 'Vừa xong' : `${diffInMinutes}m`
  } else if (diffInHours < 24) {
    return date.toLocaleTimeString('vi-VN', { hour: 'numeric', minute: '2-digit' })
  } else if (diffInHours < 48) {
    return 'Hôm qua'
  } else {
    return date.toLocaleDateString('vi-VN', { month: 'short', day: 'numeric' })
  }
}

async function handleSelectConversation(conversationId) {
  await conversationsStore.selectConversation(conversationId)
}

function handleThemeChange(newTheme) {
  currentTheme.value = newTheme
  setTheme(newTheme)
}

function getHeaderInitial() {
  const user = authStore.user
  if (!user) return 'U'
  const name = user.displayName || user.username || 'U'
  return name.charAt(0).toUpperCase()
}

function openProfileModal() {
  isProfileModalOpen.value = true
}

function closeProfileModal() {
  isProfileModalOpen.value = false
}

function openModal() {
  isModalOpen.value = true
}

function openGroupModal() {
  isGroupModalOpen.value = true
}

function closeModal() {
  isModalOpen.value = false
}

function closeGroupModal() {
  isGroupModalOpen.value = false
}

function openBlockedModal() {
  isBlockedModalOpen.value = true
}

function closeBlockedModal() {
  isBlockedModalOpen.value = false
}

function clearSearch() {
  searchQuery.value = ''
}

async function handleConversationCreated(conversation) {
  if (conversation && conversation.id) {
    await conversationsStore.selectConversation(conversation.id)
  }
}

onMounted(async () => {
  currentTheme.value = initTheme()
  
  await conversationsStore.fetchConversations()
  await blockingStore.fetchBlockedUsers()

  unreadSubscription = websocketService.subscribeToUnreadUpdates((unreadUpdate) => {
    conversationsStore.updateConversationUnreadCount(
      unreadUpdate.conversationId,
      unreadUpdate.unreadCount
    )
  })

  seenSubscription = websocketService.subscribeToSeenEvents((seenEvent) => {
    conversationsStore.handleSeenEvent(seenEvent)
  })
})

onUnmounted(() => {
  if (unreadSubscription) {
    websocketService.unsubscribe('/user/queue/unread')
  }
  if (seenSubscription) {
    websocketService.unsubscribe('/user/queue/seen')
  }
})
</script>

<style scoped>
.conversations-container {
  display: flex;
  height: 100vh;
  background: var(--color-background);
  overflow: hidden;
}

.conversations-sidebar {
  width: 380px;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  flex-shrink: 0;
}

.sidebar-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header-section {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  border-bottom: 1px solid var(--color-border);
}

.sidebar-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-icon {
  width: 32px;
  height: 32px;
  color: var(--color-primary);
}

.brand h2 {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.02em;
}

.action-buttons {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.action-btn:hover {
  background: var(--color-surface-hover);
  border-color: var(--color-border-dark);
}

.action-btn svg {
  color: var(--color-primary);
  flex-shrink: 0;
}

.search-box {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 12px;
  color: var(--color-text-tertiary);
  transition: color var(--transition-fast);
}

.search-input {
  width: 100%;
  padding: 12px 12px 12px 44px;
  background: var(--color-input-bg);
  border: 1px solid var(--color-input-border);
  border-radius: 12px;
  font-size: 14px;
  color: var(--color-text-primary);
  transition: all var(--transition-fast);
  cursor: pointer;
}

.search-input:focus, .search-input:hover {
  border-color: var(--color-primary);
  outline: none;
}

.search-box:focus-within .search-icon {
  color: var(--color-primary);
}

.search-input::placeholder {
  color: var(--color-text-tertiary);
}

.clear-search-btn {
  background: transparent;
  border: none;
  padding: 4px;
  cursor: pointer;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all var(--transition-fast);
}

.clear-search-btn:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}

.loading-state,
.error-state,
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  text-align: center;
  color: var(--color-text-secondary);
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

.error-state {
  color: var(--color-error);
}

.empty-state svg {
  margin-bottom: 16px;
  opacity: 0.3;
  color: var(--color-text-tertiary);
}

.conversations-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-bottom: 4px;
}

.conversation-item:hover {
  background: var(--color-surface-hover);
}

.conversation-item.active {
  background: var(--color-primary-light);
  border: 1px solid rgba(19, 91, 236, 0.2);
}

.conversation-avatar {
  position: relative;
  flex-shrink: 0;
}

.conversation-avatar img,
.avatar-placeholder {
  width: 48px;
  height: 48px;
  border-radius: 50%;
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

.online-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 14px;
  height: 14px;
  background: #10b981;
  border: 3px solid var(--color-surface);
  border-radius: 50%;
}

.conversation-details {
  flex: 1;
  min-width: 0;
}

.conversation-header-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 4px;
}

.conversation-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin: 0;
}

.conversation-time {
  font-size: 10px;
  font-weight: 700;
  color: var(--color-text-tertiary);
  flex-shrink: 0;
  margin-left: 8px;
}

.conversation-item.active .conversation-time {
  color: var(--color-primary);
}

.conversation-footer-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.conversation-preview {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.conversation-item.active .conversation-preview {
  color: var(--color-text-primary);
}

.unread-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 6px;
  background: var(--color-primary);
  color: white;
  border-radius: 9px;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sidebar-profile {
  background: var(--color-surface);
  flex-shrink: 0;
}

.profile-content {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.profile-content:hover {
  background: var(--color-surface-hover);
}

.profile-avatar img,
.profile-avatar .avatar-placeholder {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
}

.profile-avatar .avatar-placeholder {
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 2px;
}

.profile-status {
  font-size: 12px;
  color: #10b981;
  font-weight: 500;
}

.profile-settings {
  background: transparent;
  border: none;
  padding: 8px;
  border-radius: 8px;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-center: center;
  transition: all var(--transition-fast);
}

.profile-settings:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-background);
}
</style>
