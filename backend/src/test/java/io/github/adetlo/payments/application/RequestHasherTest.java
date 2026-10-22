package io.github.adetlo.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adetlo.payments.application.idempotency.RequestHasher;
import org.junit.jupiter.api.Test;

class RequestHasherTest {

    private static CreatePaymentCommand command(String amount, String reference, String key) {
        return new CreatePaymentCommand(
                "GB12PAYLAB00000001",
                "GB12PAYLAB00000002",
                amount,
                "GBP",
                reference,
                key,
                null,
                "corr");
    }

    @Test
    void hashIsHexSha256OfCanonicalBody() {
        String hash = RequestHasher.hash(command("125.50", "INV-1", "k"));

        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        assertThat(hash)
                .isEqualTo(
                        RequestHasher.sha256(
                                "{\"amount\":\"125.50\",\"currency\":\"GBP\",\"payeeAccount\":\"GB12PAYLAB00000002\","
                                    + "\"payerAccount\":\"GB12PAYLAB00000001\",\"reference\":\"INV-1\"}"));
    }

    @Test
    void hashIgnoresHeadersAndCorrelation() {
        assertThat(RequestHasher.hash(command("125.50", "INV-1", "key-a")))
                .isEqualTo(RequestHasher.hash(command("125.50", "INV-1", "key-b")));
    }

    @Test
    void hashChangesWhenAnyBodyFieldChanges() {
        String base = RequestHasher.hash(command("125.50", "INV-1", "k"));

        assertThat(RequestHasher.hash(command("125.51", "INV-1", "k"))).isNotEqualTo(base);
        assertThat(RequestHasher.hash(command("125.50", "INV-2", "k"))).isNotEqualTo(base);
    }
}
