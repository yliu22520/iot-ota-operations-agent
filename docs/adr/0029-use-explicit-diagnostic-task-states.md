# 使用明确的诊断任务状态

诊断任务使用 `CREATED`、`INVESTIGATING`、`REPORT_READY`、可选的 `WAITING_APPROVAL`、`EXECUTING`、`VERIFYING`，以及结果状态 `COMPLETED` 和 `INCOMPLETE`。不使用通用 `FAILED`，避免与升级任务的最终失败混淆；`INCOMPLETE` 表示诊断尚未形成满足要求的结果，并允许人工恢复。

## Consequences

- 每次状态转换都必须写入审计记录。
- 恢复逻辑从持久化状态和最近完成阶段继续。
- 没有重试计划的诊断可以从 `REPORT_READY` 直接进入 `COMPLETED`。
