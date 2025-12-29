<template>
  <div class="conversations-container">
    <div class="conversations-sidebar">
      <div class="sidebar-header">
        <h2>Conversations</h2>
        <button @click="handleLogout" class="logout-btn">Logout</button>
      </div>

      <div class="user-info">
        <div class="user-name">{{ authStore.user?.displayName }}</div>
        <div class="user-id">
          <span class="label">Your ID:</span>
          <code @click="copyUserId">{{ authStore.user?.id }}</code>
        </div>
        <div v-if="copied" class="copied-message">ID copied!</div>
      </div>

      <div class="create-conversation">
        <h3>New Conversation</h3>
        <form @submit.prevent="handleCreateConversation">
          <input
            v-model="newConversation.recipientUserId"
            type="text"
            placeholder="Enter User ID"
            required
          />
          <button type="submit" :disabled="isCreating">
            {{ isCreating ? 'Creating...' : 'Create' }}
          </button>
        </form>
        <div v-if="createError" class="error">{{ createError }}</div>
      </div>

      <div v-if="conversationsStore.isLoading" class="loading">
        Loading conversations...
      </div>

      <div v-else-if="conversationsStore.error" class="error">
        {{ conversationsStore.error }}
      </div>

      <div v-else class="conversation-list">
        <div
          v-for="conversation in conversationsStore.conversations"
          :key="conversation.id"
          class="conversation-item"
          :class="{ active: conversation.id === conversationsStore.activeConversationId }"
          @click="selectConversation(conversation.id)"
        >
          <div class="conversation-info">
            <div class="conversation-name">
              {{ conversation.name || 'Direct Message' }}
            </div>
            <div v-if="conversation.lastMessageContent" class="last-message">
              {{ conversation.lastMessageContent }}
            </div>
          </div>
          <div v-if="conversation.unreadCount > 0" class="unread-badge">
            {{ conversation.unreadCount }}
          </div>
        </div>

        <div v-if="conversationsStore.conversations.length === 0" class="no-conversations">
          No conversations yet. Create one above!
        </div>
      </div>
    </div>

    <div class="chat-area">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useConversationsStore } from '../stores/conversations'
import { useRealtimeStore } from '../stores/realtime'

const router = useRouter()
const authStore = useAuthStore()
const conversationsStore = useConversationsStore()
const realtimeStore = useRealtimeStore()

const newConversation = ref({
  recipientUserId: ''
})
const isCreating = ref(false)
const createError = ref(null)
const copied = ref(false)

onMounted(async () => {
  try {
    await conversationsStore.fetchConversations()
    realtimeStore.initializeSubscriptions()
  } catch (error) {
    console.error('Failed to load conversations:', error)
  }
})

function selectConversation(conversationId) {
  conversationsStore.setActiveConversation(conversationId)
  router.push(`/conversations/${conversationId}`)
}

async function handleCreateConversation() {
  if (!newConversation.value.recipientUserId.trim()) {
    createError.value = 'Please enter a user ID'
    return
  }

  isCreating.value = true
  createError.value = null

  try {
    const response = await conversationsStore.createConversation(
      'DIRECT',
      [newConversation.value.recipientUserId.trim()]
    )

    newConversation.value.recipientUserId = ''

    await conversationsStore.fetchConversations()

    selectConversation(response.id)
  } catch (error) {
    createError.value = error.response?.data?.message || 'Failed to create conversation'
  } finally {
    isCreating.value = false
  }
}

function copyUserId() {
  if (authStore.user?.id) {
    navigator.clipboard.writeText(authStore.user.id)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  }
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.conversations-container {
  display: flex;
  height: 100vh;
}

.conversations-sidebar {
  width: 300px;
  border-right: 1px solid #ddd;
  display: flex;
  flex-direction: column;
  background-color: #f9f9f9;
}

.sidebar-header {
  padding: 1rem;
  border-bottom: 1px solid #ddd;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-header h2 {
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
  cursor: pointer;
  font-size: 0.875rem;
}

.logout-btn:hover {
  background-color: #d32f2f;
}

.user-info {
  padding: 1rem;
  border-bottom: 1px solid #ddd;
  background-color: #f0f7ff;
}

.user-name {
  font-weight: 600;
  color: #333;
  margin-bottom: 0.5rem;
}

.user-id {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
}

.user-id .label {
  color: #666;
}

.user-id code {
  background-color: white;
  padding: 0.25rem 0.5rem;
  border-radius: 3px;
  border: 1px solid #ddd;
  font-size: 0.75rem;
  cursor: pointer;
  transition: background-color 0.2s;
}

.user-id code:hover {
  background-color: #e3f2fd;
}

.copied-message {
  margin-top: 0.5rem;
  font-size: 0.75rem;
  color: #4CAF50;
  font-weight: 500;
}

.create-conversation {
  padding: 1rem;
  border-bottom: 1px solid #ddd;
  background-color: white;
}

.create-conversation h3 {
  margin: 0 0 0.75rem 0;
  font-size: 0.875rem;
  color: #666;
  text-transform: uppercase;
}

.create-conversation form {
  display: flex;
  gap: 0.5rem;
}

.create-conversation input {
  flex: 1;
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.875rem;
}

.create-conversation button {
  padding: 0.5rem 1rem;
  background-color: #2196F3;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
}

.create-conversation button:hover {
  background-color: #1976D2;
}

.create-conversation button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.loading,
.error,
.no-conversations {
  padding: 1rem;
  text-align: center;
  color: #666;
}

.error {
  color: #c33;
  font-size: 0.875rem;
  margin-top: 0.5rem;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
}

.conversation-item {
  padding: 1rem;
  border-bottom: 1px solid #eee;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: background-color 0.2s;
}

.conversation-item:hover {
  background-color: #f0f0f0;
}

.conversation-item.active {
  background-color: #e3f2fd;
}

.conversation-info {
  flex: 1;
  min-width: 0;
}

.conversation-name {
  font-weight: 500;
  color: #333;
  margin-bottom: 0.25rem;
}

.last-message {
  font-size: 0.875rem;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.unread-badge {
  background-color: #4CAF50;
  color: white;
  border-radius: 12px;
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
  font-weight: bold;
  min-width: 20px;
  text-align: center;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: white;
}
</style>
