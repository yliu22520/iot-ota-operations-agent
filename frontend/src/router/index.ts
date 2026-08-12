import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import PublicSummaryView from '../views/PublicSummaryView.vue'
import TaskListView from '../views/TaskListView.vue'
import TaskDetailView from '../views/TaskDetailView.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/tasks' },
    { path: '/login', component: LoginView },
    { path: '/public', component: PublicSummaryView },
    { path: '/tasks', component: TaskListView, meta: { requiresAuth: true } },
    { path: '/tasks/:id', component: TaskDetailView, meta: { requiresAuth: true } },
  ],
})

router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true

  const auth = useAuthStore()
  if (!auth.loaded) await auth.loadCurrent()
  if (auth.authenticated) return true

  return { path: '/login', query: { redirect: to.fullPath } }
})

export default router
