package io.github.adetlo.payments.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Thin MockMvc wrapper returning status, headers and parsed JSON body. */
public final class ApiClient {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public ApiResponse get(String path) {
        return exchange(MockMvcRequestBuilders.get(path), Map.of());
    }

    public ApiResponse post(String path, Object body) {
        return post(path, body, Map.of());
    }

    public ApiResponse post(String path, Object body, Map<String, String> headers) {
        MockHttpServletRequestBuilder builder =
                MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(body instanceof String raw ? raw : write(body));
        }
        return exchange(builder, headers);
    }

    public ApiResponse createPayment(Map<String, Object> body) {
        return post("/api/v1/payments", body);
    }

    public ApiResponse createPayment(Map<String, Object> body, String idempotencyKey) {
        return post("/api/v1/payments", body, Map.of("Idempotency-Key", idempotencyKey));
    }

    public ApiResponse createPaymentWithFailure(Map<String, Object> body, String demoFailure) {
        return post("/api/v1/payments", body, Map.of("X-Demo-Failure", demoFailure));
    }

    public ApiResponse getPayment(String paymentId) {
        return get("/api/v1/payments/" + paymentId);
    }

    public ApiResponse cancelPayment(String paymentId, String reason) {
        return post(
                "/api/v1/payments/" + paymentId + "/cancel",
                reason == null ? null : Map.of("reason", reason));
    }

    private ApiResponse exchange(
            MockHttpServletRequestBuilder builder, Map<String, String> headers) {
        headers.forEach(builder::header);
        try {
            MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
            HttpHeaders responseHeaders = new HttpHeaders();
            response.getHeaderNames()
                    .forEach(name -> responseHeaders.addAll(name, response.getHeaders(name)));
            String raw = response.getContentAsString(StandardCharsets.UTF_8);
            JsonNode json = raw.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(raw);
            return new ApiResponse(response.getStatus(), responseHeaders, json, raw);
        } catch (Exception e) {
            throw new AssertionError("Request failed: " + e.getMessage(), e);
        }
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new AssertionError("Cannot serialise request body", e);
        }
    }

    /** An HTTP exchange result. */
    public record ApiResponse(int status, HttpHeaders headers, JsonNode body, String raw) {

        public String header(String name) {
            return headers.getFirst(name);
        }

        public String text(String field) {
            JsonNode node = body.get(field);
            return node == null || node.isNull() ? null : node.asText();
        }
    }
}
