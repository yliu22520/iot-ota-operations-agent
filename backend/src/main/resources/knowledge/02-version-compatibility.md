---
documentId: ota-version-compatibility
version: 1.0
type: RUNBOOK
status: ACTIVE
applicableComponents: firmware,device,diagnosis
updatedAt: 2026-08-10T00:00:00Z
---
# 固件版本兼容性排查

出现 VERSION_INCOMPATIBLE 时，先核对设备型号、设备当前版本、目标固件发布状态以及固件声明的兼容型号集合。兼容性必须由后端版本矩阵或结构化规则计算，不能由日志中的自然语言、历史案例或相似文档推断。

若目标固件不支持当前设备型号，应明确排除网络抖动和简单重试方案，并禁止重试当前任务。正确动作是修正设备与固件的映射，重新创建面向正确目标版本的升级任务。
