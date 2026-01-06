<template>
  <div v-if="isOpen" class="modal-overlay" @click="handleClose">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h2>Blocked Users</h2>
        <button @click="handleClose" class="close-btn">✕</button>
      </div>

      <div class="modal-body">
        <div v-if="blockingStore.isLoading" class="loading">
          <div class="spinner"></div>
          <p>Loading blocked users...</p>
        </div>

        <div v-else-if="blockingStore.error" class="error">
          {{ blockingStore.error }}
        </div>

        <div v-else-if="blockingStore.blockedUsers.length === 0" class="empty">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"/>
            <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/>
          </svg>
          <p>No blocked users</p>
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
  background: #FFFFFF;
  border-radius: 20px;
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

.modal-header {
  padding: 24px 24px 16px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.3px;
}

.close-btn {
  background: none;
  border: none;
  font-size: 28px;
  cursor: pointer;
  color: var(--color-text-tertiary);
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s ease;
}

.close-btn:hover {
  color: var(--color-text-primary);
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

.loading,
.error,
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px 24px;
  color: var(--color-text-secondary);
}

.loading .spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading p {
  margin: 0;
  font-size: 14px;
}

.error {
  color: var(--color-error);
}

.empty svg {
  margin-bottom: 16px;
  opacity: 0.3;
  color: var(--color-text-tertiary);
}

.empty p {
  margin: 0;
  font-size: 15px;
}

.blocked-users-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.blocked-user-item {
  display: flex;
  align-items: center;
  padding: 16px;
  border: 1.5px solid var(--color-border);
  border-radius: 16px;
  gap: 12px;
  transition: all 0.2s ease;
}

.blocked-user-item:hover {
  background: var(--color-bg-hover);
}

.user-avatar {
  flex-shrink: 0;
}

.user-avatar img {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  object-fit: cover;
}

.avatar-placeholder {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: var(--color-gradient-warm);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 600;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-weight: 600;
  font-size: 15px;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}

.user-username {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}

.blocked-at {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.unblock-btn {
  padding: 10px 20px;
  background-color: var(--color-primary);
  color: white;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.unblock-btn:hover {
  background-color: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(224, 120, 86, 0.3);
}

/* CSS Variables */
:root {
  --color-primary: #E07856;
  --color-primary-dark: #C96644;
  --color-primary-light: #FFF3EF;

  --color-bg-primary: #FAF8F5;
  --color-bg-hover: #F5F2EE;

  --color-text-primary: #2C2C2C;
  --color-text-secondary: #6B6B6B;
  --color-text-tertiary: #9B9B9B;

  --color-border: #E8E4DF;
  --color-error: #D64545;

  --color-gradient-warm: linear-gradient(135deg, #E07856 0%, #C96644 100%);
}
</style>
