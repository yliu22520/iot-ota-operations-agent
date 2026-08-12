<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SimulatedDataBanner from '../components/SimulatedDataBanner.vue'
import {
  approveRetryPlan,
  getActiveDiagnosis,
  getAuditEvents,
  getDiagnosis,
  getTask,
  getTelemetry,
  startDiagnosis,
  type AuditEventView,
  type DiagnosticTaskView,
  type DiagnosticTelemetry,
  type RetryExecutionView,
  type TaskDetail,
} from '../services/api'

const route = useRoute()
const router = useRouter()
const detail = ref<TaskDetail | null>(null)
const diagnostic = ref<DiagnosticTaskView | null>(null)
const auditEvents = ref<AuditEventView[]>([])
const telemetry = ref<DiagnosticTelemetry | null>(null)
const loading = ref(false)
const diagnosticLoading = ref(false)
const telemetryLoading = ref(false)
const approvalVisible = ref(false)
const approvalAcknowledged = ref(false)
const approving = ref(false)
const execution = ref<RetryExecutionView | null>(null)
let pollTimer: number | undefined

const report = computed(() => diagnostic.value?.report ?? null)
const retryPlan = computed(() => report.value?.retryPlan ?? null)
const canReviewRetryPlan = computed(() => diagnostic.value?.state === 'WAITING_APPROVAL' && !!retryPlan.value)
const retryForbidden = computed(() => report.value?.retryEligibility.status === 'FORBIDDEN')
const expiresInMinutes = computed(() => retryPlan.value
  ? Math.max(0, Math.ceil((new Date(retryPlan.value.expiresAt).getTime() - Date.now()) / 60000))
  : 0)
const executionStep = computed(() => {
  if (!execution.value) return 0
  if (['VERIFIED', 'INCOMPLETE'].includes(execution.value.executionStatus)
    || ['BUSINESS_ACCEPTED', 'INCOMPLETE'].includes(execution.value.verificationStatus)) return 3
  if (execution.value.executionStatus === 'CALL_ACCEPTED') return 2
  return 1
})
const executionOutcomeType = computed(() => execution.value?.verificationStatus === 'BUSINESS_ACCEPTED'
  ? 'success' : execution.value?.verificationStatus === 'INCOMPLETE' ? 'danger' : 'warning')

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await getTask(String(route.params.id))
  } catch (error: any) {
    if (error?.response?.status === 401) await router.push('/login')
    else ElMessage.error('无法加载升级任务详情')
  } finally {
    loading.value = false
  }
}

async function loadAuditTimeline() {
  if (!diagnostic.value) return
  auditEvents.value = await getAuditEvents(diagnostic.value.diagnosticTaskId)
}

async function loadActiveDiagnosis() {
  if (!detail.value) return
  try {
    diagnostic.value = await getActiveDiagnosis(detail.value.task.id)
    await loadAuditTimeline()
    schedulePoll()
  } catch (error: any) {
    if (error?.response?.status === 401) await router.push('/login')
    else ElMessage.error('无法加载诊断状态')
  }
}

async function loadTelemetry() {
  if (!diagnostic.value || telemetryLoading.value) return
  telemetryLoading.value = true
  try {
    telemetry.value = await getTelemetry(diagnostic.value.diagnosticTaskId)
  } catch (error: any) {
    if (error?.response?.status === 401) await router.push('/login')
    else ElMessage.error('无法加载开发遥测')
  } finally {
    telemetryLoading.value = false
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
  pollTimer = window.setTimeout(pollDiagnosis, 2000)
}

async function pollDiagnosis() {
  if (!diagnostic.value) return
  try {
    diagnostic.value = await getDiagnosis(diagnostic.value.diagnosticTaskId)
    await loadAuditTimeline()
    schedulePoll()
  } catch (error: any) {
    if (error?.response?.status === 401) await router.push('/login')
    else {
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
    if (active) diagnostic.value = active
    else {
      const created = await startDiagnosis(detail.value.task.id)
      diagnostic.value = await getDiagnosis(created.diagnosticTaskId)
    }
    await loadAuditTimeline()
    schedulePoll()
  } catch (error: any) {
    if (error?.response?.status === 401) await router.push('/login')
    else if (error?.response?.status === 409) {
      await loadActiveDiagnosis()
      ElMessage.info('已有诊断任务处于活跃状态')
    } else ElMessage.error('无法启动诊断')
  } finally {
    diagnosticLoading.value = false
  }
}

function reviewRetryPlan() {
  approvalAcknowledged.value = false
  approvalVisible.value = true
}

async function confirmRetryPlan() {
  if (!diagnostic.value || !retryPlan.value || !approvalAcknowledged.value || approving.value) return
  approving.value = true
  try {
    execution.value = await approveRetryPlan(diagnostic.value.diagnosticTaskId, retryPlan.value.planVersion, true)
    diagnostic.value = {
      ...diagnostic.value,
      state: execution.value.diagnosticState,
      status: execution.value.diagnosticState,
      terminal: ['COMPLETED', 'INCOMPLETE'].includes(execution.value.diagnosticState),
    }
    approvalVisible.value = false
    clearPoll()
    await loadAuditTimeline()
  } catch (error: any) {
    const code = error?.response?.data?.code
    if (['RETRY_PLAN_EXPIRED', 'RETRY_PLAN_SNAPSHOT_CHANGED', 'RETRY_PLAN_NOT_PENDING'].includes(code)) {
      approvalVisible.value = false
      await loadActiveDiagnosis()
      ElMessage.error('审批已失效，请重新刷新诊断')
    } else if (error?.response?.status === 401) await router.push('/login')
    else ElMessage.error('无法审批重试计划')
  } finally {
    approving.value = false
  }
}

function safeAuditMetadata(metadata: Record<string, unknown>) {
  const allowedKeys = new Set([
    'state', 'toolName', 'evidenceId', 'source', 'observedAt', 'durationMs', 'errorCode', 'modelId',
    'inputTokens', 'outputTokens', 'tokenUsageReported', 'maxOutputTokens', 'maxContextTokens',
    'planVersion', 'expiresAt', 'upgradeTaskId', 'executionId', 'idempotencyKey', 'reasonCode',
  ])
  return JSON.stringify(Object.fromEntries(Object.entries(metadata).filter(([key]) => allowedKeys.has(key))))
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
    <el-button text @click="router.push('/tasks')">← 返回失败任务列表</el-button>
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
            {{ diagnostic ? '查看诊断' : '开始诊断' }}
          </el-button>
        </div>
      </div>

      <el-card v-if="diagnostic" class="diagnosis-card" shadow="never">
        <template #header>
          <div class="card-heading">
            <span>异常诊断</span>
            <div class="heading-actions">
              <el-button data-testid="show-telemetry" text :loading="telemetryLoading" @click="loadTelemetry">查看开发遥测</el-button>
              <el-tag :type="diagnostic.state === 'COMPLETED' ? 'success' : diagnostic.terminal ? 'danger' : 'warning'">
                {{ diagnostic.state }}
              </el-tag>
            </div>
          </div>
        </template>

        <div v-if="!report" class="diagnosis-progress">正在读取任务、设备和固件证据；页面会继续轮询。</div>
        <template v-else>
          <el-alert :title="report.conclusion" type="error" :closable="false" />
          <el-alert v-if="diagnostic.state === 'INCOMPLETE'" title="证据不足，诊断未完成；当前结论不得用于执行写操作。"
            type="warning" :closable="false" class="diagnosis-report-block" />
          <div class="diagnosis-summary">
            <el-tag type="danger">{{ report.rootCauseCode }}</el-tag>
            <el-tag type="info">置信度：{{ report.confidence || '未提供' }}</el-tag>
            <el-tag v-if="retryForbidden" type="warning">动作资格：{{ report.retryEligibility.status }}</el-tag>
            <el-tag v-else-if="report.retryEligibility.eligible" type="success">动作资格：{{ report.retryEligibility.status }}</el-tag>
          </div>
          <el-descriptions :column="1" border class="diagnosis-report-block">
            <el-descriptions-item label="动作资格说明">{{ report.retryEligibility.reason }}</el-descriptions-item>
            <el-descriptions-item label="建议动作">{{ report.nextAction }}</el-descriptions-item>
          </el-descriptions>

          <el-card v-if="retryPlan" class="diagnosis-report-block retry-plan-card" shadow="never">
            <template #header><div class="card-heading"><span>重试计划 v{{ retryPlan.planVersion }}</span><el-tag type="warning">{{ retryPlan.status }}</el-tag></div></template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="目标版本">{{ retryPlan.targetVersion }}</el-descriptions-item>
              <el-descriptions-item label="影响范围">{{ retryPlan.impactScope }}</el-descriptions-item>
              <el-descriptions-item label="重试次数">{{ retryPlan.retryCount }} / {{ retryPlan.maxRetries }}</el-descriptions-item>
              <el-descriptions-item label="过期时间">{{ new Date(retryPlan.expiresAt).toLocaleString('zh-CN') }}</el-descriptions-item>
              <el-descriptions-item label="剩余时间">约 {{ expiresInMinutes }} 分钟</el-descriptions-item>
            </el-descriptions>
            <h3>执行前提</h3>
            <el-tag v-for="item in retryPlan.preconditions" :key="item" class="condition-tag">{{ item }}</el-tag>
            <el-button v-if="canReviewRetryPlan" data-testid="review-retry-plan" type="primary" @click="reviewRetryPlan">审核并批准重试计划</el-button>
          </el-card>

          <el-steps v-if="execution" :active="executionStep" finish-status="success" process-status="process" class="diagnosis-report-block">
            <el-step title="审批已记录" /><el-step title="执行中" /><el-step title="验证中" /><el-step title="最终结果" />
          </el-steps>
          <el-alert v-if="execution" :title="`执行状态：${execution.executionStatus} · 验证结果：${execution.verificationStatus}`"
            :type="executionOutcomeType" :closable="false" class="diagnosis-report-block" />

          <div v-for="section in [
            { title: '事实', items: report.facts },
            { title: '确定性规则结论', items: report.ruleConclusions },
            { title: '推断依据', items: report.inferences },
            { title: '知识建议', items: report.knowledgeSuggestions },
            { title: '排除项', items: report.exclusions },
            { title: '未知项与证据缺口', items: [...report.unknowns, ...report.evidenceGaps] },
          ]" :key="section.title" v-show="section.items.length" class="diagnosis-report-block">
            <h3>{{ section.title }}</h3><ul><li v-for="item in section.items" :key="item.code">{{ item.text }}</li></ul>
          </div>
          <div class="diagnosis-report-block">
            <h3>证据引用</h3>
            <ul><li v-for="evidence in report.evidenceRefs" :key="evidence.evidenceId">
              <span class="muted">{{ new Date(evidence.observedAt).toLocaleString('zh-CN') }} · {{ evidence.kind || 'OTHER' }} · {{ evidence.locator || evidence.evidenceId }}</span>
              <code>{{ evidence.evidenceId }}</code> · {{ evidence.source }} · {{ evidence.summary }}
            </li></ul>
          </div>
          <div v-if="report.decisionTraceRefs.length" class="diagnosis-report-block">
            <h3>决策轨迹引用</h3><ul><li v-for="traceRef in report.decisionTraceRefs" :key="traceRef"><code>{{ traceRef }}</code></li></ul>
          </div>
        </template>
      </el-card>

      <el-card v-if="telemetry" data-testid="diagnostic-telemetry" class="detail-card" shadow="never">
        <template #header>开发遥测（结构化）</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="模型配置">{{ telemetry.modelConfiguration.provider }} / {{ telemetry.modelConfiguration.modelId }}</el-descriptions-item>
          <el-descriptions-item label="推理档位">{{ telemetry.modelConfiguration.reasoningTier }}</el-descriptions-item>
          <el-descriptions-item label="提示版本">{{ telemetry.modelConfiguration.promptVersion }}</el-descriptions-item>
          <el-descriptions-item label="工具契约">{{ telemetry.modelConfiguration.toolSchemaVersion }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ telemetry.elapsedMs }} ms</el-descriptions-item>
          <el-descriptions-item label="预算">工具 {{ telemetry.budget.maxToolCalls }} 次 / 模型 {{ telemetry.budget.maxModelInteractions }} 轮 / {{ telemetry.budget.maxDurationMs }} ms</el-descriptions-item>
        </el-descriptions>
        <h3>工具事件</h3><ul><li v-for="(event, index) in telemetry.toolEvents" :key="`${event.toolName}-${event.evidenceId}-${index}`">{{ event.toolName }} · {{ event.result }} · {{ event.durationMs }} ms · {{ event.evidenceId }}<span v-if="event.errorCode"> · {{ event.errorCode }}</span></li></ul>
        <h3>Token usage</h3><p v-if="telemetry.tokenUsage.reported">input={{ telemetry.tokenUsage.inputTokens }} / output={{ telemetry.tokenUsage.outputTokens }}</p><p v-else class="muted">模型未上报 token 用量（结构化占位）</p>
        <div v-if="telemetry.errors.length"><h3>结构化错误</h3><ul><li v-for="error in telemetry.errors" :key="`${error.code}-${error.occurredAt}`">{{ error.code }} · {{ error.message }}</li></ul></div>
      </el-card>

      <el-card v-if="auditEvents.length" class="detail-card" shadow="never">
        <template #header>追加式审计时间线</template>
        <el-timeline><el-timeline-item v-for="event in auditEvents" :key="event.id" :timestamp="new Date(event.occurredAt).toLocaleString('zh-CN')"><strong>{{ event.action }}</strong> · {{ event.result }}<p class="muted">{{ event.actor }} · {{ event.summary }}</p><p class="muted">{{ safeAuditMetadata(event.metadata) }}</p></el-timeline-item></el-timeline>
      </el-card>
      <el-row :gutter="16"><el-col :span="12"><el-card><template #header>升级任务</template><dl class="facts"><dt>状态</dt><dd>{{ detail.task.status }}</dd><dt>目标版本</dt><dd>{{ detail.firmwareVersion.version }}</dd><dt>失败时间</dt><dd>{{ new Date(detail.task.failedAt).toLocaleString('zh-CN') }}</dd></dl></el-card></el-col><el-col :span="12"><el-card><template #header>设备</template><dl class="facts"><dt>设备标识</dt><dd>{{ detail.device.id }}</dd><dt>序列号</dt><dd>{{ detail.device.serialNumber }}</dd><dt>型号</dt><dd>{{ detail.device.model }}</dd><dt>当前版本</dt><dd>{{ detail.device.currentVersion }}</dd><dt>在线</dt><dd>{{ detail.device.online ? '是' : '否' }}</dd></dl></el-card></el-col></el-row>
      <el-card class="detail-card"><template #header>失败日志</template><el-timeline><el-timeline-item v-for="log in detail.failureLogs" :key="log.id" :timestamp="new Date(log.observedAt).toLocaleString('zh-CN')"><strong>{{ log.code }}</strong> · {{ log.message }}<p class="muted">来源：{{ log.source }}</p></el-timeline-item></el-timeline></el-card>
      <el-card class="detail-card"><template #header>消息状态</template><el-descriptions v-if="detail.messageStates[0]" :column="2" border><el-descriptions-item label="消息类型">{{ detail.messageStates[0].messageType }}</el-descriptions-item><el-descriptions-item label="发送状态">{{ detail.messageStates[0].sendStatus }}</el-descriptions-item><el-descriptions-item label="回调状态">{{ detail.messageStates[0].callbackStatus }}</el-descriptions-item><el-descriptions-item label="说明">{{ detail.messageStates[0].detail }}</el-descriptions-item></el-descriptions></el-card>
    </template>

    <el-dialog v-if="retryPlan" v-model="approvalVisible" title="批准并执行重试计划" width="680px">
      <el-alert title="批准后将执行并验证该计划；请确认第二次执行按钮。" type="warning" :closable="false" />
      <el-descriptions :column="1" border class="diagnosis-report-block"><el-descriptions-item label="任务">{{ retryPlan.upgradeTaskId }}</el-descriptions-item><el-descriptions-item label="目标版本">{{ retryPlan.targetVersion }}</el-descriptions-item><el-descriptions-item label="失败原因">{{ retryPlan.failureCode }}</el-descriptions-item><el-descriptions-item label="计划版本">{{ retryPlan.planVersion }}</el-descriptions-item></el-descriptions>
      <p><strong>证据：</strong>{{ retryPlan.evidenceRefs.join(' · ') }}</p>
      <el-checkbox v-model="approvalAcknowledged" data-testid="approval-acknowledgement">我已核对证据、目标版本、影响范围和执行前提，并明确授权本次重试。</el-checkbox>
      <template #footer><el-button @click="approvalVisible = false">取消</el-button><el-button data-testid="approve-retry-plan" type="primary" :disabled="!approvalAcknowledged" :loading="approving" @click="confirmRetryPlan">批准并执行</el-button></template>
    </el-dialog>
  </section>
</template>
