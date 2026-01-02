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
  background: white;
  border-radius: 8px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  padding: 1rem;
  border-bottom: 1px solid #ddd;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header h2 {
  margin: 0;
  font-size: 1.25rem;
  color: #333;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #666;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  color: #333;
}

.modal-body {
  padding: 1rem;
  overflow-y: auto;
  flex: 1;
}

.group-info-section {
  margin-bottom: 1.5rem;
}

.group-info-section h3 {
  margin: 0 0 1rem 0;
  font-size: 1rem;
  color: #333;
}

.info-item {
  margin-bottom: 0.5rem;
}

.info-item label {
  font-weight: 500;
  color: #666;
  display: block;
  margin-bottom: 0.25rem;
}

.info-value {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.edit-info-btn {
  padding: 0.25rem 0.5rem;
  background-color: #2196F3;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
}

.edit-info-btn:hover {
  background-color: #1976D2;
}

.edit-info-form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.edit-info-form input {
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.875rem;
}

.edit-info-actions {
  display: flex;
  gap: 0.5rem;
}

.members-section h3 {
  margin: 0 0 1rem 0;
  font-size: 1rem;
  color: #333;
}

.members-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.member-item {
  display: flex;
  align-items: center;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 8px;
  gap: 0.75rem;
}

.member-avatar {
  flex-shrink: 0;
}

.member-avatar img {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: #4CAF50;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
  font-weight: 500;
}

.member-info {
  flex: 1;
  min-width: 0;
}

.member-name {
  font-weight: 500;
  color: #333;
  margin-bottom: 0.125rem;
}

.member-username {
  font-size: 0.75rem;
  color: #666;
  margin-bottom: 0.25rem;
}

.member-role-badge {
  display: inline-block;
  padding: 0.125rem 0.5rem;
  border-radius: 12px;
  font-size: 0.625rem;
  font-weight: 600;
  text-transform: uppercase;
}

.member-role-badge.owner {
  background-color: #FF9800;
  color: white;
}

.member-role-badge.admin {
  background-color: #2196F3;
  color: white;
}

.member-role-badge.member {
  background-color: #9E9E9E;
  color: white;
}

.member-actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  flex-shrink: 0;
}

.role-select {
  padding: 0.25rem 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.875rem;
  cursor: pointer;
}

.transfer-btn {
  padding: 0.25rem 0.5rem;
  background-color: #FF9800;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.75rem;
  white-space: nowrap;
}

.transfer-btn:hover {
  background-color: #F57C00;
}

.kick-btn {
  padding: 0.25rem 0.5rem;
  background-color: #f44336;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.75rem;
}

.kick-btn:hover {
  background-color: #d32f2f;
}

.actions-section {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid #ddd;
}

.leave-group-btn {
  width: 100%;
  padding: 0.75rem;
  background-color: #f44336;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 1rem;
  font-weight: 500;
}

.leave-group-btn:hover:not(:disabled) {
  background-color: #d32f2f;
}

.leave-group-btn:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.leave-warning {
  margin-top: 0.5rem;
  padding: 0.5rem;
  background-color: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 4px;
  color: #856404;
  font-size: 0.875rem;
  text-align: center;
}

.save-btn {
  padding: 0.5rem 1rem;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
}

.save-btn:hover {
  background-color: #45a049;
}

.cancel-btn {
  padding: 0.5rem 1rem;
  background-color: #9E9E9E;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
}

.cancel-btn:hover {
  background-color: #757575;
}
</style>
