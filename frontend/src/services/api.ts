import axios from 'axios'

export interface AuthResponse {
  username: string
  role: string
  authenticated: boolean
}

export interface TaskSummary {
  id: string
  deviceId: string
  deviceSerialNumber: string
  targetVersion: string
  failedAt: string
  status: string
  failureCode: string
  failureSummary: string
  diagnosticStatus: string | null
  simulated: boolean
}

export interface DeviceView {
  id: string
  serialNumber: string
  model: string
  currentVersion: string
  online: boolean
  storageAvailableMb: number
  simulated: boolean
}

export interface FirmwareVersionView {
  id: string
  version: string
  releaseStatus: string
  compatibleModels: string
  checksum: string
  releasedAt: string
  simulated: boolean
}

export interface FailureLogView {
  id: number
  observedAt: string
  level: string
  code: string
  message: string
  source: string
  simulated: boolean
}

export interface MessageStateView {
  id: number
  messageType: string
  sendStatus: string
  callbackStatus: string
  observedAt: string
  detail: string
  simulated: boolean
}

export interface TaskDetail {
  task: TaskSummary
  device: DeviceView
  firmwareVersion: FirmwareVersionView
  failureLogs: FailureLogView[]
  messageStates: MessageStateView[]
  simulated: boolean
}

const client = axios.create({
  baseURL: '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

export async function getCsrf() {
  await client.get<{ token: string }>('/v1/auth/csrf')
}

export async function login(username: string, password: string) {
  await getCsrf()
  const response = await client.post<AuthResponse>('/v1/auth/login', { username, password })
  return response.data
}

export async function getCurrentUser() {
  const response = await client.get<AuthResponse>('/v1/auth/me')
  return response.data
}

export async function logout() {
  await getCsrf()
  await client.post('/v1/auth/logout')
}

export async function listTasks() {
  const response = await client.get<{ items: TaskSummary[]; simulated: boolean }>('/v1/upgrade-tasks')
  return response.data
}

export async function getTask(id: string) {
  const response = await client.get<TaskDetail>(`/v1/upgrade-tasks/${encodeURIComponent(id)}`)
  return response.data
}
