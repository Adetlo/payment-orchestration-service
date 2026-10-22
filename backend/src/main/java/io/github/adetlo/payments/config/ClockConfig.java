package io.github.adetlo.payments.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared UTC clock and property binding. */
@Configuration
@EnableConfigurationProperties(PaymentsProperties.class)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
