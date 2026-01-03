<template>
  <div v-if="isOpen" class="modal-overlay" @click="handleClose">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h2>Group Settings</h2>
        <button @click="handleClose" class="close-btn">✕</button>
      </div>

      <div class="modal-body">
        <div v-if="conversation" class="group-info-section">
          <h3>Group Information</h3>
          <div class="group-info">
            <div class="info-item">
              <label>Group Name:</label>
              <div v-if="!editingInfo" class="info-value">
                {{ conversation.name }}
                <button v-if="canEditInfo" @click="startEditInfo" class="edit-info-btn">Edit</button>
              </div>
              <div v-else class="edit-info-form">
                <input v-model="editGroupName" placeholder="Group name" />
                <input v-model="editGroupAvatar" placeholder="Avatar URL (optional)" />
                <div class="edit-info-actions">
                  <button @click="saveGroupInfo" class="save-btn">Save</button>
                  <button @click="cancelEditInfo" class="cancel-btn">Cancel</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="members-section">
          <h3>Members ({{ members.length }})</h3>
          <div class="members-list">
            <div
              v-for="member in members"
              :key="member.userId"
              class="member-item"
            >
              <div class="member-avatar">
                <img
                  v-if="member.avatarUrl"
                  :src="member.avatarUrl"
                  :alt="member.displayName"
                />
                <div v-else class="avatar-placeholder">
                  {{ member.displayName?.charAt(0).toUpperCase() || 'U' }}
                </div>
              </div>

              <div class="member-info">
                <div class="member-name">{{ member.displayName || member.username }}</div>
                <div class="member-username">@{{ member.username }}</div>
                <div class="member-role-badge" :class="member.role.toLowerCase()">
                  {{ member.role }}
                </div>
              </div>

              <div v-if="member.userId !== currentUserId" class="member-actions">
                <select
                  v-if="canChangeRole && member.role !== 'OWNER'"
                  @change="handleRoleChange(member, $event)"
                  :value="member.role"
                  class="role-select"
                >
                  <option value="MEMBER">Member</option>
                  <option value="ADMIN">Admin</option>
                </select>

                <button
                  v-if="canTransferOwnership && member.role !== 'OWNER'"
                  @click="handleTransferOwnership(member)"
                  class="transfer-btn"
                >
                  Make Owner
                </button>

                <button
                  v-if="canKickMember(member)"
                  @click="handleKick(member)"
                  class="kick-btn"
                >
                  Remove
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="actions-section">
          <button
            @click="handleLeaveGroup"
            :disabled="!canLeaveGroup"
            class="leave-group-btn"
            :title="!canLeaveGroup ? 'Owner must transfer ownership before leaving' : ''"
          >
            Leave Group
          </button>
          <div v-if="!canLeaveGroup" class="leave-warning">
            You must transfer ownership to another member before leaving the group.
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import * as groupsApi from '../api/groups'

const props = defineProps({
  isOpen: Boolean,
  conversation: Object
})

const emit = defineEmits(['close', 'groupUpdated'])

const authStore = useAuthStore()

const editingInfo = ref(false)
const editGroupName = ref('')
const editGroupAvatar = ref('')

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

function canKickMember(member) {
  if (member.role === 'OWNER') return false
  if (currentUserRole.value === 'OWNER') return true
  if (currentUserRole.value === 'ADMIN' && member.role !== 'ADMIN') return true
  return false
}

watch(() => props.isOpen, (newValue) => {
  if (newValue && props.conversation) {
    editGroupName.value = props.conversation.name || ''
    editGroupAvatar.value = props.conversation.avatarUrl || ''
  }
})

function handleClose() {
  emit('close')
}

function startEditInfo() {
  editingInfo.value = true
  editGroupName.value = props.conversation.name || ''
  editGroupAvatar.value = props.conversation.avatarUrl || ''
}

function cancelEditInfo() {
  editingInfo.value = false
}

async function saveGroupInfo() {
  if (!editGroupName.value.trim()) {
    alert('Group name is required')
    return
  }

  try {
    await groupsApi.updateGroupInfo(
      props.conversation.id,
      editGroupName.value.trim(),
      editGroupAvatar.value.trim() || null
    )
    editingInfo.value = false
    emit('groupUpdated')
  } catch (error) {
    console.error('Failed to update group info:', error)
    alert('Failed to update group information. Please try again.')
  }
}

async function handleRoleChange(member, event) {
  const newRole = event.target.value

  if (newRole === member.role) return

  const confirmChange = confirm(`Change ${member.displayName || member.username}'s role to ${newRole}?`)
  if (!confirmChange) {
    event.target.value = member.role
    return
  }

  try {
    await groupsApi.changeRole(props.conversation.id, member.userId, newRole)
    emit('groupUpdated')
  } catch (error) {
    console.error('Failed to change role:', error)
    alert('Failed to change member role. Please try again.')
    event.target.value = member.role
  }
}

async function handleTransferOwnership(member) {
  const confirmTransfer = confirm(
    `Transfer group ownership to ${member.displayName || member.username}? You will become a regular member.`
  )
  if (!confirmTransfer) return

  try {
    await groupsApi.transferOwnership(props.conversation.id, member.userId)
    emit('groupUpdated')
  } catch (error) {
    console.error('Failed to transfer ownership:', error)
    alert('Failed to transfer ownership. Please try again.')
  }
}

async function handleKick(member) {
  const confirmKick = confirm(`Remove ${member.displayName || member.username} from the group?`)
  if (!confirmKick) return

  try {
    await groupsApi.kickMember(props.conversation.id, member.userId)
    emit('groupUpdated')
  } catch (error) {
    console.error('Failed to kick member:', error)
    alert('Failed to remove member. Please try again.')
  }
}

async function handleLeaveGroup() {
  const confirmLeave = confirm('Are you sure you want to leave this group?')
  if (!confirmLeave) return

  try {
    await groupsApi.leaveGroup(props.conversation.id)
    emit('groupUpdated')
    emit('close')
  } catch (error) {
    console.error('Failed to leave group:', error)
    alert('Failed to leave group. Please try again.')
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

.group-info-section {
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-border);
}

.group-info-section h3 {
  margin: 0 0 16px 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.info-item {
  margin-bottom: 8px;
}

.info-item label {
  font-weight: 500;
  font-size: 13px;
  color: var(--color-text-secondary);
  display: block;
  margin-bottom: 6px;
}

.info-value {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 500;
}

.edit-info-btn {
  padding: 6px 16px;
  background-color: var(--color-primary);
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.edit-info-btn:hover {
  background-color: var(--color-primary-dark);
  transform: translateY(-1px);
}

.edit-info-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.edit-info-form input {
  padding: 10px 14px;
  border: 1.5px solid var(--color-border);
  border-radius: 10px;
  font-size: 14px;
  transition: all 0.2s ease;
}

.edit-info-form input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 4px rgba(224, 120, 86, 0.1);
}

.edit-info-actions {
  display: flex;
  gap: 8px;
}

.members-section h3 {
  margin: 0 0 16px 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.members-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.member-item {
  display: flex;
  align-items: center;
  padding: 14px;
  border: 1.5px solid var(--color-border);
  border-radius: 14px;
  gap: 12px;
  transition: all 0.2s ease;
}

.member-item:hover {
  background: var(--color-bg-hover);
}

.member-avatar {
  flex-shrink: 0;
}

.member-avatar img {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  object-fit: cover;
}

.avatar-placeholder {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--color-gradient-warm);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
}

.member-info {
  flex: 1;
  min-width: 0;
}

.member-name {
  font-weight: 600;
  font-size: 15px;
  color: var(--color-text-primary);
  margin-bottom: 3px;
}

.member-username {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}

.member-role-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.member-role-badge.owner {
  background-color: #F59E0B;
  color: white;
}

.member-role-badge.admin {
  background-color: var(--color-accent);
  color: white;
}

.member-role-badge.member {
  background-color: var(--color-border);
  color: var(--color-text-secondary);
}

.member-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}

.role-select {
  padding: 6px 12px;
  border: 1.5px solid var(--color-border);
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.role-select:focus {
  outline: none;
  border-color: var(--color-primary);
}

.transfer-btn {
  padding: 6px 14px;
  background-color: #F59E0B;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  transition: all 0.2s ease;
}

.transfer-btn:hover {
  background-color: #D97706;
  transform: translateY(-1px);
}

.kick-btn {
  padding: 6px 14px;
  background-color: var(--color-error);
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.kick-btn:hover {
  background-color: #C13A3A;
  transform: translateY(-1px);
}

.actions-section {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--color-border);
}

.leave-group-btn {
  width: 100%;
  padding: 12px 24px;
  background-color: var(--color-error);
  color: white;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.leave-group-btn:hover:not(:disabled) {
  background-color: #C13A3A;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(214, 69, 69, 0.3);
}

.leave-group-btn:disabled {
  background-color: var(--color-border);
  color: var(--color-text-tertiary);
  cursor: not-allowed;
  transform: none;
}

.leave-warning {
  margin-top: 12px;
  padding: 12px 16px;
  background-color: #FEF3C7;
  border: 1px solid #FCD34D;
  border-radius: 10px;
  color: #92400E;
  font-size: 13px;
  text-align: center;
}

.save-btn {
  padding: 10px 20px;
  background-color: var(--color-primary);
  color: white;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.save-btn:hover {
  background-color: var(--color-primary-dark);
  transform: translateY(-1px);
}

.cancel-btn {
  padding: 10px 20px;
  background-color: var(--color-border);
  color: var(--color-text-primary);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.cancel-btn:hover {
  background-color: #D1CCC4;
}

/* CSS Variables */
:root {
  --color-primary: #E07856;
  --color-primary-dark: #C96644;
  --color-primary-light: #FFF3EF;

  --color-accent: #7C9885;

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
