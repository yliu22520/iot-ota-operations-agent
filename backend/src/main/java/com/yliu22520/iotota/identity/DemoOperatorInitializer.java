package com.yliu22520.iotota.identity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class DemoOperatorInitializer implements ApplicationRunner {

    private final OperatorUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final DemoOperatorProperties properties;

    public DemoOperatorInitializer(OperatorUserRepository repository,
                                   PasswordEncoder passwordEncoder,
                                   DemoOperatorProperties properties) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.existsById(properties.username())) {
            return;
        }
        repository.save(new OperatorUser(
                properties.username(),
                passwordEncoder.encode(properties.password()),
                "OPERATOR",
                true,
                Instant.now()));
    }
}
