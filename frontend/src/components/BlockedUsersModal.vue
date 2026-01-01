<template>
  <div v-if="isOpen" class="modal-overlay" @click="handleClose">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h2>Blocked Users</h2>
        <button @click="handleClose" class="close-btn">✕</button>
      </div>

      <div class="modal-body">
        <div v-if="blockingStore.isLoading" class="loading">
          Loading blocked users...
        </div>

        <div v-else-if="blockingStore.error" class="error">
          {{ blockingStore.error }}
        </div>

        <div v-else-if="blockingStore.blockedUsers.length === 0" class="empty">
          No blocked users
        </div>

        <div v-else class="blocked-users-list">
          <div
            v-for="user in blockingStore.blockedUsers"
            :key="user.userId"
            class="blocked-user-item"
          >
            <div class="user-avatar">
              <img
                v-if="user.avatarUrl"
                :src="user.avatarUrl"
                :alt="user.displayName"
              />
              <div v-else class="avatar-placeholder">
                {{ user.displayName?.charAt(0).toUpperCase() || 'U' }}
              </div>
            </div>

            <div class="user-info">
              <div class="user-name">{{ user.displayName || user.username }}</div>
              <div class="user-username">@{{ user.username }}</div>
              <div class="blocked-at">Blocked {{ formatDate(user.blockedAt) }}</div>
            </div>

            <button @click="handleUnblock(user.userId)" class="unblock-btn">
              Unblock
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { watch } from 'vue'
import { useBlockingStore } from '../stores/blocking'

const props = defineProps({
  isOpen: Boolean
})

const emit = defineEmits(['close'])

const blockingStore = useBlockingStore()

watch(() => props.isOpen, (newValue) => {
  if (newValue) {
    blockingStore.fetchBlockedUsers()
  }
})

function handleClose() {
  emit('close')
}

async function handleUnblock(userId) {
  try {
    await blockingStore.unblockUser(userId)
  } catch (err) {
    console.error('Failed to unblock user:', err)
  }
}

function formatDate(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const now = new Date()
  const diffInDays = Math.floor((now - date) / (1000 * 60 * 60 * 24))

  if (diffInDays === 0) return 'today'
  if (diffInDays === 1) return 'yesterday'
  if (diffInDays < 7) return `${diffInDays} days ago`
  return date.toLocaleDateString()
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 8px;
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  padding: 1rem;
  border-bottom: 1px solid #ddd;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header h2 {
  margin: 0;
  font-size: 1.25rem;
  color: #333;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #666;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  color: #333;
}

.modal-body {
  padding: 1rem;
  overflow-y: auto;
  flex: 1;
}

.loading,
.error,
.empty {
  text-align: center;
  padding: 2rem;
  color: #666;
}

.error {
  color: #c33;
}

.blocked-users-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.blocked-user-item {
  display: flex;
  align-items: center;
  padding: 1rem;
  border: 1px solid #ddd;
  border-radius: 8px;
  gap: 1rem;
}

.user-avatar {
  flex-shrink: 0;
}

.user-avatar img {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background-color: #999;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  font-weight: 500;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-weight: 500;
  color: #333;
  margin-bottom: 0.25rem;
}

.user-username {
  font-size: 0.875rem;
  color: #666;
  margin-bottom: 0.25rem;
}

.blocked-at {
  font-size: 0.75rem;
  color: #999;
}

.unblock-btn {
  padding: 0.5rem 1rem;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
  flex-shrink: 0;
}

.unblock-btn:hover {
  background-color: #45a049;
}
</style>
