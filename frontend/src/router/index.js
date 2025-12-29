import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import ConversationsView from '../views/ConversationsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/login'
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/conversations',
      name: 'conversations',
      component: ConversationsView,
      meta: { requiresAuth: true }
    }
  ]
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    try {
      await authStore.restoreSession()
      next()
    } catch (error) {
      next('/login')
    }
  } else if (to.path === '/login' && authStore.isAuthenticated) {
    next('/conversations')
  } else {
    next()
  }
})

export default router
