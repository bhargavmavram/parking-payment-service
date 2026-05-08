package com.parking.payment.repo;

import com.parking.payment.domain.PaymentRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRuleRepository extends JpaRepository<PaymentRule, Long> {
    List<PaymentRule> findByActiveTrue();
}
