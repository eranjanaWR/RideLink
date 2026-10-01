package com.ridelink.farepayment.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "payments")
public class Payment {
    @Id
    private final String id;
    @Indexed(unique = true)
    private final String rideId;
    private final String passengerId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentMethod method;
    private PaymentStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public Payment(String id, String rideId, String passengerId, BigDecimal amount,
                   String currency, PaymentMethod method, PaymentStatus status,
                   LocalDateTime createdAt, LocalDateTime updatedAt,
                   LocalDateTime completedAt) {
        this.id = id;
        this.rideId = rideId;
        this.passengerId = passengerId;
        this.amount = amount;
        this.currency = currency;
        this.method = method;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
    }

    public String getId() { return id; }
    public String getRideId() { return rideId; }
    public String getPassengerId() { return passengerId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
