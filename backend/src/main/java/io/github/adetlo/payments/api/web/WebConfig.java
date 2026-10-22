package io.github.adetlo.payments.api.web;

import io.github.adetlo.payments.config.PaymentsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the demo UI origins and the request guard interceptor. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RequestGuardInterceptor requestGuard;
    private final PaymentsProperties properties;

    public WebConfig(RequestGuardInterceptor requestGuard, PaymentsProperties properties) {
        this.requestGuard = requestGuard;
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = properties.cors().allowedOrigins().toArray(String[]::new);
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders(
                        "Content-Type", "Idempotency-Key", "X-Demo-Failure", "X-Correlation-Id")
                .exposedHeaders(
                        "Location",
                        "X-Request-Id",
                        "X-Correlation-Id",
                        "Idempotent-Replayed",
                        "Retry-After")
                .maxAge(3600);
        registry.addMapping("/actuator/**").allowedOrigins(origins).allowedMethods("GET");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestGuard).addPathPatterns("/api/**");
    }
}
