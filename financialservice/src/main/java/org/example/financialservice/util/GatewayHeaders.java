package org.example.financialservice.util;

public final class GatewayHeaders {
    public static final String GATEWAY_KEY = "X-Gateway-Key";
    public static final String USER_ID = "X-Authenticated-User-Id";
    public static final String USER_EMAIL = "X-Authenticated-User";
    public static final String USER_ROLE = "X-Authenticated-Role";
    public static final String CUSTOMER_ID = "X-Authenticated-Customer-Id";

    private GatewayHeaders() {}
}