import { render, screen, waitFor } from '@testing-library/vue'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import { vi } from 'vitest'
import TaskListView from './TaskListView.vue'
import { listTasks } from '../services/api'

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api')
  return { ...actual, listTasks: vi.fn() }
})

describe('TaskListView', () => {
  it('展示失败任务的关键事实字段', async () => {
    vi.mocked(listTasks).mockResolvedValue({
      simulated: true,
      items: [{
        id: 'upgrade-task-sim-callback-timeout',
        deviceId: 'device-sim-002',
        deviceSerialNumber: 'SIM-SENSOR-002',
        targetVersion: '1.1.0',
        failedAt: '2026-08-08T00:02:00Z',
        status: 'FINAL_FAILURE',
        failureCode: 'CALLBACK_TIMEOUT',
        failureSummary: '回调超时',
        diagnosticStatus: null,
        simulated: true,
      }],
    })
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/tasks', component: TaskListView }] })
    await router.push('/tasks')

    render(TaskListView, { global: { plugins: [createTestingPinia(), router, ElementPlus], stubs: { 'router-link': true } } })

    await waitFor(() => expect(screen.getByText('upgrade-task-sim-callback-timeout')).toBeTruthy())
    expect(screen.getByText('SIM-SENSOR-002')).toBeTruthy()
    expect(screen.getByText('CALLBACK_TIMEOUT')).toBeTruthy()
    expect(screen.getByText('暂无诊断')).toBeTruthy()
    expect(screen.getByText(/模拟器数据/)).toBeTruthy()
  })
})
