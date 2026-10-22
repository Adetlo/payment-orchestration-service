package io.github.adetlo.payments.observability;

/** MDC key names used in log output. */
public final class MdcKeys {

    public static final String REQUEST_ID = "requestId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String PAYMENT_ID = "paymentId";

    private MdcKeys() {}
}
