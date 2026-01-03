<template>
  <div class="conversations-container">
    <!-- Sidebar -->
    <div class="conversations-sidebar">
      <div class="sidebar-header">
        <h1 class="app-title">Chats</h1>
        <div class="header-actions">
          <button @click="openProfileModal" class="icon-btn" title="Profile Settings">
            <div v-if="authStore.user?.avatarUrl" class="header-avatar">
              <img :src="authStore.user.avatarUrl" :alt="authStore.user.displayName || authStore.user.username" />
            </div>
            <div v-else class="header-avatar-placeholder">
              {{ getHeaderInitial() }}
            </div>
          </button>
          <button @click="openSearchModal" class="icon-btn" title="Search">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <path d="m21 21-4.35-4.35"/>
            </svg>
          </button>
          <button @click="openModal" class="icon-btn primary" title="New Chat">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 5v14M5 12h14"/>
            </svg>
          </button>
        </div>
      </div>

      <div class="sidebar-tabs">
        <button class="tab active">All</button>
        <button @click="openBlockedUsersModal" class="tab">Blocked</button>
      </div>

      <div v-if="conversationsStore.isLoading" class="loading-state">
        <div class="spinner"></div>
        <p>Loading conversations...</p>
      </div>

      <div v-else-if="conversationsStore.error" class="error-state">
        <p>{{ conversationsStore.error }}</p>
      </div>

      <div v-else-if="conversationsStore.conversations.length === 0" class="empty-state">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <p>No conversations yet</p>
        <button @click="openModal" class="btn-primary">Start chatting</button>
      </div>

      <div v-else class="conversations-list">
        <div
          v-for="conversation in conversationsStore.conversations"
          :key="conversation.id"
          :class="['conversation-card', { active: conversation.id === conversationsStore.activeConversationId }]"
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
            <div v-if="conversation.type === 'GROUP'" class="group-badge">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </div>
          </div>

          <div class="conversation-content">
            <div class="conversation-header">
              <h3 class="conversation-name">{{ getConversationDisplayName(conversation) }}</h3>
              <span v-if="conversation.lastMessageAt" class="conversation-time">
                {{ formatTime(conversation.lastMessageAt) }}
              </span>
            </div>

            <div class="conversation-footer">
              <p class="conversation-preview">{{ getLastMessagePreview(conversation) }}</p>
              <span v-if="conversation.unreadCount > 0" class="unread-badge">
                {{ conversation.unreadCount > 99 ? '99+' : conversation.unreadCount }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Chat Area -->
    <div class="chat-main">
      <ChatView />
    </div>

    <!-- Modals -->
    <ProfileModal
      :isOpen="isProfileModalOpen"
      @close="closeProfileModal"
    />

    <NewConversationModal
      :isOpen="isModalOpen"
      @close="closeModal"
      @conversationCreated="handleConversationCreated"
    />

    <BlockedUsersModal
      :isOpen="isBlockedUsersModalOpen"
      @close="closeBlockedUsersModal"
    />

    <SearchModal
      :isOpen="isSearchModalOpen"
      @close="closeSearchModal"
      @selectConversation="handleSearchSelectConversation"
      @selectMessage="handleSearchSelectMessage"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useConversationsStore } from '../stores/conversations'
import { useBlockingStore } from '../stores/blocking'
import websocketService from '../services/websocket'
import ChatView from './ChatView.vue'
import ProfileModal from '../components/ProfileModal.vue'
import NewConversationModal from '../components/NewConversationModal.vue'
import BlockedUsersModal from '../components/BlockedUsersModal.vue'
import SearchModal from '../components/SearchModal.vue'

const router = useRouter()
const authStore = useAuthStore()
const conversationsStore = useConversationsStore()
const blockingStore = useBlockingStore()

const isProfileModalOpen = ref(false)
const isModalOpen = ref(false)
const isBlockedUsersModalOpen = ref(false)
const isSearchModalOpen = ref(false)
let unreadSubscription = null
let seenSubscription = null

function getConversationDisplayName(conversation) {
  if (conversation.type === 'GROUP') {
    return conversation.name || 'Unnamed Group'
  }

  const otherMember = conversation.members?.find(m => m.userId !== authStore.user?.id)
  return otherMember?.displayName || otherMember?.username || 'Unknown User'
}

function getConversationInitial(conversation) {
  const name = getConversationDisplayName(conversation)
  return name.charAt(0).toUpperCase()
}

function getLastMessagePreview(conversation) {
  if (!conversation.lastMessageAt) {
    return 'No messages yet'
  }
  return 'Last message...'
}

function formatTime(timestamp) {
  const date = new Date(timestamp)
  const now = new Date()
  const diffInHours = (now - date) / (1000 * 60 * 60)

  if (diffInHours < 1) {
    const diffInMinutes = Math.floor((now - date) / (1000 * 60))
    return diffInMinutes < 1 ? 'Just now' : `${diffInMinutes}m ago`
  } else if (diffInHours < 24) {
    return date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
  } else if (diffInHours < 48) {
    return 'Yesterday'
  } else {
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  }
}

async function handleSelectConversation(conversationId) {
  await conversationsStore.selectConversation(conversationId)
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
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

function closeModal() {
  isModalOpen.value = false
}

function openBlockedUsersModal() {
  isBlockedUsersModalOpen.value = true
}

function closeBlockedUsersModal() {
  isBlockedUsersModalOpen.value = false
}

function openSearchModal() {
  isSearchModalOpen.value = true
}

function closeSearchModal() {
  isSearchModalOpen.value = false
}

async function handleSearchSelectConversation(conversationId) {
  await conversationsStore.selectConversation(conversationId)
}

async function handleSearchSelectMessage(message) {
  await conversationsStore.selectConversation(message.conversationId)
}

async function handleConversationCreated(conversation) {
  if (conversation && conversation.id) {
    await conversationsStore.selectConversation(conversation.id)
  }
}

onMounted(async () => {
  await conversationsStore.fetchConversations()
  await blockingStore.fetchBlockedUsers()

  unreadSubscription = websocketService.subscribeToUnreadUpdates((unreadUpdate) => {
    console.log('Unread update received:', unreadUpdate)
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
  background: var(--color-bg-primary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', sans-serif;
  overflow: hidden;
}

/* Sidebar */
.conversations-sidebar {
  width: 360px;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-secondary);
  border-right: 1px solid var(--color-border);
}

.sidebar-header {
  padding: 24px 20px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
}

.app-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0;
  letter-spacing: -0.5px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.icon-btn:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.icon-btn.primary {
  background: var(--color-primary);
  color: white;
}

.icon-btn.primary:hover {
  background: var(--color-primary-dark);
  transform: scale(1.05);
}

.header-avatar,
.header-avatar-placeholder {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
}

.header-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.header-avatar-placeholder {
  background: var(--color-gradient-warm);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.sidebar-tabs {
  display: flex;
  padding: 12px 20px;
  gap: 8px;
  border-bottom: 1px solid var(--color-border);
}

.tab {
  padding: 8px 16px;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.tab.active {
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
}

.tab:hover:not(.active) {
  background: var(--color-bg-hover);
}

/* States */
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
}

.empty-state p {
  margin-bottom: 16px;
  font-size: 15px;
}

.btn-primary {
  padding: 10px 24px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-primary:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}

/* Conversations List */
.conversations-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversations-list::-webkit-scrollbar {
  width: 6px;
}

.conversations-list::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 3px;
}

.conversation-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: 4px;
}

.conversation-card:hover {
  background: var(--color-bg-hover);
}

.conversation-card.active {
  background: var(--color-primary-light);
}

.conversation-avatar {
  position: relative;
  flex-shrink: 0;
}

.conversation-avatar img,
.avatar-placeholder {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  object-fit: cover;
}

.avatar-placeholder {
  background: var(--color-gradient-warm);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 600;
}

.group-badge {
  position: absolute;
  bottom: -2px;
  right: -2px;
  width: 20px;
  height: 20px;
  background: var(--color-accent);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  border: 2px solid var(--color-bg-secondary);
}

.conversation-content {
  flex: 1;
  min-width: 0;
}

.conversation-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;
}

.conversation-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
  flex-shrink: 0;
  margin-left: 8px;
}

.conversation-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.conversation-preview {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.unread-badge {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  background: var(--color-primary);
  color: white;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* Main Chat Area */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-bg-primary);
}

/* CSS Variables */
:root {
  --color-primary: #E07856;
  --color-primary-dark: #C96644;
  --color-primary-light: #FFF3EF;

  --color-accent: #7C9885;
  --color-accent-light: #EFF4F0;

  --color-bg-primary: #FAF8F5;
  --color-bg-secondary: #FFFFFF;
  --color-bg-hover: #F5F2EE;

  --color-text-primary: #2C2C2C;
  --color-text-secondary: #6B6B6B;
  --color-text-tertiary: #9B9B9B;

  --color-border: #E8E4DF;
  --color-error: #D64545;

  --color-gradient-warm: linear-gradient(135deg, #E07856 0%, #C96644 100%);
}
</style>
