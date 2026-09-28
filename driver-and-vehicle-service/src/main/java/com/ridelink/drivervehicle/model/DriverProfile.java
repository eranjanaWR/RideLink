package com.ridelink.drivervehicle.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "drivers")
public class DriverProfile {

    @Id
    private String id;

    @Indexed(unique = true)
    private String accountId;

    private String licenseNumber;
    private String serviceArea;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DriverProfile() {
    }

    public DriverProfile(
            String id,
            String accountId,
            String licenseNumber,
            String serviceArea,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.accountId = accountId;
        this.licenseNumber = licenseNumber;
        this.serviceArea = serviceArea;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getServiceArea() {
        return serviceArea;
    }

    public void setServiceArea(String serviceArea) {
        this.serviceArea = serviceArea;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
