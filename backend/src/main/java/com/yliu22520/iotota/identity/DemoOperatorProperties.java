package com.yliu22520.iotota.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.operator")
public record DemoOperatorProperties(String username, String password) {
}
