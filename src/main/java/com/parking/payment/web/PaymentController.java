package com.parking.payment.web;

import com.parking.payment.domain.*;
import com.parking.payment.repo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping
public class PaymentController {
    private final MockCardRepository cards;
    private final PaymentRuleRepository rules;
    private final PaymentTransactionRepository transactions;

    public PaymentController(MockCardRepository cards, PaymentRuleRepository rules, PaymentTransactionRepository transactions) {
        this.cards = cards;
        this.rules = rules;
        this.transactions = transactions;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse("parking-payment-service", "UP");
    }

    @PostMapping("/test-cards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PAYMENT_TESTER')")
    public MockCard createCard(@Valid @RequestBody CardRequest request) {
        MockCard card = new MockCard();
        card.setCardNumber(request.cardNumber());
        card.setCardHolder(request.cardHolder());
        card.setBalance(request.balance());
        card.setExpiryMonth(request.expiryMonth());
        card.setExpiryYear(request.expiryYear());
        card.setStatus(MockCardStatus.ACTIVE);
        return cards.save(card);
    }

    @GetMapping("/test-cards")
    @PreAuthorize("hasAnyRole('ADMIN','PAYMENT_TESTER','SUPPORT')")
    public List<MockCard> testCards() { return cards.findAll(); }

    @PatchMapping("/test-cards/{cardId}/balance")
    @PreAuthorize("hasAnyRole('ADMIN','PAYMENT_TESTER')")
    public MockCard updateBalance(@PathVariable Long cardId, @Valid @RequestBody BalanceRequest request) {
        MockCard card = cards.findById(cardId).orElseThrow(() -> notFound("Card not found"));
        card.setBalance(request.balance());
        return cards.save(card);
    }

    @PostMapping("/test-cards/{cardId}/expire")
    @PreAuthorize("hasAnyRole('ADMIN','PAYMENT_TESTER')")
    public MockCard expireCard(@PathVariable Long cardId) {
        MockCard card = cards.findById(cardId).orElseThrow(() -> notFound("Card not found"));
        card.setStatus(MockCardStatus.EXPIRED);
        return cards.save(card);
    }

    @PostMapping("/test-cards/{cardId}/block")
    @PreAuthorize("hasAnyRole('ADMIN','PAYMENT_TESTER')")
    public MockCard blockCard(@PathVariable Long cardId) {
        MockCard card = cards.findById(cardId).orElseThrow(() -> notFound("Card not found"));
        card.setStatus(MockCardStatus.BLOCKED);
        return cards.save(card);
    }

    @PostMapping("/payment-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PAYMENT_TESTER')")
    public PaymentRule createRule(@Valid @RequestBody RuleRequest request) {
        PaymentRule rule = new PaymentRule();
        rule.setRuleType(request.ruleType());
        rule.setAmountThreshold(request.amountThreshold());
        rule.setActive(request.active());
        return rules.save(rule);
    }

    @GetMapping("/payment-rules")
    @PreAuthorize("hasAnyRole('ADMIN','PAYMENT_TESTER','SUPPORT')")
    public List<PaymentRule> paymentRules() { return rules.findAll(); }

    @PostMapping("/payments/intent")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','USER','MANAGER','EMPLOYEE','PARKING_OWNER')")
    public PaymentTransaction createPaymentIntent(@Valid @RequestBody PaymentIntentRequest request, Authentication authentication) {
        cards.findById(request.cardId()).orElseThrow(() -> notFound("Card not found"));
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setUsername(authentication.getName());
        transaction.setAmount(request.amount());
        transaction.setCardId(request.cardId());
        transaction.setBookingId(request.bookingId());
        transaction.setSessionId(request.sessionId());
        transaction.setStatus(PaymentStatus.CREATED);
        return transactions.save(transaction);
    }

    @PostMapping("/payments/{paymentId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','USER','MANAGER','EMPLOYEE','PARKING_OWNER')")
    public PaymentTransaction confirmPayment(@PathVariable Long paymentId) {
        PaymentTransaction transaction = transactions.findById(paymentId).orElseThrow(() -> notFound("Payment not found"));
        MockCard card = cards.findById(transaction.getCardId()).orElseThrow(() -> notFound("Card not found"));
        String failure = evaluateFailure(transaction, card);
        if (failure == null) {
            card.setBalance(card.getBalance().subtract(transaction.getAmount()));
            cards.save(card);
            transaction.setStatus(PaymentStatus.SUCCEEDED);
            transaction.setFailureReason(null);
        } else {
            transaction.setStatus(PaymentStatus.DECLINED);
            transaction.setFailureReason(failure);
        }
        return transactions.save(transaction);
    }

    @PostMapping("/payments/{paymentId}/refund")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PAYMENT_TESTER','SUPPORT')")
    public PaymentTransaction refund(@PathVariable Long paymentId) {
        PaymentTransaction transaction = transactions.findById(paymentId).orElseThrow(() -> notFound("Payment not found"));
        if (transaction.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only succeeded payments can be refunded");
        }
        MockCard card = cards.findById(transaction.getCardId()).orElseThrow(() -> notFound("Card not found"));
        card.setBalance(card.getBalance().add(transaction.getAmount()));
        cards.save(card);
        transaction.setStatus(PaymentStatus.REFUNDED);
        return transactions.save(transaction);
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','PAYMENT_TESTER','SUPPORT')")
    public List<PaymentTransaction> payments() { return transactions.findAll(); }

    @GetMapping("/payments/me")
    @PreAuthorize("hasAnyRole('ADMIN','USER','MANAGER','EMPLOYEE','PARKING_OWNER')")
    public List<PaymentTransaction> myPayments(Authentication authentication) {
        return transactions.findByUsername(authentication.getName());
    }

    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasAnyRole('ADMIN','USER','MANAGER','EMPLOYEE','PARKING_OWNER','PAYMENT_TESTER','SUPPORT')")
    public PaymentTransaction payment(@PathVariable Long paymentId) {
        return transactions.findById(paymentId).orElseThrow(() -> notFound("Payment not found"));
    }

    private String evaluateFailure(PaymentTransaction transaction, MockCard card) {
        if (card.getStatus() == MockCardStatus.EXPIRED) return "CARD_EXPIRED";
        if (card.getStatus() == MockCardStatus.BLOCKED) return "CARD_BLOCKED";
        for (PaymentRule rule : rules.findByActiveTrue()) {
            if (rule.getRuleType() == PaymentRuleType.DECLINE_ALWAYS) return "DECLINED_BY_ACTIVE_RULE";
            if (rule.getRuleType() == PaymentRuleType.DECLINE_IF_AMOUNT_ABOVE
                    && rule.getAmountThreshold() != null
                    && transaction.getAmount().compareTo(rule.getAmountThreshold()) > 0) return "AMOUNT_ABOVE_RULE_THRESHOLD";
        }
        if (card.getBalance().compareTo(transaction.getAmount()) < 0) return "INSUFFICIENT_BALANCE";
        return null;
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public record StatusResponse(String service, String status) {}
    public record CardRequest(@NotBlank String cardNumber, @NotBlank String cardHolder, @NotNull @DecimalMin("0.00") BigDecimal balance, @Min(1) @Max(12) int expiryMonth, @Min(2024) int expiryYear) {}
    public record BalanceRequest(@NotNull @DecimalMin("0.00") BigDecimal balance) {}
    public record RuleRequest(@NotNull PaymentRuleType ruleType, BigDecimal amountThreshold, boolean active) {}
    public record PaymentIntentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount, @NotNull Long cardId, Long bookingId, Long sessionId) {}
}
