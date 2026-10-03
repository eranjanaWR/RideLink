package com.ridelink.farepayment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ridelink.farepayment.config.FareProperties;
import com.ridelink.farepayment.dto.CreatePaymentRequest;
import com.ridelink.farepayment.dto.PaymentResponse;
import com.ridelink.farepayment.exception.DuplicatePaymentException;
import com.ridelink.farepayment.exception.InvalidPaymentRequestException;
import com.ridelink.farepayment.exception.InvalidPaymentStateException;
import com.ridelink.farepayment.exception.PaymentNotFoundException;
import com.ridelink.farepayment.model.Payment;
import com.ridelink.farepayment.model.PaymentStatus;
import com.ridelink.farepayment.repository.PaymentRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");

    private final PaymentRepository repository;
    private final FareProperties properties;

    public PaymentService(PaymentRepository repository, FareProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public PaymentResponse createPayment(CreatePaymentRequest request) {
        if (request == null) {
            throw new InvalidPaymentRequestException("Payment request is required");
        }
        String rideId = normalizeIdentifier(request.rideId(), "rideId");
        String passengerId = normalizeIdentifier(request.passengerId(), "passengerId");
        BigDecimal amount = normalizeAmount(request.amount());
        if (request.method() == null) {
            throw new InvalidPaymentRequestException("method is required");
        }
        if (repository.findByRideId(rideId).isPresent()) {
            throw new DuplicatePaymentException(rideId);
        }

        LocalDateTime now = LocalDateTime.now();
        Payment payment = new Payment(UUID.randomUUID().toString(), rideId, passengerId, amount,
                properties.currency(), request.method(), PaymentStatus.PENDING, now, now, null);
        try {
            return PaymentResponse.from(repository.save(payment));
        } catch (DuplicateKeyException exception) {
            throw new DuplicatePaymentException(rideId);
        }
    }

    public PaymentResponse getPayment(String paymentId) {
        return PaymentResponse.from(findById(paymentId));
    }

    public PaymentResponse getPaymentByRide(String rideId) {
        String normalizedRideId = normalizeIdentifier(rideId, "rideId");
        return PaymentResponse.from(repository.findByRideId(normalizedRideId)
                .orElseThrow(PaymentNotFoundException::new));
    }

    public PaymentResponse completePayment(String paymentId) {
        Payment payment = findById(paymentId);
        requirePending(payment, "completed");
        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCompletedAt(now);
        payment.setUpdatedAt(now);
        return PaymentResponse.from(repository.save(payment));
    }

    public PaymentResponse failPayment(String paymentId) {
        Payment payment = findById(paymentId);
        requirePending(payment, "failed");
        payment.setStatus(PaymentStatus.FAILED);
        payment.setCompletedAt(null);
        payment.setUpdatedAt(LocalDateTime.now());
        return PaymentResponse.from(repository.save(payment));
    }

    private Payment findById(String paymentId) {
        String normalizedPaymentId = normalizeIdentifier(paymentId, "paymentId");
        return repository.findById(normalizedPaymentId)
                .orElseThrow(PaymentNotFoundException::new);
    }

    private void requirePending(Payment payment, String action) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(payment.getStatus(), action);
        }
    }

    private String normalizeIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidPaymentRequestException(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > 100) {
            throw new InvalidPaymentRequestException(fieldName + " must be at most 100 characters");
        }
        return normalized;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidPaymentRequestException(
                    "amount must be greater than zero and at most 1000000.00");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
