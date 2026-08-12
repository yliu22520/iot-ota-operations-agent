import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createTestingPinia } from '@pinia/testing'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TaskDetailView from './TaskDetailView.vue'
import {
  approveRetryPlan,
  getActiveDiagnosis,
  getAuditEvents,
  getDiagnosis,
  getTask,
  startDiagnosis,
} from '../services/api'

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api')
  return {
    ...actual,
    getTask: vi.fn(),
    getActiveDiagnosis: vi.fn(),
    getDiagnosis: vi.fn(),
    startDiagnosis: vi.fn(),
    approveRetryPlan: vi.fn(),
    getAuditEvents: vi.fn(),
  }
})

describe('TaskDetailView diagnosis seam', () => {
  beforeEach(() => {
    vi.mocked(getAuditEvents).mockResolvedValue([])
    vi.mocked(getTask).mockResolvedValue({
      task: {
        id: 'task-101', deviceId: 'device-sim-001', deviceSerialNumber: 'SIM-EDGE-001',
        targetVersion: '2.0.0', failedAt: '2026-08-08T00:01:00Z', status: 'FINAL_FAILURE',
        failureCode: 'VERSION_INCOMPATIBLE', failureSummary: 'target model is unsupported',
        diagnosticStatus: null, simulated: true,
      },
      device: { id: 'device-sim-001', serialNumber: 'SIM-EDGE-001', model: 'EDGE-CAMERA-A', currentVersion: '1.0.0', online: true, storageAvailableMb: 2048, simulated: true },
      firmwareVersion: { id: 'firmware-sim-200', version: '2.0.0', releaseStatus: 'RELEASED', compatibleModels: 'SENSOR-HUB-B', checksum: 'sha256:sim-200', releasedAt: '2026-08-08T00:00:00Z', simulated: true },
      failureLogs: [], messageStates: [], simulated: true,
    })
    vi.mocked(getActiveDiagnosis).mockResolvedValue(null)
    vi.mocked(startDiagnosis).mockResolvedValue({
      diagnosticTaskId: 'diagnostic-101', upgradeTaskId: 'task-101', state: 'CREATED', statusUrl: '/api/v1/diagnostic-tasks/diagnostic-101',
    })
    vi.mocked(getDiagnosis).mockResolvedValue({
      diagnosticTaskId: 'diagnostic-101', upgradeTaskId: 'task-101', state: 'COMPLETED', status: 'COMPLETED', terminal: true,
      createdAt: '2026-08-08T00:01:01Z', updatedAt: '2026-08-08T00:01:02Z',
      report: {
        schemaVersion: 1, diagnosticTaskId: 'diagnostic-101', rootCauseCode: 'VERSION_INCOMPATIBLE',
        conclusion: 'Target firmware is incompatible with the device model; retry is forbidden.',
        facts: [], inferences: [], exclusions: [], unknowns: [], evidenceGaps: [],
        evidenceRefs: [{ evidenceId: 'version-compatibility:task-101', source: 'backend.version-compatibility-rule', observedAt: '2026-08-08T00:01:02Z', summary: 'Backend rule returned TARGET_MODEL_NOT_SUPPORTED' }],
        rootCauseEvidenceRefs: ['version-compatibility:task-101'],
        retryEligibility: { eligible: false, status: 'FORBIDDEN', reasonCode: 'TARGET_MODEL_NOT_SUPPORTED', reason: 'do not retry this task' },
        retryPlan: null,
        nextAction: 'Create a new task after correcting the mapping.', decisionTraceRefs: [],
        provenance: { modelId: 'controlled-diagnostic-explainer-v1', promptVersion: 'v1', schemaVersion: 'v1', generatedAt: '2026-08-08T00:01:02Z' },
      },
    })
  })

  it('starts a diagnosis and renders its terminal report with forbidden retry status', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/tasks/:id', component: TaskDetailView }],
    })
    await router.push('/tasks/task-101')

    render(TaskDetailView, {
      global: { plugins: [createTestingPinia(), router, ElementPlus] },
    })

    const startButton = await screen.findByTestId('start-diagnosis')
    await fireEvent.click(startButton)

    await waitFor(() => expect(startDiagnosis).toHaveBeenCalledWith('task-101'))
    expect(await screen.findByText('VERSION_INCOMPATIBLE')).toBeTruthy()
    expect(screen.getByText(/FORBIDDEN/)).toBeTruthy()
    expect(screen.getByText(/do not retry this task/)).toBeTruthy()
  })

  it('shows the bound plan and uses one approval action to execute, verify, and refresh the audit timeline', async () => {
    vi.mocked(getActiveDiagnosis).mockResolvedValue({
      diagnosticTaskId: 'diagnostic-102', upgradeTaskId: 'task-102', state: 'WAITING_APPROVAL',
      status: 'WAITING_APPROVAL', terminal: false, createdAt: '2026-08-08T00:02:00Z',
      updatedAt: '2026-08-08T00:02:01Z',
      report: {
        schemaVersion: 1, diagnosticTaskId: 'diagnostic-102', rootCauseCode: 'CALLBACK_TIMEOUT',
        conclusion: 'The callback timed out and one approved retry is eligible.',
        facts: [], inferences: [], exclusions: [], unknowns: [], evidenceGaps: [],
        evidenceRefs: [{ evidenceId: 'message-state:task-102', source: 'simulator.message-state',
          observedAt: '2026-08-08T00:02:01Z', summary: 'message sent; callback timeout' }],
        rootCauseEvidenceRefs: ['message-state:task-102'],
        retryEligibility: { eligible: true, status: 'ELIGIBLE', reasonCode: 'CALLBACK_TIMEOUT_RETRY_ALLOWED',
          reason: 'Backend rules permit one approved retry.' },
        retryPlan: {
          planId: 'plan-102', planVersion: 1, diagnosticTaskId: 'diagnostic-102', upgradeTaskId: 'task-102',
          taskVersion: 3, taskStatus: 'FINAL_FAILURE', targetVersion: '1.1.0', failureCode: 'CALLBACK_TIMEOUT',
          retryCount: 0, maxRetries: 3, deviceOnline: true, versionCompatible: true,
          messageSendStatus: 'SENT', callbackStatus: 'TIMEOUT', impactScope: 'SINGLE_UPGRADE_TASK',
          preconditions: ['TASK_FINAL_FAILURE', 'DEVICE_ONLINE', 'VERSION_COMPATIBLE', 'CALLBACK_TIMEOUT_CONFIRMED'],
          evidenceRefs: ['message-state:task-102'], status: 'PENDING_APPROVAL',
          createdAt: '2026-08-08T00:02:01Z', expiresAt: '2026-08-08T00:17:01Z',
        },
        nextAction: 'Review and approve.', decisionTraceRefs: [],
        provenance: { modelId: 'controlled-diagnostic-explainer-v1', promptVersion: 'v1',
          schemaVersion: 'v1', generatedAt: '2026-08-08T00:02:01Z' },
      },
    } as any)
    vi.mocked(approveRetryPlan).mockResolvedValue({
      diagnosticTaskId: 'diagnostic-102', planId: 'plan-102', planVersion: 1, executionId: 'execution-102',
      diagnosticState: 'COMPLETED', executionStatus: 'VERIFIED', verificationStatus: 'BUSINESS_ACCEPTED',
      idempotencyKey: 'retry-plan:plan-102:v1', idempotentReplay: false,
      updatedAt: '2026-08-08T00:02:05Z',
    })
    vi.mocked(getAuditEvents).mockResolvedValue([{ id: 'audit-1', occurredAt: '2026-08-08T00:02:05Z',
      actor: 'demo-operator', objectType: 'RETRY_EXECUTION', objectId: 'execution-102',
      action: 'RETRY_VERIFIED', result: 'BUSINESS_ACCEPTED', summary: 'retry accepted', metadata: {} }])

    const router = createRouter({ history: createMemoryHistory(),
      routes: [{ path: '/tasks/:id', component: TaskDetailView }] })
    await router.push('/tasks/task-102')
    render(TaskDetailView, { global: { plugins: [createTestingPinia(), router, ElementPlus] } })

    await fireEvent.click(await screen.findByTestId('review-retry-plan'))
    expect((await screen.findAllByText('1.1.0')).length).toBeGreaterThanOrEqual(1)
    await fireEvent.click(screen.getByTestId('approval-acknowledgement'))
    await fireEvent.click(screen.getByTestId('approve-retry-plan'))

    await waitFor(() => expect(approveRetryPlan).toHaveBeenCalledWith('diagnostic-102', 1, true))
    expect((await screen.findAllByText(/BUSINESS_ACCEPTED/)).length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText(/RETRY_VERIFIED/)).toBeTruthy()
    expect(screen.queryByText('执行重试')).toBeNull()
  })
})
