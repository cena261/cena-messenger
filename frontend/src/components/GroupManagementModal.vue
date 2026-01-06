<template>
  <div v-if="isOpen" class="modal-overlay" @click="handleOverlayClick">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h2>Thông tin nhóm</h2>
        <button class="close-btn" @click="handleClose">
          <X :size="24" />
        </button>
      </div>

      <div class="modal-body">
        <div v-if="conversation" class="group-info-card">
          <div class="group-avatar-section">
            <div v-if="displayAvatarUrl" class="group-avatar-large-img">
              <img :src="displayAvatarUrl" :alt="conversation.name" />
            </div>
            <div v-else class="group-avatar-large">
              {{ conversation.name?.charAt(0).toUpperCase() || 'G' }}
            </div>
          </div>

          <div v-if="!editingInfo" class="group-name-display">
            <h3>{{ conversation.name || 'Nhóm không tên' }}</h3>
            <p>{{ members.length }} thành viên</p>
            <button v-if="canEditInfo" @click="startEditInfo" class="edit-info-btn">
              <Edit2 :size="16" />
              Chỉnh sửa
            </button>
          </div>

          <div v-else class="group-edit-form">
            <input
              v-model="editGroupName"
              placeholder="Tên nhóm"
              class="form-input"
              autofocus
            />
            
            <div class="avatar-upload-section">
              <label class="avatar-upload-label">Ảnh đại diện nhóm (không bắt buộc)</label>
              <div class="avatar-upload-area">
                <input
                  ref="avatarFileInput"
                  type="file"
                  accept="image/*"
                  @change="handleAvatarSelect"
                  class="file-input"
                />
                <div v-if="avatarPreview" class="avatar-preview">
                  <img :src="avatarPreview" alt="Preview" />
                  <button @click="clearAvatar" class="clear-avatar-btn" type="button">
                    <X :size="16" />
                  </button>
                </div>
                <div v-else class="upload-placeholder" @click="triggerFileInput">
                  <Upload :size="32" />
                  <p>Click để chọn ảnh</p>
                  <span>PNG, JPG, GIF, WEBP (Max 5MB)</span>
                </div>
              </div>
            </div>
            
            <div class="edit-actions">
              <button @click="saveGroupInfo" class="save-btn" :disabled="!editGroupName.trim()">
                <Check :size="16" />
                Lưu
              </button>
              <button @click="cancelEditInfo" class="cancel-btn">
                <X :size="16" />
                Hủy
              </button>
            </div>
          </div>
        </div>

        <div class="members-section">
          <h4 class="section-title">Thành viên ({{ members.length }})</h4>
          <div class="members-list">
            <div
              v-for="member in members"
              :key="member.userId"
              class="member-item"
            >
              <div class="member-avatar">
                {{ getMemberInitial(member) }}
              </div>

              <div class="member-info">
                <div class="member-name">
                  {{ member.displayName || member.username }}
                  <span v-if="member.userId === currentUserId" class="you-badge">(Bạn)</span>
                </div>
                <div class="member-username">@{{ member.username }}</div>
              </div>

              <div class="member-role-badge" :class="member.role.toLowerCase()">
                <Crown v-if="member.role === 'OWNER'" :size="12" />
                <Shield v-else-if="member.role === 'ADMIN'" :size="12" />
                <User v-else :size="12" />
                <span>{{ getRoleLabel(member.role) }}</span>
              </div>

              <div v-if="member.userId !== currentUserId && hasPermissionForMember(member)" class="member-actions">
                <button @click="toggleMemberMenu(member.userId)" class="action-menu-btn">
                  <MoreVertical :size="20" />
                </button>

                <div v-if="activeMemberMenu === member.userId" class="action-dropdown">
                  <button
                    v-if="canChangeRole && member.role !== 'OWNER'"
                    @click="openRoleChangeDialog(member)"
                    class="dropdown-item"
                  >
                    <Shield :size="16" />
                    Thay đổi vai trò
                  </button>

                  <button
                    v-if="canTransferOwnership && member.role !== 'OWNER'"
                    @click="openTransferDialog(member)"
                    class="dropdown-item transfer"
                  >
                    <Crown :size="16" />
                    Chuyển quyền chủ nhóm
                  </button>

                  <button
                    v-if="canKickMember(member)"
                    @click="openKickDialog(member)"
                    class="dropdown-item danger"
                  >
                    <UserMinus :size="16" />
                    Xóa khỏi nhóm
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="danger-zone">
          <button
            @click="openLeaveDialog"
            :disabled="!canLeaveGroup"
            class="leave-btn"
            :title="!canLeaveGroup ? 'Chủ nhóm phải chuyển quyền trước khi rời' : ''"
          >
            <LogOut :size="20" />
            Rời khỏi nhóm
          </button>
          <p v-if="!canLeaveGroup" class="leave-warning">
            Bạn phải chuyển quyền chủ nhóm trước khi rời nhóm
          </p>
        </div>
      </div>
    </div>

    <ConfirmDialog
      v-if="confirmDialog.show"
      :title="confirmDialog.title"
      :message="confirmDialog.message"
      :confirmText="confirmDialog.confirmText"
      :isDanger="confirmDialog.isDanger"
      :isLoading="confirmDialog.isLoading"
      @confirm="confirmDialog.onConfirm"
      @cancel="closeConfirmDialog"
    />

    <RoleChangeDialog
      v-if="roleDialog.show"
      :member="roleDialog.member"
      :currentRole="roleDialog.currentRole"
      @confirm="handleRoleChangeConfirm"
      @cancel="closeRoleDialog"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import * as groupsApi from '../api/groups'
import * as mediaApi from '../api/media'
import { X, Edit2, Check, Crown, Shield, User, MoreVertical, UserMinus, LogOut, Upload } from 'lucide-vue-next'
import ConfirmDialog from './ConfirmDialog.vue'
import RoleChangeDialog from './RoleChangeDialog.vue'

const props = defineProps({
  isOpen: Boolean,
  conversation: Object
})

const emit = defineEmits(['close', 'groupUpdated'])

const authStore = useAuthStore()

const editingInfo = ref(false)
const editGroupName = ref('')
const editGroupAvatar = ref('')
const avatarFile = ref(null)
const avatarPreview = ref(null)
const avatarFileInput = ref(null)
const activeMemberMenu = ref(null)

const confirmDialog = ref({
  show: false,
  title: '',
  message: '',
  confirmText: '',
  isDanger: false,
  isLoading: false,
  onConfirm: null
})

const roleDialog = ref({
  show: false,
  member: null,
  currentRole: ''
})

const currentUserId = computed(() => authStore.user?.id)

const members = computed(() => {
  return props.conversation?.members || []
})

const currentUserMember = computed(() => {
  return members.value.find(m => m.userId === currentUserId.value)
})

const currentUserRole = computed(() => {
  return currentUserMember.value?.role || 'MEMBER'
})

const canEditInfo = computed(() => {
  return currentUserRole.value === 'OWNER' || currentUserRole.value === 'ADMIN'
})

const canChangeRole = computed(() => {
  return currentUserRole.value === 'OWNER'
})

const canTransferOwnership = computed(() => {
  return currentUserRole.value === 'OWNER'
})

const isOnlyMember = computed(() => {
  return members.value.length === 1
})

const canLeaveGroup = computed(() => {
  if (currentUserRole.value !== 'OWNER') return true
  return isOnlyMember.value
})

const displayAvatarUrl = computed(() => {
  // Show preview if new avatar is being uploaded
  if (avatarPreview.value) {
    return avatarPreview.value
  }
  // Show existing avatar from conversation
  return props.conversation?.avatarUrl || null
})

function getMemberInitial(member) {
  const name = member.displayName || member.username || '?'
  return name.charAt(0).toUpperCase()
}

function getRoleLabel(role) {
  const labels = {
    OWNER: 'Chủ nhóm',
    ADMIN: 'Quản trị viên',
    MEMBER: 'Thành viên'
  }
  return labels[role] || role
}

function hasPermissionForMember(member) {
  if (member.role === 'OWNER') return false
  if (currentUserRole.value === 'OWNER') return true
  if (currentUserRole.value === 'ADMIN' && member.role !== 'ADMIN') return true
  return false
}

function canKickMember(member) {
  if (member.role === 'OWNER') return false
  if (currentUserRole.value === 'OWNER') return true
  if (currentUserRole.value === 'ADMIN' && member.role !== 'ADMIN') return true
  return false
}

function toggleMemberMenu(userId) {
  activeMemberMenu.value = activeMemberMenu.value === userId ? null : userId
}

watch(() => props.isOpen, (newValue) => {
  if (newValue && props.conversation) {
    editGroupName.value = props.conversation.name || ''
    editGroupAvatar.value = props.conversation.avatarUrl || ''
    editingInfo.value = false
    activeMemberMenu.value = null
  }
})

// ESC key handler
function handleEscKey(event) {
  if (event.key === 'Escape') {
    handleClose()
  }
}

watch(() => props.isOpen, (isOpen) => {
  if (isOpen) {
    document.addEventListener('keydown', handleEscKey)
  } else {
    document.removeEventListener('keydown', handleEscKey)
  }
})

function handleClose() {
  emit('close')
}

function handleOverlayClick(event) {
  if (event.target === event.currentTarget) {
    handleClose()
  }
}

function startEditInfo() {
  editingInfo.value = true
  editGroupName.value = props.conversation.name || ''
  editGroupAvatar.value = props.conversation.avatarUrl || ''
}

function cancelEditInfo() {
  editingInfo.value = false
  editGroupName.value = props.conversation.name || ''
  editGroupAvatar.value = props.conversation.avatarUrl || ''
  clearAvatar()
}

function triggerFileInput() {
  avatarFileInput.value?.click()
}

function handleAvatarSelect(event) {
  const file = event.target.files?.[0]
  if (!file) return

  // Validate file size (5MB)
  if (file.size > 5 * 1024 * 1024) {
    alert('Kích thước file không được vượt quá 5MB')
    return
  }

  // Validate file type
  if (!file.type.startsWith('image/')) {
    alert('Chỉ chấp nhận file hình ảnh')
    return
  }

  avatarFile.value = file
  
  // Create preview
  const reader = new FileReader()
  reader.onload = (e) => {
    avatarPreview.value = e.target.result
  }
  reader.readAsDataURL(file)
}

function clearAvatar() {
  avatarFile.value = null
  avatarPreview.value = null
  editGroupAvatar.value = ''
  if (avatarFileInput.value) {
    avatarFileInput.value.value = ''
  }
}

async function saveGroupInfo() {
  if (!editGroupName.value.trim()) return

  try {
    let finalAvatarUrl = editGroupAvatar.value

    // Upload avatar if new file is selected
    if (avatarFile.value) {
      const presignedData = await mediaApi.getPresignedUrl(
        props.conversation.id,
        avatarFile.value.name,
        avatarFile.value.size,
        avatarFile.value.type
      )

      // Upload file to MinIO
      await fetch(presignedData.presignedUrl, {
        method: 'PUT',
        body: avatarFile.value,
        headers: {
          'Content-Type': avatarFile.value.type
        }
      })

      // Construct avatar URL (assuming MinIO host from env or config)
      const minioHost = import.meta.env.VITE_MINIO_HOST || 'http://localhost:9000'
      finalAvatarUrl = `${minioHost}/chat-media/${presignedData.fileKey}`
    }

    await groupsApi.updateGroupInfo(
      props.conversation.id,
      editGroupName.value.trim(),
      finalAvatarUrl || null
    )
    
    editingInfo.value = false
    clearAvatar()
    emit('groupUpdated')
  } catch (error) {
    console.error('Failed to update group info:', error)
    alert('Không thể cập nhật thông tin nhóm. Vui lòng thử lại.')
  }
}

function openRoleChangeDialog(member) {
  activeMemberMenu.value = null
  roleDialog.value = {
    show: true,
    member,
    currentRole: member.role
  }
}

function closeRoleDialog() {
  roleDialog.value = {
    show: false,
    member: null,
    currentRole: ''
  }
}

async function handleRoleChangeConfirm(newRole) {
  const member = roleDialog.value.member
  closeRoleDialog()

  try {
    await groupsApi.changeRole(props.conversation.id, member.userId, newRole)
    emit('groupUpdated')
  } catch (error) {
    console.error('Failed to change role:', error)
  }
}

function openTransferDialog(member) {
  activeMemberMenu.value = null
  confirmDialog.value = {
    show: true,
    title: 'Chuyển quyền chủ nhóm',
    message: `Bạn có chắc muốn chuyển quyền chủ nhóm cho ${member.displayName || member.username}? Bạn sẽ trở thành thành viên thường.`,
    confirmText: 'Chuyển quyền',
    isDanger: true,
    isLoading: false,
    onConfirm: () => handleTransferOwnership(member)
  }
}

async function handleTransferOwnership(member) {
  confirmDialog.value.isLoading = true

  try {
    await groupsApi.transferOwnership(props.conversation.id, member.userId)
    closeConfirmDialog()
    emit('groupUpdated')
  } catch (error) {
    console.error('Failed to transfer ownership:', error)
    confirmDialog.value.isLoading = false
  }
}

function openKickDialog(member) {
  activeMemberMenu.value = null
  confirmDialog.value = {
    show: true,
    title: 'Xóa khỏi nhóm',
    message: `Bạn có chắc muốn xóa ${member.displayName || member.username} khỏi nhóm?`,
    confirmText: 'Xóa',
    isDanger: true,
    isLoading: false,
    onConfirm: () => handleKick(member)
  }
}

async function handleKick(member) {
  confirmDialog.value.isLoading = true

  try {
    await groupsApi.kickMember(props.conversation.id, member.userId)
    closeConfirmDialog()
    emit('groupUpdated')
  } catch (error) {
    console.error('Failed to kick member:', error)
    confirmDialog.value.isLoading = false
  }
}

function openLeaveDialog() {
  confirmDialog.value = {
    show: true,
    title: 'Rời khỏi nhóm',
    message: 'Bạn có chắc muốn rời khỏi nhóm này? Bạn sẽ không thể xem tin nhắn trong nhóm nữa.',
    confirmText: 'Rời nhóm',
    isDanger: true,
    isLoading: false,
    onConfirm: handleLeaveGroup
  }
}

async function handleLeaveGroup() {
  confirmDialog.value.isLoading = true

  try {
    await groupsApi.leaveGroup(props.conversation.id)
    closeConfirmDialog()
    
    // Clear active conversation to reset chat view
    const conversationsStore = useConversationsStore()
    conversationsStore.clearActiveConversation()
    
    emit('groupUpdated')
    emit('close')
  } catch (error) {
    console.error('Failed to leave group:', error)
    confirmDialog.value.isLoading = false
  }
}

function closeConfirmDialog() {
  confirmDialog.value = {
    show: false,
    title: '',
    message: '',
    confirmText: '',
    isDanger: false,
    isLoading: false,
    onConfirm: null
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
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-content {
  background: var(--color-surface);
  border-radius: 20px;
  width: 540px;
  max-width: 90vw;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 24px 20px;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.02em;
}

.close-btn {
  background: transparent;
  border: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  cursor: pointer;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
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

.group-info-card {
  text-align: center;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 24px;
}

.group-avatar-section {
  margin-bottom: 16px;
}

.group-avatar-large {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: white;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 700;
  box-shadow: var(--shadow-md);
}

.group-avatar-large-img {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: var(--shadow-md);
  display: inline-block;
}

.group-avatar-large-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.group-name-display h3 {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 4px 0;
}

.group-name-display p {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0 0 16px 0;
}

.edit-info-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.edit-info-btn:hover {
  background: var(--color-surface-hover);
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.group-edit-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 320px;
  margin: 0 auto;
}

.form-input {
  padding: 12px 16px;
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  font-size: 15px;
  background: var(--color-input-bg);
  color: var(--color-text-primary);
  transition: all var(--transition-fast);
}

.form-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 4px var(--color-primary-light);
  background: var(--color-surface);
}

.edit-actions {
  display: flex;
  gap: 8px;
}

.save-btn,
.cancel-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.save-btn {
  background: var(--color-primary);
  color: white;
  border: none;
}

.save-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}

.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.cancel-btn {
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text-primary);
}

.cancel-btn:hover {
  background: var(--color-surface-hover);
}

.section-title {
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--color-text-secondary);
  margin: 0 0 12px 0;
}

.members-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.member-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  transition: all var(--transition-fast);
  position: relative;
}

.member-item:hover {
  background: var(--color-surface-hover);
}

.member-avatar {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
}

.member-info {
  flex: 1;
  min-width: 0;
}

.member-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.you-badge {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.member-username {
  font-size: 13px;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-role-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border-radius: 14px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  flex-shrink: 0;
}

.member-role-badge.owner {
  background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
  color: white;
  box-shadow: 0 2px 8px rgba(251, 191, 36, 0.3);
}

.member-role-badge.admin {
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  color: white;
  box-shadow: 0 2px 8px rgba(96, 165, 250, 0.3);
}

.member-role-badge.member {
  background: var(--color-surface-hover);
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
}

.member-actions {
  position: relative;
  flex-shrink: 0;
}

.action-menu-btn {
  background: transparent;
  border: none;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.action-menu-btn:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}

.action-dropdown {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.15), 0 10px 10px -5px rgba(0, 0, 0, 0.08);
  min-width: 220px;
  z-index: 100;
  animation: fadeIn 0.15s ease;
  overflow: hidden;
}

.dropdown-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: transparent;
  border: none;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
  text-align: left;
}

.dropdown-item:first-child {
  border-radius: 12px 12px 0 0;
}

.dropdown-item:last-child {
  border-radius: 0 0 12px 12px;
}

.dropdown-item:hover {
  background: var(--color-surface-hover);
}

.dropdown-item.transfer {
  color: #f59e0b;
}

.dropdown-item.transfer:hover {
  background: #fef3c7;
}

.dropdown-item.danger {
  color: var(--color-error);
}

.dropdown-item.danger:hover {
  background: #fef2f2;
}

.danger-zone {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--color-border);
}

.leave-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px;
  background: white;
  border: 2px solid var(--color-error);
  border-radius: 12px;
  color: var(--color-error);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.leave-btn:hover:not(:disabled) {
  background: var(--color-error);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 8px 16px rgba(220, 38, 38, 0.25);
}

.leave-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  transform: none;
  border-style: dashed;
}

.leave-warning {
  margin-top: 12px;
  padding: 12px;
  background: #fef3c7;
  border: 1px solid #fcd34d;
  border-radius: 10px;
  color: #92400e;
  font-size: 13px;
  text-align: center;
  margin-bottom: 0;
}

/* Avatar Upload Styles */
.avatar-upload-section {
  margin-bottom: 16px;
}

.avatar-upload-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-bottom: 10px;
}

.avatar-upload-area {
  position: relative;
}

.file-input {
  display: none;
}

.upload-placeholder {
  border: 2px dashed var(--color-border);
  border-radius: 12px;
  padding: 32px 20px;
  text-align: center;
  cursor: pointer;
  transition: all var(--transition-fast);
  background: var(--color-surface-hover);
}

.upload-placeholder:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.upload-placeholder svg {
  color: var(--color-text-tertiary);
  margin-bottom: 12px;
}

.upload-placeholder p {
  margin: 0 0 4px 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.upload-placeholder span {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.avatar-preview {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  border: 2px solid var(--color-border);
  width: 200px;
  height: 200px;
  margin: 0 auto;
  background: var(--color-surface-hover);
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.clear-avatar-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  border: none;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.clear-avatar-btn:hover {
  background: rgba(220, 38, 38, 0.9);
  transform: scale(1.1);
}
</style>
