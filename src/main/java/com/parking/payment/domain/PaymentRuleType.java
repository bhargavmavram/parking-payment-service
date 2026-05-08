package com.parking.payment.domain;

public enum PaymentRuleType {
    SUCCESS,
    DECLINE_ALWAYS,
    DECLINE_IF_AMOUNT_ABOVE,
    DECLINE_IF_CARD_EXPIRED,
    DECLINE_IF_INSUFFICIENT_BALANCE
}
