package com.parking.payment.repo;

import com.parking.payment.domain.MockCard;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MockCardRepository extends JpaRepository<MockCard, Long> {
    Optional<MockCard> findByCardNumber(String cardNumber);
}
