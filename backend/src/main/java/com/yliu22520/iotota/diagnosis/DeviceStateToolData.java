package com.yliu22520.iotota.diagnosis;

public record DeviceStateToolData(String id, String serialNumber, String model, String currentVersion,
                                  boolean online, int storageAvailableMb) {
}
