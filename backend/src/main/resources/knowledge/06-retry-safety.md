---
documentId: ota-retry-safety
version: 1.0
type: POLICY_GUIDE
status: ACTIVE
applicableComponents: retry,diagnosis
updatedAt: 2026-08-10T00:00:00Z
---
# 单任务重试安全策略

重试当前升级任务是 V1 唯一允许的处置，但必须由后端规则确认资格并生成绑定事实快照的计划。计划只有在人工审批、有效期、任务版本和关键前提都保持一致时才能执行，模型与知识检索均不能调用执行入口。

版本不兼容、证据不足、任务状态变化或重试次数超限都应禁止重试。重复审批和网络重放必须返回同一幂等执行结果，不能产生第二次副作用。
