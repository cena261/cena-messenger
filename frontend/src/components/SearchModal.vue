<template>
  <div v-if="isOpen" class="modal-overlay" @click="handleClose">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h2>Search</h2>
        <button @click="handleClose" class="close-btn">✕</button>
      </div>

      <div class="search-tabs">
        <button
          @click="activeTab = 'conversations'"
          :class="{ active: activeTab === 'conversations' }"
          class="tab-btn"
        >
          Conversations
        </button>
        <button
          @click="activeTab = 'messages'"
          :class="{ active: activeTab === 'messages' }"
          class="tab-btn"
        >
          Messages
        </button>
      </div>

      <div class="search-input-container">
        <input
          v-if="activeTab === 'conversations'"
          v-model="conversationSearchQuery"
          @input="handleConversationSearch"
          type="text"
          placeholder="Search conversations by name or username..."
          class="search-input"
        />
        <div v-else class="message-search-inputs">
          <select v-model="selectedConversationId" class="conversation-select">
            <option value="">Select a conversation...</option>
            <option
              v-for="conv in conversationsStore.conversations"
              :key="conv.id"
              :value="conv.id"
            >
              {{ getConversationName(conv) }}
            </option>
          </select>
          <input
            v-model="messageSearchQuery"
            @input="handleMessageSearch"
            @keyup.enter="handleMessageSearch"
            type="text"
            placeholder="Search messages..."
            class="search-input"
            :disabled="!selectedConversationId"
          />
        </div>
      </div>

      <div class="search-results">
        <div v-if="activeTab === 'conversations'">
          <div v-if="searchStore.isSearchingConversations" class="loading">
            Searching conversations...
          </div>
          <div v-else-if="conversationSearchQuery && searchStore.conversationResults.length === 0" class="no-results">
            No conversations found
          </div>
          <div v-else-if="searchStore.conversationResults.length > 0" class="results-list">
            <div
              v-for="conversation in searchStore.conversationResults"
              :key="conversation.id"
              @click="handleSelectConversation(conversation.id)"
              class="result-item"
            >
              <div class="result-info">
                <div class="result-name">{{ getConversationName(conversation) }}</div>
                <div class="result-type">{{ conversation.type === 'GROUP' ? 'Group' : 'Direct Message' }}</div>
              </div>
            </div>
          </div>
        </div>

        <div v-else>
          <div v-if="searchStore.isSearchingMessages" class="loading">
            Searching messages...
          </div>
          <div v-else-if="messageSearchQuery && searchStore.messageResults.length === 0" class="no-results">
            No messages found
          </div>
          <div v-else-if="searchStore.messageResults.length > 0" class="results-list">
            <div
              v-for="message in searchStore.messageResults"
              :key="message.id"
              @click="handleSelectMessage(message)"
              class="result-item message-result"
            >
              <div class="result-info">
                <div class="message-sender">{{ message.senderDisplayName || message.senderUsername }}</div>
                <div class="message-content">{{ message.content }}</div>
                <div class="message-time">{{ formatTime(message.createdAt) }}</div>
              </div>
            </div>
            <div v-if="searchStore.messageHasNext" class="load-more-container">
              <button @click="loadMoreMessages" class="load-more-btn" :disabled="searchStore.isSearchingMessages">
                Load More
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useSearchStore } from '../stores/search'
import { useConversationsStore } from '../stores/conversations'
import { useAuthStore } from '../stores/auth'

const props = defineProps({
  isOpen: {
    type: Boolean,
    required: true
  }
})

const emit = defineEmits(['close', 'selectConversation', 'selectMessage'])

const searchStore = useSearchStore()
const conversationsStore = useConversationsStore()
const authStore = useAuthStore()

const activeTab = ref('conversations')
const conversationSearchQuery = ref('')
const messageSearchQuery = ref('')
const selectedConversationId = ref('')
let searchTimeout = null

watch(() => props.isOpen, (isOpen) => {
  if (!isOpen) {
    conversationSearchQuery.value = ''
    messageSearchQuery.value = ''
    selectedConversationId.value = ''
    searchStore.clearAllSearch()
  }
})

function handleConversationSearch() {
  if (searchTimeout) {
    clearTimeout(searchTimeout)
  }

  searchTimeout = setTimeout(async () => {
    if (conversationSearchQuery.value.trim()) {
      await searchStore.searchConversations(conversationSearchQuery.value)
    } else {
      searchStore.clearConversationSearch()
    }
  }, 300)
}

function handleMessageSearch() {
  if (!selectedConversationId.value) return

  if (searchTimeout) {
    clearTimeout(searchTimeout)
  }

  searchTimeout = setTimeout(async () => {
    if (messageSearchQuery.value.trim()) {
      await searchStore.searchMessages(selectedConversationId.value, messageSearchQuery.value, 0)
    } else {
      searchStore.clearMessageSearch()
    }
  }, 300)
}

async function loadMoreMessages() {
  await searchStore.loadMoreMessages()
}

function handleSelectConversation(conversationId) {
  emit('selectConversation', conversationId)
  handleClose()
}

function handleSelectMessage(message) {
  emit('selectMessage', message)
  handleClose()
}

function handleClose() {
  emit('close')
}

function getConversationName(conversation) {
  if (conversation.type === 'GROUP') {
    return conversation.name || 'Unnamed Group'
  }

  const otherMember = conversation.members?.find(m => m.userId !== authStore.user?.id)
  return otherMember?.displayName || otherMember?.username || 'Unknown User'
}

function formatTime(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
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
  background-color: white;
  border-radius: 8px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}

.modal-header {
  padding: 1.5rem;
  border-bottom: 1px solid #ddd;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header h2 {
  margin: 0;
  font-size: 1.5rem;
  color: #333;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #666;
  padding: 0;
}

.close-btn:hover {
  color: #333;
}

.search-tabs {
  display: flex;
  border-bottom: 1px solid #ddd;
}

.tab-btn {
  flex: 1;
  padding: 1rem;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 1rem;
  color: #666;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.tab-btn.active {
  color: #4CAF50;
  border-bottom-color: #4CAF50;
  font-weight: 600;
}

.tab-btn:hover {
  background-color: #f5f5f5;
}

.search-input-container {
  padding: 1rem;
  border-bottom: 1px solid #ddd;
}

.message-search-inputs {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.search-input,
.conversation-select {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

.search-input:focus,
.conversation-select:focus {
  outline: none;
  border-color: #4CAF50;
}

.search-input:disabled {
  background-color: #f5f5f5;
  cursor: not-allowed;
}

.search-results {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
}

.loading,
.no-results {
  text-align: center;
  color: #666;
  padding: 2rem;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.result-item {
  padding: 1rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.result-item:hover {
  background-color: #f5f5f5;
}

.result-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.result-name {
  font-weight: 600;
  color: #333;
  font-size: 1rem;
}

.result-type {
  font-size: 0.875rem;
  color: #666;
}

.message-result {
  border-left: 3px solid #4CAF50;
}

.message-sender {
  font-weight: 600;
  color: #333;
  font-size: 0.875rem;
}

.message-content {
  color: #555;
  font-size: 1rem;
  margin: 0.25rem 0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.message-time {
  font-size: 0.75rem;
  color: #999;
}

.load-more-container {
  display: flex;
  justify-content: center;
  margin-top: 1rem;
}

.load-more-btn {
  padding: 0.75rem 1.5rem;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 1rem;
}

.load-more-btn:hover:not(:disabled) {
  background-color: #45a049;
}

.load-more-btn:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}
</style>
