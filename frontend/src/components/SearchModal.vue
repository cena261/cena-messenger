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
            <div class="spinner"></div>
            <p>Searching conversations...</p>
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
            <div class="spinner"></div>
            <p>Searching messages...</p>
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
  background-color: #FFFFFF;
  border-radius: 20px;
  width: 90%;
  max-width: 600px;
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
  transition: color 0.2s ease;
}

.close-btn:hover {
  color: var(--color-text-primary);
}

.search-tabs {
  display: flex;
  border-bottom: 1px solid var(--color-border);
}

.tab-btn {
  flex: 1;
  padding: 16px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-secondary);
  border-bottom: 2px solid transparent;
  transition: all 0.2s ease;
}

.tab-btn.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  font-weight: 600;
}

.tab-btn:hover:not(.active) {
  background-color: var(--color-bg-hover);
}

.search-input-container {
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border);
}

.message-search-inputs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-input,
.conversation-select {
  width: 100%;
  padding: 12px 16px;
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  font-size: 15px;
  transition: all 0.2s ease;
  background: var(--color-bg-primary);
}

.search-input:focus,
.conversation-select:focus {
  outline: none;
  border-color: var(--color-primary);
  background: white;
  box-shadow: 0 0 0 4px rgba(224, 120, 86, 0.1);
}

.search-input:disabled {
  background-color: var(--color-bg-hover);
  cursor: not-allowed;
  color: var(--color-text-tertiary);
}

.search-input::placeholder {
  color: var(--color-text-tertiary);
}

.search-results {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.loading,
.no-results {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--color-text-secondary);
  padding: 48px 24px;
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

.results-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-item {
  padding: 16px;
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.result-item:hover {
  background-color: var(--color-bg-hover);
  border-color: var(--color-primary);
}

.result-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.result-name {
  font-weight: 600;
  color: var(--color-text-primary);
  font-size: 15px;
}

.result-type {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.message-result {
  border-left: 3px solid var(--color-primary);
}

.message-sender {
  font-weight: 600;
  color: var(--color-text-primary);
  font-size: 13px;
}

.message-content {
  color: var(--color-text-secondary);
  font-size: 15px;
  margin: 4px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.message-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.load-more-container {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.load-more-btn {
  padding: 10px 24px;
  background-color: var(--color-primary);
  color: white;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.load-more-btn:hover:not(:disabled) {
  background-color: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(224, 120, 86, 0.3);
}

.load-more-btn:disabled {
  background-color: var(--color-border);
  color: var(--color-text-tertiary);
  cursor: not-allowed;
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
