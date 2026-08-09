<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SimulatedDataBanner from '../components/SimulatedDataBanner.vue'
import {
  getActiveDiagnosis,
  getDiagnosis,
  getTask,
  startDiagnosis,
  type DiagnosticTaskView,
  type TaskDetail,
} from '../services/api'

const route = useRoute()
const router = useRouter()
const detail = ref<TaskDetail | null>(null)
const diagnostic = ref<DiagnosticTaskView | null>(null)
const loading = ref(false)
const diagnosticLoading = ref(false)
let pollTimer: number | undefined

const report = computed(() => diagnostic.value?.report ?? null)
const retryForbidden = computed(() => report.value?.retryEligibility.status === 'FORBIDDEN')

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

async function loadActiveDiagnosis() {
  if (!detail.value) return
  try {
    diagnostic.value = await getActiveDiagnosis(detail.value.task.id)
    schedulePoll()
  } catch (error: any) {
    if (error?.response?.status === 401) {
      await router.push('/login')
    } else {
      ElMessage.error('无法加载诊断状态')
    }
  }
}

function clearPoll() {
  if (pollTimer !== undefined) {
    window.clearTimeout(pollTimer)
    pollTimer = undefined
  }
}

function schedulePoll() {
  clearPoll()
  if (!diagnostic.value || diagnostic.value.terminal) return
  pollTimer = window.setTimeout(async () => {
    await pollDiagnosis()
  }, 2000)
}

async function pollDiagnosis() {
  if (!diagnostic.value) return
  try {
    diagnostic.value = await getDiagnosis(diagnostic.value.diagnosticTaskId)
    schedulePoll()
  } catch (error: any) {
    if (error?.response?.status === 401) {
      await router.push('/login')
    } else {
      ElMessage.error('无法轮询诊断状态')
      schedulePoll()
    }
  }
}

async function beginDiagnosis() {
  if (!detail.value || diagnosticLoading.value) return
  diagnosticLoading.value = true
  try {
    const active = await getActiveDiagnosis(detail.value.task.id)
    if (active) {
      diagnostic.value = active
    } else {
      const created = await startDiagnosis(detail.value.task.id)
      diagnostic.value = await getDiagnosis(created.diagnosticTaskId)
    }
    schedulePoll()
  } catch (error: any) {
    if (error?.response?.status === 401) {
      await router.push('/login')
    } else if (error?.response?.status === 409) {
      await loadActiveDiagnosis()
      ElMessage.info('该升级任务已有活跃诊断')
    } else {
      ElMessage.error('无法发起诊断')
    }
  } finally {
    diagnosticLoading.value = false
  }
}

onMounted(async () => {
  await loadDetail()
  await loadActiveDiagnosis()
})

onUnmounted(clearPoll)
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
        <div class="heading-actions">
          <el-tag type="danger">{{ detail.task.failureCode }}</el-tag>
          <el-button data-testid="start-diagnosis" type="primary" :loading="diagnosticLoading" @click="beginDiagnosis">
            {{ diagnostic ? '查看诊断' : '发起诊断' }}
          </el-button>
        </div>
      </div>

      <el-card v-if="diagnostic" class="diagnosis-card" shadow="never">
        <template #header>
          <div class="card-heading">
            <span>异常诊断</span>
            <el-tag :type="diagnostic.terminal ? 'success' : 'warning'">{{ diagnostic.state }}</el-tag>
          </div>
        </template>
        <div v-if="!diagnostic.report" class="diagnosis-progress">
          <span class="loading-dot" aria-hidden="true">…</span>
          <span>正在读取任务、设备和固件证据，页面每 2 秒更新一次。</span>
        </div>
        <template v-else>
          <el-alert :title="diagnostic.report.conclusion" type="error" :closable="false" />
          <div class="diagnosis-summary">
            <el-tag type="danger">{{ diagnostic.report.rootCauseCode }}</el-tag>
            <el-tag v-if="retryForbidden" type="warning">重试：{{ diagnostic.report.retryEligibility.status }}</el-tag>
          </div>
          <el-descriptions :column="1" border class="diagnosis-report-block">
            <el-descriptions-item label="重试原因">
              {{ diagnostic.report.retryEligibility.reason }}
            </el-descriptions-item>
            <el-descriptions-item label="下一步">
              {{ diagnostic.report.nextAction }}
            </el-descriptions-item>
          </el-descriptions>

          <div v-if="diagnostic.report.facts.length" class="diagnosis-report-block">
            <h3>事实</h3>
            <ul><li v-for="item in diagnostic.report.facts" :key="item.code">{{ item.text }}</li></ul>
          </div>
          <div v-if="diagnostic.report.inferences.length" class="diagnosis-report-block">
            <h3>推断</h3>
            <ul><li v-for="item in diagnostic.report.inferences" :key="item.code">{{ item.text }}</li></ul>
          </div>
          <div v-if="diagnostic.report.exclusions.length" class="diagnosis-report-block">
            <h3>排除项</h3>
            <ul><li v-for="item in diagnostic.report.exclusions" :key="item.code">{{ item.text }}</li></ul>
          </div>
          <div v-if="diagnostic.report.unknowns.length || diagnostic.report.evidenceGaps.length" class="diagnosis-report-block">
            <h3>未知项</h3>
            <ul>
              <li v-for="item in [...diagnostic.report.unknowns, ...diagnostic.report.evidenceGaps]" :key="item.code">{{ item.text }}</li>
            </ul>
          </div>
          <div class="diagnosis-report-block">
            <h3>证据</h3>
            <ul>
              <li v-for="evidence in diagnostic.report.evidenceRefs" :key="evidence.evidenceId">
                <code>{{ evidence.evidenceId }}</code> · {{ evidence.source }} · {{ evidence.summary }}
              </li>
            </ul>
          </div>
        </template>
      </el-card>

      <el-row :gutter="16">
        <el-col :span="12"><el-card><template #header>升级任务</template><dl class="facts"><dt>状态</dt><dd>{{ detail.task.status }}</dd><dt>目标版本</dt><dd>{{ detail.firmwareVersion.version }}</dd><dt>失败时间</dt><dd>{{ new Date(detail.task.failedAt).toLocaleString('zh-CN') }}</dd></dl></el-card></el-col>
        <el-col :span="12"><el-card><template #header>设备</template><dl class="facts"><dt>设备标识</dt><dd>{{ detail.device.id }}</dd><dt>序列号</dt><dd>{{ detail.device.serialNumber }}</dd><dt>型号</dt><dd>{{ detail.device.model }}</dd><dt>当前版本</dt><dd>{{ detail.device.currentVersion }}</dd><dt>在线</dt><dd>{{ detail.device.online ? '是' : '否' }}</dd></dl></el-card></el-col>
      </el-row>
      <el-card class="detail-card"><template #header>失败日志</template><el-timeline><el-timeline-item v-for="log in detail.failureLogs" :key="log.id" :timestamp="new Date(log.observedAt).toLocaleString('zh-CN')"><strong>{{ log.code }}</strong> · {{ log.message }}<p class="muted">来源：{{ log.source }}</p></el-timeline-item></el-timeline></el-card>
      <el-card class="detail-card"><template #header>消息状态</template><el-descriptions v-if="detail.messageStates[0]" :column="2" border><el-descriptions-item label="消息类型">{{ detail.messageStates[0].messageType }}</el-descriptions-item><el-descriptions-item label="发送状态">{{ detail.messageStates[0].sendStatus }}</el-descriptions-item><el-descriptions-item label="回调状态">{{ detail.messageStates[0].callbackStatus }}</el-descriptions-item><el-descriptions-item label="说明">{{ detail.messageStates[0].detail }}</el-descriptions-item></el-descriptions></el-card>
    </template>
  </section>
</template>
