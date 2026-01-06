<template>
  <div v-if="isOpen" class="modal-overlay" @click="handleOverlayClick">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h2>Cài đặt hồ sơ</h2>
        <button class="close-btn" @click="handleClose">
          <X :size="24" />
        </button>
      </div>

      <div class="modal-body">
        <div v-if="error" class="error-message">
          {{ error }}
        </div>

        <div v-if="successMessage" class="success-message">
          {{ successMessage }}
        </div>

        <div class="profile-avatar-section">
          <div class="avatar-container">
            <div v-if="previewAvatarUrl || currentUser?.avatarUrl" class="avatar-large-img">
              <img :src="previewAvatarUrl || currentUser?.avatarUrl" :alt="currentUser?.displayName || currentUser?.username" />
            </div>
            <div v-else class="avatar-large">
              {{ getInitial() }}
            </div>
            <div v-if="isUploadingAvatar" class="avatar-uploading-overlay">
              <div class="spinner"></div>
            </div>
          </div>

          <div class="avatar-actions">
            <label class="upload-avatar-btn">
              <input
                type="file"
                accept="image/jpeg,image/jpg,image/png,image/gif,image/webp"
                @change="handleAvatarSelect"
                :disabled="isUploadingAvatar || isUpdating"
                style="display: none"
              />
              {{ isUploadingAvatar ? 'Đang tải lên...' : 'Thay đổi avatar' }}
            </label>
            <button
              v-if="previewAvatarUrl || currentUser?.avatarUrl"
              @click="handleRemoveAvatar"
              :disabled="isUploadingAvatar || isUpdating"
              class="remove-avatar-btn"
            >
              Xóa
            </button>
          </div>
          <p class="avatar-hint">Tối đa 5MB. Hỗ trợ: JPG, PNG, GIF, WebP</p>
        </div>

        <div class="profile-fields">
          <div class="form-group">
            <label>Tên người dùng</label>
            <input type="text" :value="currentUser?.username" disabled class="form-input read-only" />
            <span class="field-hint">Không thể thay đổi</span>
          </div>

          <div class="form-group">
            <label>Email</label>
            <input type="email" :value="currentUser?.email" disabled class="form-input read-only" />
            <span class="field-hint">Không thể thay đổi</span>
          </div>

          <div class="form-group">
            <label>Tên hiển thị</label>
            <input
              type="text"
              v-model="editDisplayName"
              :disabled="isUpdating || isUploadingAvatar"
              placeholder="Nhập tên hiển thị của bạn"
              maxlength="50"
              class="form-input"
            />
            <span class="field-hint">Cách người khác thấy bạn trong ứng dụng</span>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button @click="handleLogout" :disabled="isUpdating || isUploadingAvatar" class="logout-btn">
          <LogOut :size="18" />
          Đăng xuất
        </button>
        <div class="footer-spacer"></div>
        <button @click="handleClose" :disabled="isUpdating || isUploadingAvatar" class="cancel-btn">
          Hủy
        </button>
        <button
          @click="handleSave"
          :disabled="!hasChanges || isUpdating || isUploadingAvatar"
          class="save-btn"
        >
          {{ isUpdating ? 'Đang lưu...' : 'Lưu thay đổi' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import * as usersApi from '../api/users'
import { LogOut, X } from 'lucide-vue-next'

const props = defineProps({
  isOpen: Boolean
})

const emit = defineEmits(['close'])

const router = useRouter()
const authStore = useAuthStore()

const currentUser = computed(() => authStore.user)

const editDisplayName = ref('')
const previewAvatarUrl = ref(null)
const pendingAvatarUrl = ref(null)
const isUpdating = ref(false)
const isUploadingAvatar = ref(false)
const error = ref(null)
const successMessage = ref(null)

const hasChanges = computed(() => {
  const displayNameChanged = editDisplayName.value !== (currentUser.value?.displayName || '')
  const avatarChanged = pendingAvatarUrl.value !== null
  return displayNameChanged || avatarChanged
})

watch(() => props.isOpen, (newValue) => {
  if (newValue) {
    resetForm()
  }
})

watch(() => currentUser.value, (newUser) => {
  if (newUser && props.isOpen) {
    editDisplayName.value = newUser.displayName || ''
  }
}, { immediate: true })

function resetForm() {
  editDisplayName.value = currentUser.value?.displayName || ''
  previewAvatarUrl.value = null
  pendingAvatarUrl.value = null
  error.value = null
  successMessage.value = null
}

function getInitial() {
  const name = currentUser.value?.displayName || currentUser.value?.username || 'U'
  return name.charAt(0).toUpperCase()
}

async function handleAvatarSelect(event) {
  const file = event.target.files?.[0]
  if (!file) return

  error.value = null
  successMessage.value = null

  if (file.size > 5 * 1024 * 1024) {
    error.value = 'Kích thước file phải nhỏ hơn 5MB'
    event.target.value = ''
    return
  }

  const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    error.value = 'Chỉ hỗ trợ ảnh JPG, PNG, GIF và WebP'
    event.target.value = ''
    return
  }

  isUploadingAvatar.value = true

  try {
    const presignedResponse = await usersApi.requestAvatarPresignedUrl(
      file.name,
      file.size,
      file.type
    )

    const { presignedUrl, fileKey } = presignedResponse.data

    await usersApi.uploadAvatarToObjectStorage(presignedUrl, file, file.type)

    const minioHost = import.meta.env.VITE_MINIO_URL || 'http://localhost:9000'
    const avatarUrl = `${minioHost}/chat-media/${fileKey}`

    previewAvatarUrl.value = avatarUrl
    pendingAvatarUrl.value = avatarUrl
  } catch (err) {
    console.error('Avatar upload failed:', err)
    error.value = err.response?.data?.message || err.message || 'Không thể tải lên avatar'
    previewAvatarUrl.value = null
    pendingAvatarUrl.value = null
  } finally {
    isUploadingAvatar.value = false
    event.target.value = ''
  }
}

function handleRemoveAvatar() {
  previewAvatarUrl.value = null
  pendingAvatarUrl.value = ''
}

async function handleSave() {
  if (!hasChanges.value || isUpdating.value || isUploadingAvatar.value) return

  isUpdating.value = true
  error.value = null
  successMessage.value = null

  try {
    const displayNameToUpdate = editDisplayName.value !== (currentUser.value?.displayName || '')
      ? editDisplayName.value
      : undefined

    const avatarUrlToUpdate = pendingAvatarUrl.value !== null
      ? pendingAvatarUrl.value
      : undefined

    const response = await usersApi.updateProfile(displayNameToUpdate, avatarUrlToUpdate)

    authStore.user = response.data

    successMessage.value = 'Cập nhật hồ sơ thành công'

    setTimeout(() => {
      handleClose()
    }, 1000)
  } catch (err) {
    console.error('Profile update failed:', err)
    error.value = err.response?.data?.message || err.message || 'Không thể cập nhật hồ sơ'
  } finally {
    isUpdating.value = false
  }
}

async function handleLogout() {
  try {
    await authStore.logout()
    emit('close')
    router.push('/login')
  } catch (error) {
    console.error('Logout failed:', error)
    error.value = 'Không thể đăng xuất. Vui lòng thử lại.'
  }
}

function handleClose() {
  if (!isUpdating.value && !isUploadingAvatar.value) {
    emit('close')
  }
}

function handleOverlayClick(event) {
  if (event.target === event.currentTarget) {
    handleClose()
  }
}

function handleEscKey(event) {
  if (event.key === 'Escape') {
    handleClose()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleEscKey)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscKey)
})
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
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-content {
  background: var(--color-surface);
  border-radius: 16px;
  width: 90%;
  max-width: 520px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.modal-header {
  padding: 20px 24px;
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
}

.close-btn {
  background: transparent;
  border: none;
  padding: 4px;
  cursor: pointer;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
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
  background: var(--color-error-bg);
  color: var(--color-error);
  padding: 12px 16px;
  border-radius: 10px;
  margin-bottom: 20px;
  font-size: 14px;
  border: 1px solid var(--color-error);
}

.success-message {
  background: var(--color-success-bg);
  color: var(--color-success);
  padding: 12px 16px;
  border-radius: 10px;
  margin-bottom: 20px;
  font-size: 14px;
  border: 1px solid var(--color-success);
}

.profile-avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 28px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-border);
}

.avatar-container {
  position: relative;
  margin-bottom: 16px;
}

.avatar-large,
.avatar-large-img {
  width: 100px;
  height: 100px;
  border-radius: 16px;
  overflow: hidden;
}

.avatar-large {
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.avatar-large-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-uploading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
}

.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.avatar-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 8px;
}

.upload-avatar-btn {
  padding: 10px 18px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all var(--transition-fast);
  display: inline-block;
}

.upload-avatar-btn:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}

.remove-avatar-btn {
  padding: 10px 18px;
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all var(--transition-fast);
}

.remove-avatar-btn:hover:not(:disabled) {
  background: var(--color-error-bg);
  color: var(--color-error);
  border-color: var(--color-error);
}

.remove-avatar-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.avatar-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  text-align: center;
  margin: 0;
}

.profile-fields {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-group label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.form-input {
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  font-size: 15px;
  transition: all var(--transition-fast);
  background: var(--color-input-bg);
  color: var(--color-text-primary);
}

.form-input:focus:not(:disabled) {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.form-input.read-only {
  background: var(--color-surface-hover);
  color: var(--color-text-tertiary);
  cursor: not-allowed;
}

.field-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.modal-footer {
  padding: 16px 24px;
  border-top: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  gap: 10px;
}

.logout-btn {
  padding: 10px 16px;
  background: transparent;
  border: 1px solid var(--color-error);
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-error);
  transition: all var(--transition-fast);
  display: flex;
  align-items: center;
  gap: 6px;
}

.logout-btn:hover:not(:disabled) {
  background: var(--color-error);
  color: white;
}

.logout-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.footer-spacer {
  flex: 1;
}

.cancel-btn {
  padding: 10px 20px;
  background: var(--color-surface-hover);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  transition: all var(--transition-fast);
}

.cancel-btn:hover:not(:disabled) {
  background: var(--color-surface-active);
}

.cancel-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.save-btn {
  padding: 10px 20px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all var(--transition-fast);
}

.save-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}

.save-btn:disabled {
  background: var(--color-border);
  color: var(--color-text-tertiary);
  cursor: not-allowed;
  transform: none;
}
</style>
