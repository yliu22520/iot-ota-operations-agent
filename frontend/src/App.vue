<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()

onMounted(() => auth.loadCurrent())

async function logout() {
  await auth.logout()
  await router.push('/login')
}
</script>

<template>
  <el-container class="app-shell">
    <el-header class="app-header">
      <div>
        <p class="eyebrow">IoT / OTA OPERATIONS</p>
        <h1>升级任务运维工作台</h1>
      </div>
      <div v-if="auth.authenticated" class="operator-menu">
        <span>{{ auth.username }}</span>
        <el-button text @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-main>
      <nav v-if="auth.authenticated" class="workbench-nav">
        <RouterLink to="/tasks">失败升级任务</RouterLink>
      </nav>
      <RouterView />
    </el-main>
  </el-container>
</template>
