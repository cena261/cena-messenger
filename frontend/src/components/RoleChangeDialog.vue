<template>
  <div class="role-dialog-overlay">
    <div class="role-dialog">
      <div class="role-header">
        <h3>Thay đổi vai trò</h3>
      </div>

      <div class="role-body">
        <p class="member-name">{{ member?.displayName || member?.username }}</p>
        <p class="current-role">Vai trò hiện tại: <strong>{{ getRoleLabel(currentRole) }}</strong></p>

        <div class="role-options">
          <label class="role-option" :class="{ selected: selectedRole === 'MEMBER' }">
            <input type="radio" value="MEMBER" v-model="selectedRole" />
            <div class="option-content">
              <User :size="20" />
              <div>
                <div class="option-title">Thành viên</div>
                <div class="option-desc">Chỉ có thể xem và gửi tin nhắn</div>
              </div>
            </div>
          </label>

          <label class="role-option" :class="{ selected: selectedRole === 'ADMIN' }">
            <input type="radio" value="ADMIN" v-model="selectedRole" />
            <div class="option-content">
              <Shield :size="20" />
              <div>
                <div class="option-title">Quản trị viên</div>
                <div class="option-desc">Có thể quản lý thành viên và chỉnh sửa thông tin nhóm</div>
              </div>
            </div>
          </label>
        </div>
      </div>

      <div class="role-footer">
        <button @click="$emit('cancel')" class="cancel-btn">
          Hủy
        </button>
        <button
          @click="$emit('confirm', selectedRole)"
          class="confirm-btn"
          :disabled="selectedRole === currentRole"
        >
          Xác nhận
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { User, Shield } from 'lucide-vue-next'

const props = defineProps({
  member: Object,
  currentRole: String
})

const emit = defineEmits(['confirm', 'cancel'])

const selectedRole = ref(props.currentRole)

watch(() => props.currentRole, (newRole) => {
  selectedRole.value = newRole
})

function getRoleLabel(role) {
  const labels = {
    OWNER: 'Chủ nhóm',
    ADMIN: 'Quản trị viên',
    MEMBER: 'Thành viên'
  }
  return labels[role] || role
}

function handleEscKey(event) {
  if (event.key === 'Escape') {
    emit('cancel')
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
.role-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.role-dialog {
  background: var(--color-surface);
  border-radius: 16px;
  width: 90%;
  max-width: 440px;
  box-shadow: var(--shadow-lg);
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    transform: translateY(20px) scale(0.95);
    opacity: 0;
  }
  to {
    transform: translateY(0) scale(1);
    opacity: 1;
  }
}

.role-header {
  padding: 20px 20px 16px;
  border-bottom: 1px solid var(--color-border);
}

.role-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.role-body {
  padding: 20px;
}

.member-name {
  margin: 0 0 4px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.current-role {
  margin: 0 0 20px 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.current-role strong {
  color: var(--color-text-primary);
}

.role-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.role-option {
  display: block;
  padding: 14px;
  border: 2px solid var(--color-border);
  border-radius: 12px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.role-option:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
}

.role-option.selected {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.role-option input[type="radio"] {
  display: none;
}

.option-content {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  color: var(--color-text-primary);
}

.option-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 2px;
}

.option-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.4;
}

.role-footer {
  padding: 16px 20px 20px;
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.cancel-btn,
.confirm-btn {
  padding: 10px 20px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
  min-width: 80px;
}

.cancel-btn {
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text-primary);
}

.cancel-btn:hover {
  background: var(--color-surface-hover);
}

.confirm-btn {
  background: var(--color-primary);
  border: none;
  color: white;
}

.confirm-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}

.confirm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}
</style>
