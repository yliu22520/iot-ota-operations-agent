---
documentId: ota-callback-timeout
version: 1.0
type: RUNBOOK
status: ACTIVE
applicableComponents: message,callback,diagnosis
updatedAt: 2026-08-10T00:00:00Z
---
# 回调超时诊断

回调超时需要同时检查命令发送、设备消费、安装阶段和结果回调状态。命令已发送且设备已消费，但在约定窗口内没有成功回调，才支持 CALLBACK_TIMEOUT 结论；缺少消息状态时只能标记证据缺口。

即使历史经验认为重试通常有效，也必须重新检查任务状态、设备在线状态、重试次数和版本兼容性。知识建议不等于执行授权，任何重试仍需通过确定性资格规则与人工审批。
