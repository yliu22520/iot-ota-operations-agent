import { render, screen, waitFor } from '@testing-library/vue'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PublicSummaryView from './PublicSummaryView.vue'
import { listPublicSummaries } from '../services/api'

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api')
  return { ...actual, listPublicSummaries: vi.fn() }
})

describe('PublicSummaryView', () => {
  beforeEach(() => {
    vi.mocked(listPublicSummaries).mockResolvedValue({
      items: [{
        caseId: 'VERSION_INCOMPATIBLE',
        title: '版本不兼容：禁止重试',
        scenario: '目标固件与设备型号不匹配',
        conclusion: '后端规则确认禁止重试。',
        confidence: 'HIGH',
        evidence: ['任务失败事实', '设备型号'],
        actionOutcome: '只读诊断；不执行写操作。',
        simulated: true,
      }],
    })
  })

  it('renders safe pre-generated summaries without live action controls', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/public', component: PublicSummaryView }] })
    await router.push('/public')

    render(PublicSummaryView, { global: { plugins: [router, ElementPlus] } })

    await waitFor(() => expect(screen.getByText('版本不兼容：禁止重试')).toBeTruthy())
    expect(screen.getByText('后端规则确认禁止重试。')).toBeTruthy()
    expect(screen.getByText('HIGH')).toBeTruthy()
    expect(screen.getByText('只读诊断；不执行写操作。')).toBeTruthy()
    expect(screen.queryByRole('button', { name: /诊断|审批|重试|执行/ })).toBeNull()
  })
})
