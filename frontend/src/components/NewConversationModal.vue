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
  background: white;
  border-radius: 8px;
  width: 500px;
  max-width: 90vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #eee;
}

.modal-header h2 {
  margin: 0;
  font-size: 1.25rem;
}

.close-btn {
  background: none;
  border: none;
  font-size: 2rem;
  cursor: pointer;
  color: #666;
  line-height: 1;
  padding: 0;
  width: 32px;
  height: 32px;
}

.close-btn:hover {
  color: #333;
}

.mode-toggle {
  display: flex;
  gap: 0;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #eee;
}

.mode-toggle button {
  flex: 1;
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  background: white;
  cursor: pointer;
  font-size: 0.9rem;
}

.mode-toggle button:first-child {
  border-radius: 4px 0 0 4px;
}

.mode-toggle button:last-child {
  border-radius: 0 4px 4px 0;
  border-left: none;
}

.mode-toggle button.active {
  background: #4CAF50;
  color: white;
  border-color: #4CAF50;
}

.modal-body {
  padding: 1.5rem;
  overflow-y: auto;
  flex: 1;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #333;
}

.form-group input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
  box-sizing: border-box;
}

.form-group input:focus {
  outline: none;
  border-color: #4CAF50;
}

.error-message {
  background: #fee;
  color: #c33;
  padding: 0.75rem;
  border-radius: 4px;
  margin-bottom: 1rem;
  font-size: 0.9rem;
}

.loading {
  text-align: center;
  color: #666;
  padding: 1rem;
}

.search-result {
  margin-top: 0.5rem;
}

.user-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  border: 1px solid #eee;
  border-radius: 4px;
  cursor: pointer;
}

.user-item:hover {
  background: #f9f9f9;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #4CAF50;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  text-transform: uppercase;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
}

.user-name {
  font-weight: 500;
  margin-bottom: 2px;
}

.user-username {
  font-size: 0.9rem;
  color: #666;
}

.add-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 2px solid #4CAF50;
  background: white;
  color: #4CAF50;
  font-size: 1.5rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  padding: 0;
}

.add-btn:hover {
  background: #4CAF50;
  color: white;
}

.no-results {
  text-align: center;
  color: #999;
  padding: 1rem;
  font-size: 0.9rem;
}

.selected-members {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid #eee;
}

.selected-members h4 {
  margin: 0 0 0.75rem 0;
  font-size: 0.9rem;
  color: #666;
}

.member-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.member-chip {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  background: #f0f0f0;
  border-radius: 16px;
  font-size: 0.9rem;
}

.member-chip button {
  background: none;
  border: none;
  font-size: 1.25rem;
  cursor: pointer;
  color: #666;
  line-height: 1;
  padding: 0;
  width: 20px;
  height: 20px;
}

.member-chip button:hover {
  color: #333;
}

.modal-footer {
  padding: 1rem 1.5rem;
  border-top: 1px solid #eee;
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}

.cancel-btn {
  padding: 0.75rem 1.5rem;
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  font-size: 1rem;
}

.cancel-btn:hover {
  background: #f5f5f5;
}

.create-btn {
  padding: 0.75rem 1.5rem;
  background: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 1rem;
}

.create-btn:hover:not(:disabled) {
  background: #45a049;
}

.create-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}
</style>
