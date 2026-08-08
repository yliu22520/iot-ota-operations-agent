<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import SimulatedDataBanner from '../components/SimulatedDataBanner.vue'
import TaskTable from '../components/TaskTable.vue'
import { listTasks, type TaskSummary } from '../services/api'

const router = useRouter()
const tasks = ref<TaskSummary[]>([])
const loading = ref(false)

async function loadTasks() {
  loading.value = true
  try {
    const response = await listTasks()
    tasks.value = response.items
  } catch (error: any) {
    if (error?.response?.status === 401) {
      await router.push('/login')
    } else {
      ElMessage.error('无法加载模拟升级任务')
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadTasks)
</script>

<template>
  <section class="page-content">
    <SimulatedDataBanner />
    <div class="page-heading">
      <div>
        <p class="eyebrow">SIMULATOR / FINAL FAILURE TASKS</p>
        <h2>最终失败升级任务</h2>
        <p class="muted">选择一个任务查看设备、目标固件、日志和消息状态。</p>
      </div>
      <el-button :loading="loading" @click="loadTasks">刷新</el-button>
    </div>
    <el-card shadow="never"><TaskTable :tasks="tasks" v-loading="loading" /></el-card>
  </section>
</template>
