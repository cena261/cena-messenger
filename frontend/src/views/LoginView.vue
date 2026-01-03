<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <div class="app-icon">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            <path d="M9 10h6M9 14h4"/>
          </svg>
        </div>
        <h1>{{ isForgotPassword ? 'Reset Password' : (isRegistering ? 'Create Account' : 'Welcome Back') }}</h1>
        <p>{{ isForgotPassword ? 'Enter your email to reset password' : (isRegistering ? 'Sign up to start chatting' : 'Sign in to continue') }}</p>
      </div>

      <div v-if="error" class="alert error">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        {{ error }}
      </div>

      <div v-if="successMessage" class="alert success">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
          <polyline points="22 4 12 14.01 9 11.01"/>
        </svg>
        {{ successMessage }}
      </div>

      <!-- Login/Register Form -->
      <form v-if="!isForgotPassword" @submit.prevent="handleSubmit" class="form">
        <div class="form-group">
          <label for="username">Username</label>
          <input
            id="username"
            v-model="username"
            type="text"
            required
            autocomplete="username"
            placeholder="Enter your username"
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
            placeholder="Enter your password"
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
            placeholder="Enter your email"
          />
        </div>

        <div v-if="isRegistering" class="form-group">
          <label for="displayName">Display Name</label>
          <input
            id="displayName"
            v-model="displayName"
            type="text"
            :required="isRegistering"
            placeholder="How should we call you?"
          />
        </div>

        <div v-if="isRegistering" class="form-group">
          <label for="phone">Phone (optional)</label>
          <input
            id="phone"
            v-model="phone"
            type="tel"
            autocomplete="tel"
            placeholder="Your phone number"
          />
        </div>

        <button type="submit" :disabled="isLoading" class="btn-primary">
          <span v-if="!isLoading">{{ isRegistering ? 'Create Account' : 'Sign In' }}</span>
          <span v-else class="loading-spinner"></span>
        </button>

        <button type="button" @click="toggleForgotPassword" class="btn-text">
          Forgot password?
        </button>

        <div class="divider">
          <span>{{ isRegistering ? 'Already have an account?' : 'New here?' }}</span>
        </div>

        <button type="button" @click="isRegistering = !isRegistering" class="btn-secondary">
          {{ isRegistering ? 'Sign In' : 'Create Account' }}
        </button>
      </form>

      <!-- Password Reset Form -->
      <form v-if="isForgotPassword && resetStep === 1" @submit.prevent="handleRequestReset" class="form">
        <div class="form-group">
          <label for="resetEmail">Email Address</label>
          <input
            id="resetEmail"
            v-model="resetEmail"
            type="email"
            required
            autocomplete="email"
            placeholder="Enter your email address"
          />
        </div>

        <button type="submit" :disabled="isLoading" class="btn-primary">
          <span v-if="!isLoading">Send Reset Code</span>
          <span v-else class="loading-spinner"></span>
        </button>

        <button type="button" @click="backToLogin" class="btn-text">
          Back to Sign In
        </button>
      </form>

      <form v-if="isForgotPassword && resetStep === 2" @submit.prevent="handleResetPassword" class="form">
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

        <button type="submit" :disabled="isLoading" class="btn-primary">
          <span v-if="!isLoading">Reset Password</span>
          <span v-else class="loading-spinner"></span>
        </button>

        <button type="button" @click="backToLogin" class="btn-text">
          Back to Sign In
        </button>
      </form>
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
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #FAF8F5 0%, #F5F2EE 100%);
  padding: 20px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', sans-serif;
}

.login-card {
  width: 100%;
  max-width: 440px;
  background: white;
  border-radius: 20px;
  padding: 48px 40px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.app-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
  background: var(--color-gradient-warm);
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.login-header h1 {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}

.login-header p {
  font-size: 15px;
  color: var(--color-text-secondary);
  margin: 0;
}

.alert {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 12px;
  font-size: 14px;
  margin-bottom: 24px;
}

.alert.error {
  background: #FEF2F2;
  color: var(--color-error);
}

.alert.success {
  background: #D1FAE5;
  color: #065F46;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-group label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.form-group input {
  padding: 12px 16px;
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  font-size: 15px;
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

.btn-primary {
  padding: 14px 24px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-top: 8px;
}

.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(224, 120, 86, 0.3);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.loading-spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.btn-text {
  padding: 8px;
  background: none;
  border: none;
  color: var(--color-primary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: underline;
}

.btn-text:hover {
  color: var(--color-primary-dark);
}

.divider {
  display: flex;
  align-items: center;
  text-align: center;
  margin: 8px 0;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  border-bottom: 1px solid var(--color-border);
}

.divider span {
  padding: 0 16px;
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.btn-secondary {
  padding: 12px 24px;
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-secondary:hover {
  background: var(--color-border);
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
