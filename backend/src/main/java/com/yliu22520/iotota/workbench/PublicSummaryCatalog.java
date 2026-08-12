package com.yliu22520.iotota.workbench;

import org.springframework.stereotype.Component;

import java.util.List;

/** Curated, read-only summaries that never invoke the live diagnostic workflow. */
@Component
public class PublicSummaryCatalog {

    private final List<WorkbenchDtos.PublicSummary> summaries = List.of(
            new WorkbenchDtos.PublicSummary(
                    "version-incompatible",
                    "版本不兼容：禁止重试",
                    "目标固件与设备型号不匹配",
                    "后端兼容性规则确认目标固件不支持当前设备型号，因此系统明确禁止重试。",
                    "HIGH",
                    List.of("任务失败事实", "设备型号", "固件兼容型号", "确定性兼容性规则"),
                    "只读诊断；不创建审批计划，不执行设备写操作。",
                    true),
            new WorkbenchDtos.PublicSummary(
                    "callback-timeout",
                    "回调超时：审批后受控重试",
                    "升级命令已发送但设备回调超时",
                    "任务、设备、版本和消息事实满足重试规则，系统生成绑定事实快照的审批计划。",
                    "HIGH",
                    List.of("任务失败事实", "设备在线状态", "版本兼容性", "消息回调状态"),
                    "需要人工审批；审批成功后由后端执行并验证一次重试。",
                    true));

    public List<WorkbenchDtos.PublicSummary> list() {
        return summaries;
    }
}
