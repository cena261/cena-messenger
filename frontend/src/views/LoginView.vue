<template>
  <div class="auth-container">
    <div class="auth-hero">
      <div class="hero-background">
        <img 
          src="https://images.unsplash.com/photo-1563986768609-322da13575f3?q=80&w=2070&auto=format&fit=crop" 
          alt="Abstract network background" 
          class="hero-image"
        >
        <div class="hero-overlay"></div>
      </div>
      
      <div class="hero-content">
        <div class="hero-header">
          <div class="brand-icon">
            <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M4 4H17.3334V17.3334H30.6666V30.6666H44V44H4V4Z" fill="currentColor"/>
            </svg>
          </div>
          <h2 class="brand-name">Cena</h2>
        </div>

        <div class="hero-message">
          <h1>Kết nối với mọi người theo real-time, ngay lập tức.</h1>
          <p>"Giao tiếp nhanh chóng, đáng tin cậy, và không xao nhãng."</p>
          
          <div class="testimonial">
            <div class="avatar-circle">JD</div>
            <div class="testimonial-info">
              <p class="name">Nguyen Tu</p>
              <p class="role">Cuu thieu nhi</p>
            </div>
          </div>
        </div>

        <div class="hero-footer">
          © 2024 Cena Inc. All rights reserved.
        </div>
      </div>
    </div>

    <div class="auth-form-container">
      <div class="auth-form-wrapper">
        <div class="mobile-brand">
          <div class="brand-icon-mobile">
            <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M4 4H17.3334V17.3334H30.6666V30.6666H44V44H4V4Z" fill="currentColor"/>
            </svg>
          </div>
          <h2>Cena</h2>
        </div>

        <div class="auth-header">
          <h2>{{ isRegistering ? 'Tạo tài khoản' : 'Đăng nhập' }}</h2>
          <p>{{ isRegistering ? 'Bắt đầu ngay với Cena hôm nay.' : 'Vui lòng nhập thông tin của bạn để đăng nhập.' }}</p>
        </div>

        <div v-if="error" class="alert alert-error">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          {{ error }}
        </div>

        <div v-if="successMessage" class="alert alert-success">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
          {{ successMessage }}
        </div>

        <Transition name="fade" mode="out-in">
          <form v-if="!isForgotPassword" :key="isRegistering ? 'register' : 'login'" @submit.prevent="handleSubmit" class="auth-form">
            <div v-if="!isRegistering" class="form-group">
              <label for="username">Tên tài khoản</label>
              <div class="input-wrapper">
                <input
                  id="username"
                  v-model="username"
                  type="text"
                  required
                  autocomplete="username"
                  placeholder="Điền tên tài khoản"
                  class="form-input"
                >
                <div class="input-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                    <circle cx="12" cy="7" r="4"/>
                  </svg>
                </div>
              </div>
            </div>

            <div v-if="isRegistering" class="form-group">
              <label for="reg-username">Tên tài khoản</label>
              <input
                id="reg-username"
                v-model="username"
                type="text"
                required
                autocomplete="username"
                placeholder="Mọi người sẽ tìm kiếm bạn theo tên tài khoản"
                class="form-input"
              >
            </div>

            <div class="form-group">
              <label for="password">Mật khẩu</label>
              <div class="input-wrapper">
                <input
                  id="password"
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  required
                  :autocomplete="isRegistering ? 'new-password' : 'current-password'"
                  :placeholder="isRegistering ? 'Ít nhất 6 ký tự' : 'Điền mật khẩu'"
                  class="form-input"
                >
                <button 
                  type="button" 
                  @click="showPassword = !showPassword"
                  class="input-icon clickable"
                >
                  <svg v-if="showPassword" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                    <circle cx="12" cy="12" r="3"/>
                  </svg>
                  <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                    <line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                </button>
              </div>
            </div>

            <div v-if="isRegistering" class="form-group">
              <label for="email">Email</label>
              <input
                id="email"
                v-model="email"
                type="email"
                required
                autocomplete="email"
                placeholder="name@company.com"
                class="form-input"
              >
            </div>

            <div v-if="isRegistering" class="form-group">
              <label for="displayName">Tên hiển thị</label>
              <input
                id="displayName"
                v-model="displayName"
                type="text"
                required
                placeholder="Mọi người nên gọi bạn như nào?"
                class="form-input"
              >
            </div>

            <div v-if="isRegistering" class="form-group">
              <label for="phone">Số điện thoại <span class="optional">(Optional)</span></label>
              <input
                id="phone"
                v-model="phone"
                type="tel"
                autocomplete="tel"
                placeholder="+84 123456789"
                class="form-input"
              >
            </div>

            <div v-if="!isRegistering" class="form-options">
              <div class="remember-me">
                <input
                  id="remember"
                  v-model="rememberMe"
                  type="checkbox"
                  class="checkbox"
                >
                <label for="remember">Ghi nhớ đăng nhập</label>
              </div>
              <button type="button" @click="toggleForgotPassword" class="link-button">
                Quên mật khẩu?
              </button>
            </div>

            <div v-if="isRegistering" class="terms-checkbox">
              <input
                id="terms"
                v-model="acceptTerms"
                type="checkbox"
                class="checkbox"
                required
              >
              <label for="terms">
                Tôi đồng ý với <a href="#" class="link">Điều khoản dịch vụ</a> và <a href="#" class="link">Chính sách bảo mật</a>.
              </label>
            </div>

            <button type="submit" :disabled="isLoading" class="btn-primary">
              <span v-if="!isLoading">{{ isRegistering ? 'Tạo tài khoản' : 'Đăng nhập' }}</span>
              <span v-else class="loading-spinner"></span>
            </button>

            <div class="auth-switch">
              <p>
                {{ isRegistering ? 'Đã có tài khoản?' : "Chưa có tài khoản?" }}
                <button type="button" @click="toggleAuthMode" class="link-button">
                  {{ isRegistering ? 'Đăng nhập' : 'Đăng ký' }}
                </button>
              </p>
            </div>
          </form>

          <form v-else-if="resetStep === 1" key="reset-request" @submit.prevent="handleRequestReset" class="auth-form">
            <div class="form-group">
              <label for="resetEmail">Email</label>
              <input
                id="resetEmail"
                v-model="resetEmail"
                type="email"
                required
                autocomplete="email"
                placeholder="Địa chỉ email"
                class="form-input"
              >
            </div>

            <button type="submit" :disabled="isLoading" class="btn-primary">
              <span v-if="!isLoading">Gửi mã khôi phục</span>
              <span v-else class="loading-spinner"></span>
            </button>

            <button type="button" @click="backToLogin" class="btn-text">
              Quay lại đăng nhập
            </button>
          </form>

          <form v-else key="reset-confirm" @submit.prevent="handleResetPassword" class="auth-form">
            <div class="form-group">
              <label for="resetCode">Mã xác nhận</label>
              <input
                id="resetCode"
                v-model="resetCode"
                type="text"
                required
                maxlength="6"
                pattern="[0-9]{6}"
                placeholder="Mã xác nhận"
                class="form-input"
              >
            </div>

            <div class="form-group">
              <label for="newPassword">Mật khẩu mới</label>
              <input
                id="newPassword"
                v-model="newPassword"
                type="password"
                required
                minlength="8"
                placeholder="Ít nhất 6 ký tự"
                class="form-input"
              >
            </div>

            <button type="submit" :disabled="isLoading" class="btn-primary">
              <span v-if="!isLoading">Reset mật khẩu</span>
              <span v-else class="loading-spinner"></span>
            </button>

            <button type="button" @click="backToLogin" class="btn-text">
              Quay lại đăng nhập
            </button>
          </form>
        </Transition>
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
const showPassword = ref(false)
const rememberMe = ref(false)
const acceptTerms = ref(false)

const isForgotPassword = ref(false)
const resetStep = ref(1)
const resetEmail = ref('')
const resetCode = ref('')
const newPassword = ref('')
const successMessage = ref(null)

function toggleAuthMode() {
  isRegistering.value = !isRegistering.value
  error.value = null
  clearForm()
}

function clearForm() {
  username.value = ''
  password.value = ''
  email.value = ''
  displayName.value = ''
  phone.value = ''
  showPassword.value = false
  acceptTerms.value = false
}

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
.auth-container {
  display: flex;
  min-height: 100vh;
  background: var(--color-surface-light);
}

.auth-hero {
  position: relative;
  width: 50%;
  display: none;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px;
  background: var(--color-background-dark);
  color: var(--color-text-white);
  overflow: hidden;
}

@media (min-width: 1024px) {
  .auth-hero {
    display: flex;
  }
}

.hero-background {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.hero-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.4;
}

.hero-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, var(--color-background-dark), transparent, transparent);
}

.hero-content {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 100%;
}

.hero-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-icon {
  width: 32px;
  height: 32px;
  color: var(--color-primary);
}

.brand-name {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.hero-message {
  max-width: 540px;
}

.hero-message h1 {
  font-size: 40px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.03em;
  margin-bottom: 24px;
}

.hero-message > p {
  font-size: 18px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.7);
  margin-bottom: 24px;
}

.testimonial {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar-circle {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(19, 91, 236, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  font-weight: 700;
}

.testimonial-info .name {
  font-weight: 600;
  color: var(--color-text-white);
}

.testimonial-info .role {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
}

.hero-footer {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.5);
}

.auth-form-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  overflow-y: auto;
  padding: 32px 24px;
}

.auth-form-wrapper {
  width: 100%;
  max-width: 480px;
}

.mobile-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 32px;
  color: var(--color-text-main);
}

@media (min-width: 1024px) {
  .mobile-brand {
    display: none;
  }
}

.brand-icon-mobile {
  width: 24px;
  height: 24px;
  color: var(--color-primary);
}

.mobile-brand h2 {
  font-size: 20px;
  font-weight: 700;
}

.auth-header {
  margin-bottom: 32px;
}

.auth-header h2 {
  font-size: 30px;
  font-weight: 800;
  color: var(--color-text-main);
  margin-bottom: 8px;
  letter-spacing: -0.03em;
}

.auth-header p {
  font-size: 16px;
  color: var(--color-text-sub);
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

.alert-error {
  background: #fef2f2;
  color: var(--color-error);
}

.alert-success {
  background: #dcfce7;
  color: var(--color-success);
}

.auth-form {
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
  color: var(--color-text-main);
}

.optional {
  font-weight: 400;
  color: var(--color-text-sub);
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.form-input {
  width: 100%;
  padding: 12px 16px;
  border: 1.5px solid var(--color-border-light);
  border-radius: 12px;
  font-size: 15px;
  color: var(--color-text-main);
  background: var(--color-surface-light);
  transition: all var(--transition-fast);
}

.form-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 4px var(--color-primary-light);
}

.form-input::placeholder {
  color: var(--color-text-sub);
}

.input-wrapper .form-input {
  padding-right: 48px;
}

.input-icon {
  position: absolute;
  right: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-sub);
  pointer-events: none;
}

.input-icon.clickable {
  pointer-events: auto;
  cursor: pointer;
  background: none;
  border: none;
  padding: 4px;
  transition: color var(--transition-fast);
}

.input-icon.clickable:hover {
  color: var(--color-primary);
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 8px;
}

.checkbox {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  border: 1.5px solid var(--color-border-light);
  cursor: pointer;
  accent-color: var(--color-primary);
}

.remember-me label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-sub);
  cursor: pointer;
}

.terms-checkbox {
  display: flex;
  align-items: start;
  gap: 12px;
  padding: 8px 0;
}

.terms-checkbox label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-main);
}

.link {
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 600;
}

.link:hover {
  text-decoration: underline;
}

.link-button {
  background: none;
  border: none;
  color: var(--color-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  transition: color var(--transition-fast);
}

.link-button:hover {
  color: var(--color-primary-dark);
  text-decoration: underline;
}

.btn-primary {
  width: 100%;
  padding: 12px 24px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-top: 8px;
  box-shadow: var(--shadow-primary);
}

.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}

.btn-primary:active:not(:disabled) {
  transform: translateY(0);
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
  width: 100%;
  padding: 12px;
  background: none;
  border: none;
  color: var(--color-primary);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
  border-radius: 8px;
}

.btn-text:hover {
  background: var(--color-primary-light);
}

.auth-switch {
  margin-top: 8px;
  text-align: center;
}

.auth-switch p {
  font-size: 14px;
  color: var(--color-text-sub);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--transition-normal), transform var(--transition-normal);
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
