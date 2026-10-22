package io.github.adetlo.payments.application.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.adetlo.payments.application.CreatePaymentCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

/** SHA-256 over the canonical (key-sorted) JSON of the request body. */
public final class RequestHasher {

    private static final ObjectMapper CANONICAL = new ObjectMapper();

    private RequestHasher() {}

    public static String hash(CreatePaymentCommand command) {
        TreeMap<String, String> canonical = new TreeMap<>();
        canonical.put("payerAccount", command.payerAccount());
        canonical.put("payeeAccount", command.payeeAccount());
        canonical.put("amount", command.amount());
        canonical.put("currency", command.currency());
        canonical.put("reference", command.reference());
        try {
            byte[] json = CANONICAL.writeValueAsBytes(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot hash request", e);
        }
    }

    /** Hex-encoded SHA-256 of arbitrary text (used by tests and tooling). */
    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
