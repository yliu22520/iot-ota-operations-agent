---
documentId: ota-task-state
version: 1.0
type: RUNBOOK
status: ACTIVE
applicableComponents: upgrade-task,diagnosis
updatedAt: 2026-08-10T00:00:00Z
---
# OTA 任务状态与失败事实

升级任务只有在业务状态机进入终止失败状态后，才能作为失败任务诊断。下载中、安装中、等待回调等中间状态不能仅凭超时日志改写成最终失败。排查时应记录任务标识、目标版本、失败码、失败时间和当前状态，并把这些字段作为业务事实引用。

诊断建议不能反向修改任务状态。若日志描述与任务状态冲突，应保留冲突并以任务状态机为权威来源；知识文档只能解释常见含义，不能授予重试资格。
