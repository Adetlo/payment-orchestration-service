package io.github.adetlo.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountReferenceTest {

    @Test
    void acceptsSyntheticIbanLikeValues() {
        AccountReference account = new AccountReference("GB12PAYLAB00000001");

        assertThat(account.value()).isEqualTo("GB12PAYLAB00000001");
        assertThat(account.masked()).isEqualTo("GB**...0001");
        assertThat(account).hasToString("GB**...0001");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "gb12paylab00000001",
                "G12PAYLAB00000001",
                "GB1APAYLAB00000001",
                "GB12PAY",
                "GB12PAYLAB-0000001",
                ""
            })
    void rejectsMalformedValues(String value) {
        assertThatThrownBy(() -> new AccountReference(value))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsValuesLongerThanThirtyFourCharacters() {
        String tooLong = "GB12" + "A".repeat(31);

        assertThatThrownBy(() -> new AccountReference(tooLong))
                .isInstanceOf(DomainValidationException.class);
        assertThat(new AccountReference("GB12" + "A".repeat(30)).value()).hasSize(34);
    }
}
