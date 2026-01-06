<template>
  <div v-if="isOpen" class="modal-overlay" @click="handleOverlayClick">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h2>Đoạn chat mới</h2>
        <button class="close-btn" @click="handleClose">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>

      <div class="modal-body">
        <div v-if="error" class="error-message">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          {{ error }}
        </div>

        <div class="search-section">
          <label class="search-label">Tìm người để nhắn tin</label>
          <div class="search-input-wrapper">
            <svg class="search-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <path d="m21 21-4.35-4.35"/>
            </svg>
            <input
              v-model="searchQuery"
              type="text"
              placeholder="Nhập tên, username, email hoặc số điện thoại..."
              @input="handleSearch"
              class="search-input"
              autofocus
            />
          </div>
        </div>

        <div v-if="isSearching" class="loading-state">
          <div class="spinner"></div>
          <p>Đang tìm kiếm...</p>
        </div>

        <div v-else-if="searchResult" class="search-result">
          <div class="result-card">
            <div class="user-avatar">
              {{ searchResult.displayName?.charAt(0) || searchResult.username?.charAt(0) }}
            </div>
            <div class="user-info">
              <div class="user-name">{{ searchResult.displayName || searchResult.username }}</div>
              <div class="user-username">@{{ searchResult.username }}</div>
            </div>
            <div class="user-badge">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
            </div>
          </div>
        </div>

        <div v-else-if="searchQuery && !isSearching" class="no-results">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="11" cy="11" r="8"/>
            <path d="m21 21-4.35-4.35"/>
          </svg>
          <p>Không tìm thấy người dùng</p>
          <span>Hãy thử tìm kiếm với username, email hoặc số điện thoại khác</span>
        </div>

        <div v-else class="empty-state">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          <p>Tìm kiếm người dùng</p>
          <span>Nhập tên, username, email hoặc số điện thoại để bắt đầu</span>
        </div>
      </div>

      <div class="modal-footer">
        <button class="cancel-btn" @click="handleClose">Hủy</button>
        <button
          class="create-btn"
          @click="handleCreate"
          :disabled="!canCreate || isCreating"
        >
          <span v-if="isCreating" class="spinner-small"></span>
          {{ isCreating ? 'Đang tạo...' : 'Bắt đầu trò chuyện' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { searchUser } from '../api/users'
import { useConversationsStore } from '../stores/conversations'

const props = defineProps({
  isOpen: Boolean
})

const emit = defineEmits(['close', 'conversationCreated'])

const conversationsStore = useConversationsStore()

const searchQuery = ref('')
const searchResult = ref(null)
const isSearching = ref(false)
const error = ref(null)
const isCreating = ref(false)
let searchTimeout = null
let searchedUsers = new Map()

const canCreate = computed(() => {
  return searchResult.value !== null
})

watch(() => props.isOpen, (newVal) => {
  if (newVal) {
    resetForm()
  }
})

function handleEscKey(event) {
  if (event.key === 'Escape') {
    handleClose()
  }
}

watch(() => props.isOpen, (isOpen) => {
  if (isOpen) {
    document.addEventListener('keydown', handleEscKey)
  } else {
    document.removeEventListener('keydown', handleEscKey)
  }
})

function resetForm() {
  searchQuery.value = ''
  searchResult.value = null
  error.value = null
  isSearching.value = false
  isCreating.value = false
  searchedUsers.clear()
}

function handleSearch() {
  error.value = null
  searchResult.value = null

  if (!searchQuery.value.trim()) {
    return
  }

  if (searchTimeout) {
    clearTimeout(searchTimeout)
  }

  searchTimeout = setTimeout(async () => {
    const query = searchQuery.value.trim()

    if (searchedUsers.has(query)) {
      searchResult.value = searchedUsers.get(query)
      return
    }

    isSearching.value = true
    try {
      const response = await searchUser(query)
      const user = response.data
      searchedUsers.set(query, user)
      searchResult.value = user
    } catch (err) {
      error.value = err.response?.data?.message || 'Tìm kiếm thất bại'
    } finally {
      isSearching.value = false
    }
  }, 300)
}

async function handleCreate() {
  if (!canCreate.value || isCreating.value) return

  isCreating.value = true
  error.value = null

  try {
    const targetUserId = searchResult.value.userId
    const newConversation = await conversationsStore.createDirectConversation(targetUserId)

    if (!newConversation) {
      throw new Error('Failed to create conversation')
    }

    emit('conversationCreated', newConversation)
    emit('close')
  } catch (err) {
    console.error('Error creating conversation:', err)
    error.value = err.response?.data?.message || err.message || 'Không thể tạo đoạn chat'
    isCreating.value = false
  }
}

function handleClose() {
  if (!isCreating.value) {
    emit('close')
  }
}

function handleOverlayClick(event) {
  if (event.target === event.currentTarget) {
    handleClose()
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.modal-content {
  background: var(--color-surface);
  border-radius: 20px;
  width: 500px;
  max-width: 90vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 24px 20px;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.02em;
}

.close-btn {
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

.close-btn:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
  min-height: 400px;
}

.error-message {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fef2f2;
  color: var(--color-error);
  padding: 14px 16px;
  border-radius: 12px;
  margin-bottom: 20px;
  font-size: 14px;
  border: 1px solid rgba(220, 38, 38, 0.2);
}

.search-section {
  margin-bottom: 24px;
}

.search-label {
  display: block;
  margin-bottom: 12px;
  font-weight: 600;
  font-size: 14px;
  color: var(--color-text-primary);
}

.search-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 16px;
  color: var(--color-text-tertiary);
  pointer-events: none;
  transition: color var(--transition-fast);
}

.search-input-wrapper:focus-within .search-icon {
  color: var(--color-primary);
}

.search-input {
  width: 100%;
  padding: 14px 16px 14px 48px;
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  font-size: 15px;
  background: var(--color-input-bg);
  color: var(--color-text-primary);
  transition: all var(--transition-fast);
}

.search-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 4px var(--color-primary-light);
  background: var(--color-surface);
}

.search-input::placeholder {
  color: var(--color-text-tertiary);
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--color-text-secondary);
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-state p {
  font-size: 14px;
  margin: 0;
}

.search-result {
  animation: fadeIn 0.3s ease;
}

.result-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 2px solid var(--color-primary);
  background: var(--color-primary-light);
  border-radius: 16px;
  transition: all var(--transition-fast);
}

.user-avatar {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 22px;
  text-transform: uppercase;
  flex-shrink: 0;
  box-shadow: var(--shadow-md);
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-weight: 700;
  font-size: 16px;
  margin-bottom: 4px;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-username {
  font-size: 14px;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-badge {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--color-primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.no-results,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
  color: var(--color-text-secondary);
}

.no-results svg,
.empty-state svg {
  margin-bottom: 16px;
  opacity: 0.3;
  color: var(--color-text-tertiary);
}

.no-results p,
.empty-state p {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 8px 0;
}

.no-results span,
.empty-state span {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.modal-footer {
  padding: 20px 24px;
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.cancel-btn {
  padding: 12px 24px;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  transition: all var(--transition-fast);
}

.cancel-btn:hover {
  background: var(--color-surface-hover);
  border-color: var(--color-border-dark);
}

.create-btn {
  padding: 12px 24px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  transition: all var(--transition-fast);
  display: flex;
  align-items: center;
  gap: 8px;
}

.create-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: var(--shadow-primary);
}

.create-btn:disabled {
  background: var(--color-border);
  color: var(--color-text-tertiary);
  cursor: not-allowed;
  transform: none;
}

.spinner-small {
  display: none;
}

.create-btn:disabled .spinner-small {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
</style>
