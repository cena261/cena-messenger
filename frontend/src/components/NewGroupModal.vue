<template>
  <div v-if="isOpen" class="modal-overlay" @click="handleOverlayClick">
    <div class="modal-content" @click.stop">
      <div class="modal-header">
        <h2>Nhóm chat mới</h2>
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

        <div class="form-group">
          <label class="form-label">Tên nhóm</label>
          <input
            v-model="groupName"
            type="text"
            placeholder="Nhập tên nhóm..."
            class="form-input"
            autofocus
          />
        </div>

        <div class="form-group">
          <label class="form-label">Thêm thành viên</label>
          <div class="search-input-wrapper">
            <svg class="search-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <path d="m21 21-4.35-4.35"/>
            </svg>
            <input
              v-model="searchQuery"
              type="text"
              placeholder="Tìm kiếm để thêm thành viên..."
              @input="handleSearch"
              class="search-input"
            />
          </div>
        </div>

        <div v-if="selectedMembers.length > 0" class="selected-section">
          <div class="section-header">
            <h4>Đã chọn ({{ selectedMembers.length }})</h4>
          </div>
          <div class="selected-list">
            <div
              v-for="member in selectedMembers"
              :key="member.userId"
              class="member-chip"
            >
              <div class="chip-avatar">{{ getInitial(member) }}</div>
              <span class="chip-name">{{ member.displayName || member.username }}</span>
              <button @click="removeMember(member)" class="chip-remove">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>
        </div>

        <div v-if="isSearching" class="loading-state">
          <div class="spinner-small"></div>
          <p>Đang tìm kiếm...</p>
        </div>

        <div v-else-if="searchQuery && searchResult" class="search-results">
          <div class="section-header">
            <h4>Kết quả tìm kiếm</h4>
          </div>
          <div
            class="user-item"
            @click="addMember(searchResult)"
            :class="{ disabled: isSelected(searchResult) }"
          >
            <div class="user-avatar">{{ getInitial(searchResult) }}</div>
            <div class="user-info">
              <div class="user-name">{{ searchResult.displayName || searchResult.username }}</div>
              <div class="user-username">@{{ searchResult.username }}</div>
            </div>
            <button v-if="!isSelected(searchResult)" class="add-btn">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
            <div v-else class="added-badge">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            </div>
          </div>
        </div>

        <div v-else-if="searchQuery && !isSearching" class="no-results">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="11" cy="11" r="8"/>
            <path d="m21 21-4.35-4.35"/>
          </svg>
          <p>Không tìm thấy</p>
        </div>

        <div v-else-if="conversationUsers.length > 0" class="user-list-section">
          <div class="section-header">
            <h4>Người đã trò chuyện</h4>
          </div>
          <div class="user-list">
            <div
              v-for="user in conversationUsers"
              :key="user.userId"
              class="user-item"
              @click="addMember(user)"
              :class="{ disabled: isSelected(user) }"
            >
              <div class="user-avatar">{{ getInitial(user) }}</div>
              <div class="user-info">
                <div class="user-name">{{ user.displayName || user.username }}</div>
                <div class="user-username">@{{ user.username }}</div>
              </div>
              <button v-if="!isSelected(user)" class="add-btn">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="5" x2="12" y2="19"/>
                  <line x1="5" y1="12" x2="19" y2="12"/>
                </svg>
              </button>
              <div v-else class="added-badge">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="empty-state">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
          <p>Tìm kiếm để thêm thành viên</p>
          <span>Sử dụng ô tìm kiếm ở trên để tìm và thêm thành viên vào nhóm</span>
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
          {{ isCreating ? 'Đang tạo...' : 'Tạo nhóm' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { searchUser } from '../api/users'
import { useConversationsStore } from '../stores/conversations'
import { useAuthStore } from '../stores/auth'

const props = defineProps({
  isOpen: Boolean
})

const emit = defineEmits(['close', 'groupCreated'])

const conversationsStore = useConversationsStore()
const authStore = useAuthStore()

const groupName = ref('')
const searchQuery = ref('')
const searchResult = ref(null)
const isSearching = ref(false)
const error = ref(null)
const selectedMembers = ref([])
const isCreating = ref(false)
let searchTimeout = null

const conversationUsers = computed(() => {
  const users = new Map()
  const currentUserId = authStore.user?.id

  conversationsStore.conversations.forEach(conv => {
    if (conv.members) {
      conv.members.forEach(member => {
        if (member.userId !== currentUserId && !users.has(member.userId)) {
          users.set(member.userId, {
            userId: member.userId,
            username: member.username,
            displayName: member.displayName,
            avatarUrl: member.avatarUrl
          })
        }
      })
    }
  })

  return Array.from(users.values())
})

const canCreate = computed(() => {
  return groupName.value.trim() && selectedMembers.value.length >= 1
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
  groupName.value = ''
  searchQuery.value = ''
  searchResult.value = null
  selectedMembers.value = []
  error.value = null
  isSearching.value = false
  isCreating.value = false
}

function getInitial(user) {
  const name = user.displayName || user.username || '?'
  return name.charAt(0).toUpperCase()
}

function isSelected(user) {
  return selectedMembers.value.some(m => m.userId === user.userId)
}

function addMember(user) {
  if (!isSelected(user)) {
    selectedMembers.value.push(user)
    searchQuery.value = ''
    searchResult.value = null
  }
}

function removeMember(user) {
  selectedMembers.value = selectedMembers.value.filter(m => m.userId !== user.userId)
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

    isSearching.value = true
    try {
      const response = await searchUser(query)
      searchResult.value = response.data
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
    const memberUserIds = selectedMembers.value.map(m => m.userId)
    const newGroup = await conversationsStore.createGroupConversation(
      groupName.value.trim(),
      memberUserIds
    )

    if (!newGroup) {
      throw new Error('Failed to create group')
    }

    emit('groupCreated', newGroup)
    emit('close')
  } catch (err) {
    console.error('Error creating group:', err)
    error.value = err.response?.data?.message || err.message || 'Không thể tạo nhóm'
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
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-content {
  background: var(--color-surface);
  border-radius: 20px;
  width: 540px;
  max-width: 90vw;
  max-height: 85vh;
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

.form-group {
  margin-bottom: 20px;
}

.form-label {
  display: block;
  margin-bottom: 10px;
  font-weight: 600;
  font-size: 14px;
  color: var(--color-text-primary);
}

.form-input {
  width: 100%;
  padding: 12px 16px;
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  font-size: 15px;
  background: var(--color-input-bg);
  color: var(--color-text-primary);
  transition: all var(--transition-fast);
}

.form-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 4px var(--color-primary-light);
  background: var(--color-surface);
}

.form-input::placeholder {
  color: var(--color-text-tertiary);
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
  padding: 12px 16px 12px 48px;
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

.section-header {
  margin-bottom: 12px;
}

.section-header h4 {
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--color-text-secondary);
  margin: 0;
}

.selected-section {
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border);
}

.selected-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.member-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px 6px 6px;
  background: var(--color-primary-light);
  border: 1px solid var(--color-primary);
  border-radius: 20px;
  font-size: 14px;
  transition: all var(--transition-fast);
}

.chip-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
}

.chip-name {
  color: var(--color-text-primary);
  font-weight: 500;
}

.chip-remove {
  background: transparent;
  border: none;
  padding: 2px;
  cursor: pointer;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color var(--transition-fast);
}

.chip-remove:hover {
  color: var(--color-error);
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 20px;
  color: var(--color-text-secondary);
}

.loading-state p {
  margin: 0;
  font-size: 14px;
}

.user-list-section,
.search-results {
  margin-top: 20px;
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
}

.user-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.user-item:hover:not(.disabled) {
  background: var(--color-surface-hover);
  border-color: var(--color-primary);
}

.user-item.disabled {
  opacity: 0.6;
  cursor: default;
}

.user-avatar {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 18px;
  text-transform: uppercase;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-weight: 600;
  font-size: 15px;
  margin-bottom: 2px;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-username {
  font-size: 13px;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.add-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 2px solid var(--color-primary);
  background: transparent;
  color: var(--color-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
  flex-shrink: 0;
}

.add-btn:hover {
  background: var(--color-primary);
  color: white;
  transform: scale(1.1);
}

.added-badge {
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
  padding: 40px 20px;
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
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
