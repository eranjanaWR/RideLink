package com.ridelink.drivervehicle.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "vehicles")
public class Vehicle {

    @Id
    private String id;

    private String driverId;

    @Indexed(unique = true)
    private String registrationNumber;

    private String make;
    private String model;
    private String color;
    private VehicleType vehicleType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Vehicle() {
    }

    public Vehicle(
            String id,
            String driverId,
            String registrationNumber,
            String make,
            String model,
            String color,
            VehicleType vehicleType,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.driverId = driverId;
        this.registrationNumber = registrationNumber;
        this.make = make;
        this.model = model;
        this.color = color;
        this.vehicleType = vehicleType;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getMake() {
        return make;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
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
