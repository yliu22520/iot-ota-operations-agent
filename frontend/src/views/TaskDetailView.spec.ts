import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createTestingPinia } from '@pinia/testing'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TaskDetailView from './TaskDetailView.vue'
import { getActiveDiagnosis, getDiagnosis, getTask, startDiagnosis } from '../services/api'

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api')
  return {
    ...actual,
    getTask: vi.fn(),
    getActiveDiagnosis: vi.fn(),
    getDiagnosis: vi.fn(),
    startDiagnosis: vi.fn(),
  }
})

describe('TaskDetailView diagnosis seam', () => {
  beforeEach(() => {
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
})
