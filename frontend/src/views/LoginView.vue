<template>
  <div class="login-container">
    <div class="login-box">
      <h1>Chat App</h1>

      <div v-if="error" class="error-message">
        {{ error }}
      </div>

      <div v-if="successMessage" class="success-message">
        {{ successMessage }}
      </div>

      <form v-if="!isForgotPassword" @submit.prevent="handleSubmit">
        <div class="form-group">
          <label for="username">Username</label>
          <input
            id="username"
            v-model="username"
            type="text"
            required
            autocomplete="username"
          />
        </div>

        <div class="form-group">
          <label for="password">Password</label>
          <input
            id="password"
            v-model="password"
            type="password"
            required
            autocomplete="current-password"
          />
        </div>

        <div v-if="isRegistering" class="form-group">
          <label for="email">Email</label>
          <input
            id="email"
            v-model="email"
            type="email"
            :required="isRegistering"
            autocomplete="email"
          />
        </div>

        <div v-if="isRegistering" class="form-group">
          <label for="displayName">Display Name</label>
          <input
            id="displayName"
            v-model="displayName"
            type="text"
            :required="isRegistering"
          />
        </div>

        <div v-if="isRegistering" class="form-group">
          <label for="phone">Phone (optional)</label>
          <input
            id="phone"
            v-model="phone"
            type="tel"
            autocomplete="tel"
          />
        </div>

        <button type="submit" :disabled="isLoading">
          {{ isLoading ? 'Loading...' : (isRegistering ? 'Register' : 'Login') }}
        </button>
      </form>

      <form v-if="isForgotPassword && resetStep === 1" @submit.prevent="handleRequestReset">
        <div class="form-group">
          <label for="resetEmail">Email</label>
          <input
            id="resetEmail"
            v-model="resetEmail"
            type="email"
            required
            autocomplete="email"
            placeholder="Enter your email address"
          />
        </div>

        <button type="submit" :disabled="isLoading">
          {{ isLoading ? 'Sending...' : 'Send Reset Code' }}
        </button>
      </form>

      <form v-if="isForgotPassword && resetStep === 2" @submit.prevent="handleResetPassword">
        <div class="form-group">
          <label for="resetCode">6-Digit Code</label>
          <input
            id="resetCode"
            v-model="resetCode"
            type="text"
            required
            maxlength="6"
            pattern="[0-9]{6}"
            placeholder="Enter 6-digit code"
          />
        </div>

        <div class="form-group">
          <label for="newPassword">New Password</label>
          <input
            id="newPassword"
            v-model="newPassword"
            type="password"
            required
            minlength="8"
            placeholder="Minimum 8 characters"
          />
        </div>

        <button type="submit" :disabled="isLoading">
          {{ isLoading ? 'Resetting...' : 'Reset Password' }}
        </button>
      </form>

      <div v-if="!isForgotPassword" class="toggle-mode">
        <button type="button" @click="toggleForgotPassword">
          Forgot password?
        </button>
      </div>

      <div v-if="!isForgotPassword" class="toggle-mode">
        <button type="button" @click="isRegistering = !isRegistering">
          {{ isRegistering ? 'Already have an account? Login' : 'Need an account? Register' }}
        </button>
      </div>

      <div v-if="isForgotPassword" class="toggle-mode">
        <button type="button" @click="backToLogin">
          Back to Login
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import * as authApi from '../api/auth'

const router = useRouter()
const authStore = useAuthStore()
const realtimeStore = useRealtimeStore()

const username = ref('')
const password = ref('')
const email = ref('')
const displayName = ref('')
const phone = ref('')
const isRegistering = ref(false)
const isLoading = ref(false)
const error = ref(null)

const isForgotPassword = ref(false)
const resetStep = ref(1)
const resetEmail = ref('')
const resetCode = ref('')
const newPassword = ref('')
const successMessage = ref(null)

async function handleSubmit() {
  isLoading.value = true
  error.value = null
  successMessage.value = null

  try {
    if (isRegistering.value) {
      await authStore.register(
        username.value,
        password.value,
        email.value,
        displayName.value,
        phone.value || null
      )
    } else {
      await authStore.login(username.value, password.value)
    }

    realtimeStore.initializeSubscriptions()
    router.push('/conversations')
  } catch (err) {
    error.value = err.response?.data?.message || 'Authentication failed'
  } finally {
    isLoading.value = false
  }
}

async function handleRequestReset() {
  isLoading.value = true
  error.value = null
  successMessage.value = null

  try {
    await authApi.requestPasswordReset(resetEmail.value)
    successMessage.value = 'Reset code sent to your email'
    resetStep.value = 2
  } catch (err) {
    error.value = err.response?.data?.message || 'Failed to send reset code'
  } finally {
    isLoading.value = false
  }
}

async function handleResetPassword() {
  isLoading.value = true
  error.value = null
  successMessage.value = null

  try {
    await authApi.resetPassword(resetEmail.value, resetCode.value, newPassword.value)
    successMessage.value = 'Password reset successfully! You can now login.'

    setTimeout(() => {
      backToLogin()
    }, 2000)
  } catch (err) {
    error.value = err.response?.data?.message || 'Failed to reset password'
  } finally {
    isLoading.value = false
  }
}

function toggleForgotPassword() {
  isForgotPassword.value = true
  resetStep.value = 1
  error.value = null
  successMessage.value = null
  resetEmail.value = ''
  resetCode.value = ''
  newPassword.value = ''
}

function backToLogin() {
  isForgotPassword.value = false
  resetStep.value = 1
  error.value = null
  successMessage.value = null
  resetEmail.value = ''
  resetCode.value = ''
  newPassword.value = ''
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f5f5f5;
}

.login-box {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 400px;
}

h1 {
  margin-top: 0;
  margin-bottom: 1.5rem;
  text-align: center;
  color: #333;
}

.error-message {
  background-color: #fee;
  color: #c33;
  padding: 0.75rem;
  border-radius: 4px;
  margin-bottom: 1rem;
  font-size: 0.9rem;
}

.success-message {
  background-color: #efe;
  color: #3c3;
  padding: 0.75rem;
  border-radius: 4px;
  margin-bottom: 1rem;
  font-size: 0.9rem;
}

.form-group {
  margin-bottom: 1rem;
}

label {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: 500;
  color: #555;
}

input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
  box-sizing: border-box;
}

input:focus {
  outline: none;
  border-color: #4CAF50;
}

button[type="submit"] {
  width: 100%;
  padding: 0.75rem;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  font-weight: 500;
}

button[type="submit"]:hover:not(:disabled) {
  background-color: #45a049;
}

button[type="submit"]:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.toggle-mode {
  margin-top: 1rem;
  text-align: center;
}

.toggle-mode button {
  background: none;
  border: none;
  color: #4CAF50;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
  font-size: 0.9rem;
}

.toggle-mode button:hover {
  color: #45a049;
}
</style>
