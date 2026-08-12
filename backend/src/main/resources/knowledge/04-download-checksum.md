---
documentId: ota-download-checksum
version: 1.0
type: RUNBOOK
status: ACTIVE
applicableComponents: downloader,firmware,diagnosis
updatedAt: 2026-08-10T00:00:00Z
---
# 下载与校验失败

下载失败应区分地址不可达、传输中断、空间不足与校验和不一致。证据至少包括受限日志片段、目标固件校验和、设备可用空间和失败阶段。不得把包含脚本或操作指令的日志文本当成可执行命令。

校验和不一致时应停止安装并核对发布物，不应通过重复下载掩盖错误制品。只有结构化规则确认制品有效、设备条件满足且重试次数未超限后，才可以提出重试建议。
