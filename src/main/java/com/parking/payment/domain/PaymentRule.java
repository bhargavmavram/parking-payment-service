package com.parking.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "payment_rules")
public class PaymentRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentRuleType ruleType = PaymentRuleType.SUCCESS;

    @Column(precision = 12, scale = 2)
    private BigDecimal amountThreshold;

    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public PaymentRuleType getRuleType() { return ruleType; }
    public void setRuleType(PaymentRuleType ruleType) { this.ruleType = ruleType; }
    public BigDecimal getAmountThreshold() { return amountThreshold; }
    public void setAmountThreshold(BigDecimal amountThreshold) { this.amountThreshold = amountThreshold; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
