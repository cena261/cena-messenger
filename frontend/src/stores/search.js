import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as searchApi from '../api/search'

export const useSearchStore = defineStore('search', () => {
  const conversationResults = ref([])
  const messageResults = ref([])
  const messagePage = ref(0)
  const messageSize = ref(20)
  const messageTotalElements = ref(0)
  const messageHasNext = ref(false)
  const isSearchingConversations = ref(false)
  const isSearchingMessages = ref(false)
  const conversationQuery = ref('')
  const messageQuery = ref('')
  const currentConversationId = ref(null)

  async function searchConversations(query) {
    if (!query || !query.trim()) {
      conversationResults.value = []
      conversationQuery.value = ''
      return
    }

    isSearchingConversations.value = true
    conversationQuery.value = query

    try {
      const response = await searchApi.searchConversations(query)
      conversationResults.value = response.data || []
    } catch (err) {
      console.error('Failed to search conversations:', err)
      conversationResults.value = []
      throw err
    } finally {
      isSearchingConversations.value = false
    }
  }

  async function searchMessages(conversationId, query, page = 0) {
    if (!conversationId || !query || !query.trim()) {
      messageResults.value = []
      messageQuery.value = ''
      return
    }

    isSearchingMessages.value = true
    messageQuery.value = query
    currentConversationId.value = conversationId

    try {
      const response = await searchApi.searchMessages(conversationId, query, page, messageSize.value)
      const data = response.data

      if (page === 0) {
        messageResults.value = data.messages || []
      } else {
        messageResults.value = [...messageResults.value, ...(data.messages || [])]
      }

      messagePage.value = data.page
      messageTotalElements.value = data.totalElements
      messageHasNext.value = data.hasNext
    } catch (err) {
      console.error('Failed to search messages:', err)
      if (page === 0) {
        messageResults.value = []
      }
      throw err
    } finally {
      isSearchingMessages.value = false
    }
  }

  async function loadMoreMessages() {
    if (!messageHasNext.value || isSearchingMessages.value) return

    await searchMessages(currentConversationId.value, messageQuery.value, messagePage.value + 1)
  }

  function clearConversationSearch() {
    conversationResults.value = []
    conversationQuery.value = ''
  }

  function clearMessageSearch() {
    messageResults.value = []
    messageQuery.value = ''
    messagePage.value = 0
    messageTotalElements.value = 0
    messageHasNext.value = false
    currentConversationId.value = null
  }

  function clearAllSearch() {
    clearConversationSearch()
    clearMessageSearch()
  }

  return {
    conversationResults,
    messageResults,
    messagePage,
    messageSize,
    messageTotalElements,
    messageHasNext,
    isSearchingConversations,
    isSearchingMessages,
    conversationQuery,
    messageQuery,
    searchConversations,
    searchMessages,
    loadMoreMessages,
    clearConversationSearch,
    clearMessageSearch,
    clearAllSearch
  }
})
