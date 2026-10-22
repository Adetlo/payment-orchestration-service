package io.github.adetlo.payments.application.port;

/** Result of one settlement attempt. */
public record SettlementResult(SettlementOutcome outcome, String reason) {

    public static SettlementResult settled() {
        return new SettlementResult(SettlementOutcome.SETTLED, null);
    }

    public static SettlementResult transientFailure(String reason) {
        return new SettlementResult(SettlementOutcome.TRANSIENT_FAILURE, reason);
    }

    public static SettlementResult permanentFailure(String reason) {
        return new SettlementResult(SettlementOutcome.PERMANENT_FAILURE, reason);
    }

    public boolean isSettled() {
        return outcome == SettlementOutcome.SETTLED;
    }
}
