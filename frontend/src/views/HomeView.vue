<template>
  <div class="home-container">
    <div class="home-content">
      <h1>Welcome to ChatApp</h1>

      <div class="user-info">
        <h2>Authenticated User</h2>
        <p><strong>Username:</strong> {{ authStore.user?.username || 'Loading...' }}</p>
        <p><strong>Display Name:</strong> {{ authStore.user?.displayName || 'N/A' }}</p>
        <p><strong>Email:</strong> {{ authStore.user?.email || 'N/A' }}</p>
        <p><strong>User ID:</strong> {{ authStore.user?.id || 'Loading...' }}</p>
      </div>

      <div class="connection-status">
        <h2>Connection Status</h2>
        <p>
          <strong>REST API:</strong>
          <span class="status-badge status-connected">Connected</span>
        </p>
        <p>
          <strong>WebSocket:</strong>
          <span :class="['status-badge', wsConnected ? 'status-connected' : 'status-disconnected']">
            {{ wsConnected ? 'Connected' : 'Disconnected' }}
          </span>
        </p>
      </div>

      <div class="actions">
        <button @click="handleLogout" :disabled="authStore.isLoading">
          {{ authStore.isLoading ? 'Logging out...' : 'Logout' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import websocketService from '../services/websocket'

const router = useRouter()
const authStore = useAuthStore()
const wsConnected = ref(false)

let connectionCheckInterval = null

function checkWebSocketConnection() {
  wsConnected.value = websocketService.isConnected()
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}

onMounted(() => {
  checkWebSocketConnection()
  connectionCheckInterval = setInterval(checkWebSocketConnection, 1000)
})

onUnmounted(() => {
  if (connectionCheckInterval) {
    clearInterval(connectionCheckInterval)
  }
})
</script>

<style scoped>
.home-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f5f5f5;
  padding: 2rem;
}

.home-content {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 600px;
}

h1 {
  margin-top: 0;
  margin-bottom: 2rem;
  text-align: center;
  color: #333;
}

h2 {
  margin-top: 1.5rem;
  margin-bottom: 1rem;
  font-size: 1.2rem;
  color: #555;
  border-bottom: 2px solid #4CAF50;
  padding-bottom: 0.5rem;
}

.user-info p,
.connection-status p {
  margin: 0.75rem 0;
  color: #666;
}

.status-badge {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 4px;
  font-size: 0.9rem;
  font-weight: 500;
}

.status-connected {
  background-color: #d4edda;
  color: #155724;
}

.status-disconnected {
  background-color: #f8d7da;
  color: #721c24;
}

.actions {
  margin-top: 2rem;
  text-align: center;
}

button {
  padding: 0.75rem 2rem;
  background-color: #f44336;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  font-weight: 500;
}

button:hover:not(:disabled) {
  background-color: #d32f2f;
}

button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}
</style>
