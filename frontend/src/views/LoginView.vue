<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const submitting = ref(false)
const form = reactive({ username: 'demo-operator', password: 'demo-password' })

async function submit() {
  submitting.value = true
  try {
    await auth.signIn(form.username, form.password)
    const redirect = typeof route.query.redirect === 'string'
      && route.query.redirect.startsWith('/')
      && !route.query.redirect.startsWith('//')
      ? route.query.redirect
      : '/tasks'
    await router.push(redirect)
  } catch {
    ElMessage.error('登录失败，请检查演示账号和密码')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header><span>演示运维账号登录</span></template>
      <el-form @submit.prevent="submit">
        <el-form-item label="账号"><el-input v-model="form.username" autocomplete="username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password autocomplete="current-password" /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="submitting" class="full-width">登录</el-button>
      </el-form>
      <p class="muted">V1 仅提供一个本地演示运维身份，后端使用 Session Cookie 保护接口。</p>
    </el-card>
  </div>
</template>
