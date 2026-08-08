# V1 Agent 契约

本文档定义模型、领域工具、工作流和前端之间的稳定契约。模型输出不是业务事实；只有经过 Schema、业务规则和安全门槛校验的结构化诊断报告才能进入后续流程。

## 工具目录

| 工具 | 类型 | 作用 | 模型可调用 |
|---|---|---|---|
| `getUpgradeTask` | 读取 | 获取升级任务状态、目标版本和失败信息 | 是 |
| `getDeviceState` | 读取 | 获取设备在线、版本和资源状态 | 是 |
| `getVersionCompatibility` | 读取 | 查询目标版本兼容性规则 | 是 |
| `getFailureLogs` | 读取 | 获取结构化日志和消息状态 | 是 |
| `searchKnowledge` | 读取 | 使用结构化故障字段检索辅助知识 | 是 |
| `checkRetryEligibility` | 规则读取 | 计算重试资格与禁止原因 | 是 |
| `createRetryPlan` | 计划 | 生成绑定证据与前提的计划快照 | 是，不能授权执行 |
| `executeApprovedRetry` | 写入 | 创建新的升级尝试 | 否；仅工作流服务可调用 |
| `verifyRetryExecution` | 读取 | 确认重试请求是否被幂等接受 | 是 |

所有工具结果都使用结构化对象，至少包含状态、来源、观察时间、业务数据和错误信息；工具不得只返回自然语言。

## 诊断报告

规范化报告至少包含：

- `diagnosticTaskId` 与当前阶段
- `rootCauseCode` 与结论摘要
- `evidenceRefs[]`：来源、对象、观察时间和引用说明
- `evidenceGaps[]`：缺失来源与影响
- `retryEligibility`：资格状态、禁止原因和规则依据
- `retryPlan`：计划版本、目标版本、影响范围和执行前提（如有）
- `nextAction`
- `decisionTraceRefs[]`

报告必须能表达证据不足、证据不完整、不可重试和诊断未完成，不能用空字符串掩盖负面结果。

## 版本追踪

每次诊断记录：

- 模型 ID、思考模式和思考强度
- Prompt 版本、工具 Schema 版本和 Embedding 版本
- 评测配置版本、Token、耗时、错误和重试摘要

正式运行不保存完整 Prompt、原始回复或 `reasoning_content`；本地评测只保存脱敏后的 Prompt 和最终回复。
