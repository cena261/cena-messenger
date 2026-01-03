<template>
  <div v-if="isOpen" class="modal-overlay" @click="handleClose">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h2>Profile Settings</h2>
        <button @click="handleClose" class="close-btn">✕</button>
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
            <div v-if="previewAvatarUrl || currentUser?.avatarUrl" class="avatar-large">
              <img :src="previewAvatarUrl || currentUser?.avatarUrl" :alt="currentUser?.displayName || currentUser?.username" />
            </div>
            <div v-else class="avatar-large avatar-placeholder">
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
              {{ isUploadingAvatar ? 'Uploading...' : 'Change Avatar' }}
            </label>
            <button
              v-if="previewAvatarUrl || currentUser?.avatarUrl"
              @click="handleRemoveAvatar"
              :disabled="isUploadingAvatar || isUpdating"
              class="remove-avatar-btn"
            >
              Remove
            </button>
          </div>
          <p class="avatar-hint">Max 5MB. Supported: JPG, PNG, GIF, WebP</p>
        </div>

        <div class="profile-fields">
          <div class="form-group">
            <label>Username</label>
            <input type="text" :value="currentUser?.username" disabled class="read-only-input" />
            <span class="field-hint">Cannot be changed</span>
          </div>

          <div class="form-group">
            <label>Email</label>
            <input type="email" :value="currentUser?.email" disabled class="read-only-input" />
            <span class="field-hint">Cannot be changed</span>
          </div>

          <div class="form-group">
            <label>Display Name</label>
            <input
              type="text"
              v-model="editDisplayName"
              :disabled="isUpdating || isUploadingAvatar"
              placeholder="Enter your display name"
              maxlength="50"
            />
            <span class="field-hint">How others see you in the app</span>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button @click="handleClose" :disabled="isUpdating || isUploadingAvatar" class="cancel-btn">
          Cancel
        </button>
        <button
          @click="handleSave"
          :disabled="!hasChanges || isUpdating || isUploadingAvatar"
          class="save-btn"
        >
          {{ isUpdating ? 'Saving...' : 'Save Changes' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import * as usersApi from '../api/users'

const props = defineProps({
  isOpen: Boolean
})

const emit = defineEmits(['close'])

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
    error.value = 'File size must be less than 5MB'
    event.target.value = ''
    return
  }

  const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    error.value = 'Only JPG, PNG, GIF, and WebP images are supported'
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
    error.value = err.response?.data?.message || err.message || 'Failed to upload avatar'
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

    successMessage.value = 'Profile updated successfully'

    setTimeout(() => {
      handleClose()
    }, 1000)
  } catch (err) {
    console.error('Profile update failed:', err)
    error.value = err.response?.data?.message || err.message || 'Failed to update profile'
  } finally {
    isUpdating.value = false
  }
}

function handleClose() {
  if (!isUpdating.value && !isUploadingAvatar.value) {
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
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background: #FFFFFF;
  border-radius: 20px;
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
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
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s ease;
}

.close-btn:hover {
  color: var(--color-text-primary);
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
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

.success-message {
  background: #D1FAE5;
  color: #065F46;
  padding: 14px 16px;
  border-radius: 12px;
  margin-bottom: 20px;
  font-size: 14px;
  border: 1px solid #6EE7B7;
}

.profile-avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-border);
}

.avatar-container {
  position: relative;
  margin-bottom: 16px;
}

.avatar-large {
  width: 120px;
  height: 120px;
  border-radius: 20px;
  overflow: hidden;
  background: var(--color-gradient-warm);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-large img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-large.avatar-placeholder {
  color: white;
  font-size: 48px;
  font-weight: 700;
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
  border-radius: 20px;
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
  gap: 12px;
  margin-bottom: 8px;
}

.upload-avatar-btn {
  padding: 10px 20px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
  display: inline-block;
}

.upload-avatar-btn:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(224, 120, 86, 0.3);
}

.upload-avatar-btn input:disabled + label,
.upload-avatar-btn:has(input:disabled) {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.remove-avatar-btn {
  padding: 10px 20px;
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.remove-avatar-btn:hover:not(:disabled) {
  background: var(--color-border);
}

.remove-avatar-btn:disabled {
  opacity: 0.6;
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

.form-group input:focus:not(:disabled) {
  outline: none;
  border-color: var(--color-primary);
  background: white;
  box-shadow: 0 0 0 4px rgba(224, 120, 86, 0.1);
}

.form-group input:disabled {
  background: var(--color-bg-hover);
  color: var(--color-text-secondary);
  cursor: not-allowed;
}

.form-group input.read-only-input {
  background: var(--color-bg-hover);
  color: var(--color-text-secondary);
  cursor: not-allowed;
}

.field-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
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

.cancel-btn:hover:not(:disabled) {
  background: var(--color-border);
}

.cancel-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.save-btn {
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

.save-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(224, 120, 86, 0.3);
}

.save-btn:disabled {
  background: var(--color-border);
  color: var(--color-text-tertiary);
  cursor: not-allowed;
  transform: none;
}

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
