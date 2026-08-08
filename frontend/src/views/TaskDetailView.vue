<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SimulatedDataBanner from '../components/SimulatedDataBanner.vue'
import { getTask, type TaskDetail } from '../services/api'

const route = useRoute()
const router = useRouter()
const detail = ref<TaskDetail | null>(null)
const loading = ref(false)

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await getTask(String(route.params.id))
  } catch (error: any) {
    if (error?.response?.status === 401) {
      await router.push('/login')
    } else {
      ElMessage.error('无法加载升级任务详情')
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>

<template>
  <section v-loading="loading" class="page-content">
    <SimulatedDataBanner />
    <el-button text @click="router.push('/tasks')">← 返回任务列表</el-button>
    <template v-if="detail">
      <div class="page-heading">
        <div>
          <p class="eyebrow">UPGRADE TASK DETAIL</p>
          <h2>{{ detail.task.id }}</h2>
          <p class="muted">{{ detail.task.failureSummary }}</p>
        </div>
        <el-tag type="danger">{{ detail.task.failureCode }}</el-tag>
      </div>
      <el-row :gutter="16">
        <el-col :span="12"><el-card><template #header>升级任务</template><dl class="facts"><dt>状态</dt><dd>{{ detail.task.status }}</dd><dt>目标版本</dt><dd>{{ detail.firmwareVersion.version }}</dd><dt>失败时间</dt><dd>{{ new Date(detail.task.failedAt).toLocaleString('zh-CN') }}</dd></dl></el-card></el-col>
        <el-col :span="12"><el-card><template #header>设备</template><dl class="facts"><dt>设备标识</dt><dd>{{ detail.device.id }}</dd><dt>序列号</dt><dd>{{ detail.device.serialNumber }}</dd><dt>型号</dt><dd>{{ detail.device.model }}</dd><dt>当前版本</dt><dd>{{ detail.device.currentVersion }}</dd><dt>在线</dt><dd>{{ detail.device.online ? '是' : '否' }}</dd></dl></el-card></el-col>
      </el-row>
      <el-card class="detail-card"><template #header>故障日志</template><el-timeline><el-timeline-item v-for="log in detail.failureLogs" :key="log.id" :timestamp="new Date(log.observedAt).toLocaleString('zh-CN')"><strong>{{ log.code }}</strong> · {{ log.message }}<p class="muted">来源：{{ log.source }}</p></el-timeline-item></el-timeline></el-card>
      <el-card class="detail-card"><template #header>消息状态</template><el-descriptions :column="2" border><el-descriptions-item label="消息类型">{{ detail.messageStates[0]?.messageType }}</el-descriptions-item><el-descriptions-item label="发送状态">{{ detail.messageStates[0]?.sendStatus }}</el-descriptions-item><el-descriptions-item label="回调状态">{{ detail.messageStates[0]?.callbackStatus }}</el-descriptions-item><el-descriptions-item label="说明">{{ detail.messageStates[0]?.detail }}</el-descriptions-item></el-descriptions></el-card>
    </template>
  </section>
</template>
