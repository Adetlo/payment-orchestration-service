package io.github.adetlo.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the payment orchestration service. */
@SpringBootApplication
public class PaymentOrchestrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentOrchestrationApplication.class, args);
    }
}
