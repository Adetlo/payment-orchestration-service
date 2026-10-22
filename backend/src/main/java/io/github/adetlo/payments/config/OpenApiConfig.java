package io.github.adetlo.payments.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata for the generated specification. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentsOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Payment Orchestration Lab API")
                                .version("v1")
                                .description(
                                        "Synthetic payment orchestration service: idempotent"
                                            + " intake, event-driven state machine, transactional"
                                            + " outbox, retries, dead-lettering and"
                                            + " reconciliation.")
                                .license(new License().name("MIT")));
    }
}
