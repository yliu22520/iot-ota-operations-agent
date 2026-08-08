package com.yliu22520.iotota.simulator;

public class UpgradeTaskNotFoundException extends RuntimeException {
    public UpgradeTaskNotFoundException(Object id) {
        super("升级任务不存在: " + id);
    }
}
