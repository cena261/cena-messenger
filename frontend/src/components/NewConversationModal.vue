<template>
  <div v-if="isOpen" class="modal-overlay" @click="handleClose">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h2>{{ isGroupMode ? 'New Group' : 'New Chat' }}</h2>
        <button class="close-btn" @click="handleClose">×</button>
      </div>

      <div class="mode-toggle">
        <button
          :class="{ active: !isGroupMode }"
          @click="isGroupMode = false"
        >
          Direct
        </button>
        <button
          :class="{ active: isGroupMode }"
          @click="isGroupMode = true"
        >
          Group
        </button>
      </div>

      <div class="modal-body">
        <div v-if="error" class="error-message">
          {{ error }}
        </div>

        <div v-if="isGroupMode" class="form-group">
          <label>Group Name *</label>
          <input
            v-model="groupName"
            type="text"
            placeholder="Enter group name"
            @keypress.enter="handleCreate"
          />
        </div>

        <div class="form-group">
          <label>{{ isGroupMode ? 'Add Members *' : 'Search User *' }}</label>
          <input
            v-model="searchQuery"
            type="text"
            placeholder="Enter username, email, or phone"
            @input="handleSearch"
          />
        </div>

        <div v-if="isSearching" class="loading">Searching...</div>

        <div v-if="searchResult && !isGroupMode" class="search-result">
          <div class="user-item">
            <div class="user-avatar">{{ searchResult.displayName?.charAt(0) || searchResult.username?.charAt(0) }}</div>
            <div class="user-info">
              <div class="user-name">{{ searchResult.displayName || searchResult.username }}</div>
              <div class="user-username">@{{ searchResult.username }}</div>
            </div>
          </div>
        </div>

        <div v-if="searchResult && isGroupMode" class="search-result">
          <div class="user-item" @click="addMember(searchResult)">
            <div class="user-avatar">{{ searchResult.displayName?.charAt(0) || searchResult.username?.charAt(0) }}</div>
            <div class="user-info">
              <div class="user-name">{{ searchResult.displayName || searchResult.username }}</div>
              <div class="user-username">@{{ searchResult.username }}</div>
            </div>
            <button class="add-btn">+</button>
          </div>
        </div>

        <div v-if="searchQuery && !searchResult && !isSearching" class="no-results">
          No user found
        </div>

        <div v-if="isGroupMode && selectedMembers.length > 0" class="selected-members">
          <h4>Selected Members ({{ selectedMembers.length }})</h4>
          <div class="member-chips">
            <div
              v-for="member in selectedMembers"
              :key="member.username"
              class="member-chip"
            >
              <span>{{ member.displayName || member.username }}</span>
              <button @click="removeMember(member)">×</button>
            </div>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="cancel-btn" @click="handleClose">Cancel</button>
        <button
          class="create-btn"
          @click="handleCreate"
          :disabled="!canCreate || isCreating"
        >
          {{ isCreating ? 'Creating...' : 'Create' }}
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

const isGroupMode = ref(false)
const searchQuery = ref('')
const searchResult = ref(null)
const isSearching = ref(false)
const error = ref(null)
const groupName = ref('')
const selectedMembers = ref([])
const isCreating = ref(false)
let searchTimeout = null
let searchedUsers = new Map()

const canCreate = computed(() => {
  if (isGroupMode.value) {
    return groupName.value.trim() && selectedMembers.value.length >= 1
  } else {
    return searchResult.value !== null
  }
})

watch(() => props.isOpen, (newVal) => {
  if (newVal) {
    resetForm()
  }
})

watch(isGroupMode, () => {
  resetForm()
})

function resetForm() {
  searchQuery.value = ''
  searchResult.value = null
  groupName.value = ''
  selectedMembers.value = []
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
      error.value = err.response?.data?.message || 'Search failed'
    } finally {
      isSearching.value = false
    }
  }, 300)
}

function addMember(user) {
  if (!selectedMembers.value.find(m => m.username === user.username)) {
    selectedMembers.value.push(user)
    searchQuery.value = ''
    searchResult.value = null
  }
}

function removeMember(user) {
  selectedMembers.value = selectedMembers.value.filter(m => m.username !== user.username)
}

async function handleCreate() {
  if (!canCreate.value || isCreating.value) return

  isCreating.value = true
  error.value = null

  try {
    let newConversation

    if (isGroupMode.value) {
      const memberUserIds = selectedMembers.value.map(m => m.userId)
      newConversation = await conversationsStore.createGroupConversation(
        groupName.value.trim(),
        memberUserIds
      )
    } else {
      const targetUserId = searchResult.value.userId
      newConversation = await conversationsStore.createDirectConversation(targetUserId)
    }

    if (!newConversation) {
      throw new Error('Failed to create conversation')
    }

    emit('conversationCreated', newConversation)
    emit('close')
  } catch (err) {
    console.error('Error creating conversation:', err)
    error.value = err.response?.data?.message || err.message || 'Failed to create conversation'
    isCreating.value = false
  }
}

function handleClose() {
  if (!isCreating.value) {
    emit('close')
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
}

.modal-content {
  background: #FFFFFF;
  border-radius: 20px;
  width: 500px;
  max-width: 90vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 24px 16px;
  border-bottom: 1px solid var(--color-border);
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
  line-height: 1;
  padding: 0;
  width: 32px;
  height: 32px;
  transition: color 0.2s ease;
}

.close-btn:hover {
  color: var(--color-text-primary);
}

.mode-toggle {
  display: flex;
  gap: 0;
  padding: 16px 24px;
  border-bottom: 1px solid var(--color-border);
}

.mode-toggle button {
  flex: 1;
  padding: 10px 20px;
  border: 1.5px solid var(--color-border);
  background: #FFFFFF;
  cursor: pointer;
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-secondary);
  transition: all 0.2s ease;
}

.mode-toggle button:first-child {
  border-radius: 10px 0 0 10px;
}

.mode-toggle button:last-child {
  border-radius: 0 10px 10px 0;
  border-left: none;
}

.mode-toggle button.active {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}

.mode-toggle button:hover:not(.active) {
  background: var(--color-bg-hover);
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  font-size: 14px;
  color: var(--color-text-primary);
}

.form-group input {
  width: 100%;
  padding: 12px 16px;
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  font-size: 15px;
  box-sizing: border-box;
  transition: all 0.2s ease;
  background: var(--color-bg-primary);
}

.form-group input:focus {
  outline: none;
  border-color: var(--color-primary);
  background: white;
  box-shadow: 0 0 0 4px rgba(224, 120, 86, 0.1);
}

.form-group input::placeholder {
  color: var(--color-text-tertiary);
}

.error-message {
  background: #FEF2F2;
  color: var(--color-error);
  padding: 14px 16px;
  border-radius: 12px;
  margin-bottom: 20px;
  font-size: 14px;
  border: 1px solid rgba(214, 69, 69, 0.2);
}

.loading {
  text-align: center;
  color: var(--color-text-secondary);
  padding: 20px;
  font-size: 14px;
}

.search-result {
  margin-top: 12px;
}

.user-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.user-item:hover {
  background: var(--color-bg-hover);
  border-color: var(--color-primary);
}

.user-avatar {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--color-gradient-warm);
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
}

.user-name {
  font-weight: 600;
  font-size: 15px;
  margin-bottom: 2px;
  color: var(--color-text-primary);
}

.user-username {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.add-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 2px solid var(--color-primary);
  background: #FFFFFF;
  color: var(--color-primary);
  font-size: 20px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  padding: 0;
  transition: all 0.2s ease;
}

.add-btn:hover {
  background: var(--color-primary);
  color: white;
  transform: scale(1.1);
}

.no-results {
  text-align: center;
  color: var(--color-text-tertiary);
  padding: 20px;
  font-size: 14px;
}

.selected-members {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
}

.selected-members h4 {
  margin: 0 0 12px 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.member-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.member-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--color-primary-light);
  border-radius: 20px;
  font-size: 14px;
  color: var(--color-primary-dark);
  font-weight: 500;
}

.member-chip button {
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: var(--color-primary);
  line-height: 1;
  padding: 0;
  width: 20px;
  height: 20px;
  transition: color 0.2s ease;
}

.member-chip button:hover {
  color: var(--color-primary-dark);
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
  background: var(--color-bg-hover);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  transition: all 0.2s ease;
}

.cancel-btn:hover {
  background: var(--color-border);
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
  transition: all 0.2s ease;
}

.create-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(224, 120, 86, 0.3);
}

.create-btn:disabled {
  background: var(--color-border);
  color: var(--color-text-tertiary);
  cursor: not-allowed;
  transform: none;
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
