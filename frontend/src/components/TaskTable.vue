<script setup lang="ts">
import type { TaskSummary } from '../services/api'

defineProps<{ tasks: TaskSummary[] }>()
</script>

<template>
  <el-table :data="tasks" stripe empty-text="暂无最终失败的模拟升级任务">
    <el-table-column prop="id" label="任务标识" min-width="220" />
    <el-table-column prop="deviceSerialNumber" label="设备" min-width="150" />
    <el-table-column prop="targetVersion" label="目标版本" width="120" />
    <el-table-column prop="failureCode" label="故障编码" width="180" />
    <el-table-column label="失败时间" width="190">
      <template #default="scope">{{ new Date(scope.row.failedAt).toLocaleString('zh-CN') }}</template>
    </el-table-column>
    <el-table-column label="当前状态" width="130">
      <template #default="scope"><el-tag type="danger">{{ scope.row.status }}</el-tag></template>
    </el-table-column>
    <el-table-column label="诊断状态" width="130">
      <template #default="scope"><el-tag type="info">{{ scope.row.diagnosticStatus ?? '暂无诊断' }}</el-tag></template>
    </el-table-column>
    <el-table-column label="操作" width="100" fixed="right">
      <template #default="scope">
        <el-link type="primary" :href="`/tasks/${encodeURIComponent(scope.row.id)}`">查看详情</el-link>
      </template>
    </el-table-column>
  </el-table>
</template>
