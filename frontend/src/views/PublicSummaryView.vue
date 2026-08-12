<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import SimulatedDataBanner from '../components/SimulatedDataBanner.vue'
import { listPublicSummaries, type PublicSummary } from '../services/api'

const summaries = ref<PublicSummary[]>([])
const loading = ref(false)

async function loadSummaries() {
  loading.value = true
  try {
    const response = await listPublicSummaries()
    summaries.value = response.items
  } catch {
    ElMessage.error('暂时无法加载诊断摘要')
  } finally {
    loading.value = false
  }
}

onMounted(loadSummaries)
</script>

<template>
  <section v-loading="loading" class="page-content public-summary-page">
    <SimulatedDataBanner />
    <div class="page-heading">
      <div>
        <p class="eyebrow">PUBLIC DIAGNOSTIC SUMMARIES</p>
        <h2>公开诊断摘要</h2>
        <p class="muted">以下内容来自预生成的安全摘要，仅用于了解诊断结论，不提供实时诊断或写操作。</p>
      </div>
    </div>

    <div v-if="summaries.length" class="summary-grid">
      <el-card v-for="summary in summaries" :key="summary.caseId" class="summary-card" shadow="never">
        <template #header>
          <div class="card-heading">
            <span>{{ summary.title }}</span>
            <el-tag type="success">{{ summary.confidence }}</el-tag>
          </div>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="场景">{{ summary.scenario }}</el-descriptions-item>
          <el-descriptions-item label="结论">{{ summary.conclusion }}</el-descriptions-item>
          <el-descriptions-item label="处理结果">{{ summary.actionOutcome }}</el-descriptions-item>
        </el-descriptions>
        <div class="diagnosis-report-block">
          <h3>证据摘要</h3>
          <ul>
            <li v-for="evidence in summary.evidence" :key="evidence">{{ evidence }}</li>
          </ul>
        </div>
      </el-card>
    </div>
    <el-empty v-else description="暂无公开诊断摘要" />
  </section>
</template>
