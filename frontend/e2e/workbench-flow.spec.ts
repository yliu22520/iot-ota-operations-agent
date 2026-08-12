import { expect, test } from '@playwright/test'

const task = (id: string, failureCode: string, failureSummary: string) => ({
  task: {
    id, deviceId: 'device-sim-001', deviceSerialNumber: 'SIM-EDGE-001', targetVersion: '2.0.0',
    failedAt: '2026-08-08T00:01:00Z', status: 'FINAL_FAILURE', failureCode, failureSummary,
    diagnosticStatus: null, simulated: true,
  },
  device: { id: 'device-sim-001', serialNumber: 'SIM-EDGE-001', model: 'EDGE-CAMERA-A', currentVersion: '1.0.0', online: true, storageAvailableMb: 2048, simulated: true },
  firmwareVersion: { id: 'firmware-sim-200', version: '2.0.0', releaseStatus: 'RELEASED', compatibleModels: 'SENSOR-HUB-B', checksum: 'sha256:sim-200', releasedAt: '2026-08-08T00:00:00Z', simulated: true },
  failureLogs: [], messageStates: [], simulated: true,
})

const report = (rootCauseCode: string, conclusion: string, retryPlan: unknown = null) => ({
  schemaVersion: 1, diagnosticTaskId: 'diagnostic-e2e', rootCauseCode, conclusion, confidence: 'HIGH',
  facts: [{ code: 'FACT', text: 'Structured fact evidence', evidenceRefs: ['upgrade-task:task-e2e'] }],
  ruleConclusions: [], inferences: [], knowledgeSuggestions: [], exclusions: [], unknowns: [], evidenceGaps: [],
  evidenceRefs: [{ evidenceId: 'version-compatibility:task-e2e', source: 'backend.rule', observedAt: '2026-08-08T00:01:02Z', summary: 'Structured rule evidence', kind: 'RULE', locator: 'task-e2e' }],
  rootCauseEvidenceRefs: ['version-compatibility:task-e2e'],
  retryEligibility: { eligible: Boolean(retryPlan), status: retryPlan ? 'ELIGIBLE' : 'FORBIDDEN', reasonCode: retryPlan ? 'CALLBACK_TIMEOUT_RETRY_ALLOWED' : 'TARGET_MODEL_NOT_SUPPORTED', reason: retryPlan ? 'Review and approve.' : 'do not retry this task' },
  retryPlan, nextAction: retryPlan ? 'Review and approve.' : 'Create a new task after correcting the mapping.', decisionTraceRefs: ['trace:diagnostic-e2e:report'],
  provenance: { modelId: 'controlled-diagnostic-explainer-v1', promptVersion: 'diagnosis-controlled-v1', schemaVersion: 'diagnostic-report-v1', embeddingModelId: 'test', embeddingModelRevision: 'test', embeddingModelSha256: 'test', generatedAt: '2026-08-08T00:01:02Z' },
})

async function authenticate(page: import('@playwright/test').Page) {
  await page.route('**/api/v1/auth/me', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ username: 'demo-operator', role: 'OPERATOR', authenticated: true }) }))
}

test('version-incompatible diagnosis renders a forbidden action and evidence', async ({ page }) => {
  await authenticate(page)
  await page.route('**/api/v1/upgrade-tasks/task-101', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(task('task-101', 'VERSION_INCOMPATIBLE', 'target model is unsupported')) }))
  await page.route('**/api/v1/diagnostic-tasks/active**', route => route.fulfill({ status: 404 }))
  await page.route('**/api/v1/diagnostic-tasks', async route => {
    if (route.request().method() === 'POST') return route.fulfill({ status: 202, contentType: 'application/json', body: JSON.stringify({ diagnosticTaskId: 'diagnostic-e2e', upgradeTaskId: 'task-101', state: 'CREATED', statusUrl: '/api/v1/diagnostic-tasks/diagnostic-e2e' }) })
    return route.continue()
  })
  await page.route('**/api/v1/diagnostic-tasks/diagnostic-e2e', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ diagnosticTaskId: 'diagnostic-e2e', upgradeTaskId: 'task-101', state: 'COMPLETED', status: 'COMPLETED', terminal: true, createdAt: '2026-08-08T00:01:01Z', updatedAt: '2026-08-08T00:01:02Z', report: report('VERSION_INCOMPATIBLE', 'Target firmware is incompatible; retry is forbidden.') }) }))
  await page.route('**/api/v1/diagnostic-tasks/diagnostic-e2e/audit-events', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))

  await page.goto('/tasks/task-101')
  await page.getByTestId('start-diagnosis').click()
  await expect(page.getByText('VERSION_INCOMPATIBLE')).toBeVisible()
  await expect(page.getByText(/FORBIDDEN/)).toBeVisible()
  await expect(page.getByText('do not retry this task')).toBeVisible()
  await expect(page.getByText('RULE · task-e2e')).toBeVisible()
})

test('callback-timeout approval renders execution and verification outcome', async ({ page }) => {
  await authenticate(page)
  let approved = false
  const plan = { planId: 'plan-e2e', planVersion: 1, diagnosticTaskId: 'diagnostic-e2e', upgradeTaskId: 'task-102', taskVersion: 3, taskStatus: 'FINAL_FAILURE', targetVersion: '1.1.0', failureCode: 'CALLBACK_TIMEOUT', retryCount: 0, maxRetries: 3, deviceOnline: true, versionCompatible: true, messageSendStatus: 'SENT', callbackStatus: 'TIMEOUT', impactScope: 'SINGLE_UPGRADE_TASK', preconditions: ['TASK_FINAL_FAILURE'], evidenceRefs: ['message-state:task-102'], status: 'PENDING_APPROVAL', createdAt: '2026-08-08T00:02:01Z', expiresAt: '2026-08-08T00:17:01Z' }
  await page.route('**/api/v1/upgrade-tasks/task-102', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(task('task-102', 'CALLBACK_TIMEOUT', 'callback timed out')) }))
  await page.route('**/api/v1/diagnostic-tasks/active**', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ diagnosticTaskId: 'diagnostic-e2e', upgradeTaskId: 'task-102', state: 'WAITING_APPROVAL', status: 'WAITING_APPROVAL', terminal: false, createdAt: '2026-08-08T00:02:00Z', updatedAt: '2026-08-08T00:02:01Z', report: report('CALLBACK_TIMEOUT', 'The callback timed out.', plan) }) }))
  await page.route('**/api/v1/diagnostic-tasks/diagnostic-e2e/audit-events', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(approved ? [{ id: 'audit-e2e', occurredAt: '2026-08-08T00:02:05Z', actor: 'demo-operator', objectType: 'RETRY_EXECUTION', objectId: 'execution-e2e', action: 'RETRY_VERIFIED', result: 'BUSINESS_ACCEPTED', summary: 'retry accepted', metadata: {} }] : []),
  }))
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ token: 'test' }) }))
  await page.route('**/api/v1/diagnostic-tasks/diagnostic-e2e/retry-plan/approve', route => {
    approved = true
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ diagnosticTaskId: 'diagnostic-e2e', planId: 'plan-e2e', planVersion: 1, executionId: 'execution-e2e', diagnosticState: 'COMPLETED', executionStatus: 'VERIFIED', verificationStatus: 'BUSINESS_ACCEPTED', idempotencyKey: 'retry-plan:plan-e2e:v1', idempotentReplay: false, updatedAt: '2026-08-08T00:02:05Z' }) })
  })

  await page.goto('/tasks/task-102')
  await page.getByTestId('review-retry-plan').click()
  await expect(page.getByRole('dialog').getByText('1.1.0')).toBeVisible()
  await page.getByTestId('approval-acknowledgement').click()
  await page.getByTestId('approve-retry-plan').click()
  await expect(page.getByText(/BUSINESS_ACCEPTED/)).toBeVisible()
  await expect(page.getByText(/RETRY_VERIFIED/)).toBeVisible()
  await expect(page.getByText('最终结果')).toBeVisible()
})
