package org.example.notificationservice.model;

public final class AuditEventType {
    public static final String POLICY_CREATED = "POLICY_CREATED";
    public static final String POLICY_APPROVED = "POLICY_APPROVED";
    public static final String POLICY_REJECTED = "POLICY_REJECTED";
    public static final String POLICY_CANCELLED = "POLICY_CANCELLED";
    public static final String POLICY_EXPIRED = "POLICY_EXPIRED";
    public static final String CLAIM_CREATED = "CLAIM_CREATED";
    public static final String CLAIM_UNDER_REVIEW = "CLAIM_UNDER_REVIEW";
    public static final String CLAIM_APPROVED = "CLAIM_APPROVED";
    public static final String CLAIM_REJECTED = "CLAIM_REJECTED";
    public static final String CLAIM_CANCELLED = "CLAIM_CANCELLED";
    public static final String CLAIM_SETTLED = "CLAIM_SETTLED";
    public static final String TRIGGER_DETECTED = "TRIGGER_DETECTED";
    public static final String TRIGGER_VERIFIED = "TRIGGER_VERIFIED";
    public static final String TRIGGER_REJECTED = "TRIGGER_REJECTED";
    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYOUT_CREATED = "PAYOUT_CREATED";
    public static final String PAYOUT_COMPLETED = "PAYOUT_COMPLETED";
    public static final String PAYOUT_FAILED = "PAYOUT_FAILED";

    private AuditEventType() {}
}
