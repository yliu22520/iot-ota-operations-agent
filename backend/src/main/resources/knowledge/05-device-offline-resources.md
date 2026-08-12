---
documentId: ota-device-offline-resources
version: 1.0
type: RUNBOOK
status: ACTIVE
applicableComponents: device,diagnosis
updatedAt: 2026-08-10T00:00:00Z
---
# 设备离线与资源不足

设备离线、存储空间不足或电量策略不满足时，升级可能在下发前或下载阶段失败。诊断需要引用设备在线状态、可用存储和观测时间；陈旧的在线记录不能替代当前设备事实。

资源不足时建议先恢复设备条件，再创建新的升级尝试。文档不能把设备标记为在线，也不能绕过后端对设备状态和版本规则的校验。
