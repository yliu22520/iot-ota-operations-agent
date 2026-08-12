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
        <h1>物联网运维工作台</h1>
      </div>
      <div v-if="auth.authenticated" class="operator-menu">
        <span>{{ auth.username }}</span>
        <el-button text @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-main>
      <nav class="workbench-nav">
        <RouterLink v-if="auth.authenticated" to="/tasks">失败任务列表</RouterLink>
        <RouterLink to="/public">公开摘要</RouterLink>
      </nav>
      <RouterView />
    </el-main>
  </el-container>
</template>
