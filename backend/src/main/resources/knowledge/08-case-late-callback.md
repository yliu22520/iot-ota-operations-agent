---
documentId: case-late-device-callback
version: 1.0
type: INCIDENT_CASE
status: ACTIVE
applicableComponents: message,callback
updatedAt: 2026-08-10T00:00:00Z
---
# 历史案例：设备回调迟到

一次升级命令已成功发送并被设备消费，安装日志没有校验错误，但结果回调超过窗口后才到达。诊断把消息状态和时间线作为主要证据，标记 CALLBACK_TIMEOUT；资格规则确认版本兼容且设备在线后，才提出一次受控重试建议。

若当前任务缺少消息状态或设备已离线，应标记证据缺口并停止确定性判断。历史案例不能替代当前事实，也不能直接触发重试。
