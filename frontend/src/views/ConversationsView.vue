<template>
  <div class="conversations-container">
    <div class="conversations-list">
      <div class="list-header">
        <h2>Conversations</h2>
        <button @click="handleLogout" class="logout-btn">Logout</button>
      </div>

      <div v-if="conversationsStore.isLoading" class="loading">
        Loading conversations...
      </div>

      <div v-else-if="conversationsStore.error" class="error">
        {{ conversationsStore.error }}
      </div>

      <div v-else-if="conversationsStore.conversations.length === 0" class="empty">
        No conversations yet
      </div>

      <div v-else class="conversation-items">
        <div
          v-for="conversation in conversationsStore.conversations"
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
          </div>

          <div class="conversation-info">
            <div class="conversation-header">
              <span class="conversation-name">{{ getConversationDisplayName(conversation) }}</span>
              <span v-if="conversation.lastMessageAt" class="conversation-time">
                {{ formatTime(conversation.lastMessageAt) }}
              </span>
            </div>

            <div class="conversation-preview">
              <span class="preview-text">{{ getLastMessagePreview(conversation) }}</span>
              <span v-if="conversation.unreadCount > 0" class="unread-badge">
                {{ conversation.unreadCount }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="conversation-detail">
      <div v-if="!conversationsStore.activeConversationId" class="empty-state">
        <p>Select a conversation to view</p>
      </div>

      <div v-else class="selected-conversation">
        <h3>Conversation Selected</h3>
        <p><strong>ID:</strong> {{ conversationsStore.activeConversationId }}</p>
        <p v-if="conversationsStore.activeConversation">
          <strong>Name:</strong> {{ getConversationDisplayName(conversationsStore.activeConversation) }}
        </p>
        <p v-if="conversationsStore.activeConversation">
          <strong>Type:</strong> {{ conversationsStore.activeConversation.type }}
        </p>
        <p v-if="conversationsStore.activeConversation">
          <strong>Members:</strong> {{ conversationsStore.activeConversation.members?.length || 0 }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useConversationsStore } from '../stores/conversations'
import websocketService from '../services/websocket'

const router = useRouter()
const authStore = useAuthStore()
const conversationsStore = useConversationsStore()

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

  if (diffInHours < 24) {
    return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
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

onMounted(async () => {
  await conversationsStore.fetchConversations()

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
  overflow: hidden;
}

.conversations-list {
  width: 350px;
  border-right: 1px solid #ddd;
  display: flex;
  flex-direction: column;
  background-color: white;
}

.list-header {
  padding: 1rem;
  border-bottom: 1px solid #ddd;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.list-header h2 {
  margin: 0;
  font-size: 1.25rem;
  color: #333;
}

.logout-btn {
  padding: 0.5rem 1rem;
  background-color: #f44336;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 0.875rem;
  cursor: pointer;
}

.logout-btn:hover {
  background-color: #d32f2f;
}

.loading,
.error,
.empty {
  padding: 2rem;
  text-align: center;
  color: #666;
}

.error {
  color: #c33;
}

.conversation-items {
  flex: 1;
  overflow-y: auto;
}

.conversation-item {
  display: flex;
  padding: 1rem;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background-color 0.2s;
}

.conversation-item:hover {
  background-color: #f5f5f5;
}

.conversation-item.active {
  background-color: #e3f2fd;
}

.conversation-avatar {
  flex-shrink: 0;
  margin-right: 1rem;
}

.conversation-avatar img {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background-color: #4CAF50;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  font-weight: 500;
}

.conversation-info {
  flex: 1;
  min-width: 0;
}

.conversation-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 0.25rem;
}

.conversation-name {
  font-weight: 500;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-time {
  font-size: 0.75rem;
  color: #999;
  flex-shrink: 0;
  margin-left: 0.5rem;
}

.conversation-preview {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.preview-text {
  font-size: 0.875rem;
  color: #666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.unread-badge {
  background-color: #4CAF50;
  color: white;
  border-radius: 12px;
  padding: 0.125rem 0.5rem;
  font-size: 0.75rem;
  font-weight: 500;
  margin-left: 0.5rem;
  flex-shrink: 0;
}

.conversation-detail {
  flex: 1;
  background-color: #fafafa;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-state {
  text-align: center;
  color: #999;
}

.empty-state p {
  font-size: 1.125rem;
}

.selected-conversation {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  max-width: 500px;
  width: 100%;
}

.selected-conversation h3 {
  margin-top: 0;
  margin-bottom: 1.5rem;
  color: #333;
}

.selected-conversation p {
  margin: 0.75rem 0;
  color: #666;
}

.selected-conversation strong {
  color: #333;
}
</style>
