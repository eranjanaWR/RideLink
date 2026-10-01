package com.ridelink.farepayment.exception;

import java.time.LocalDateTime;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception,
                                                      HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid fare estimate request");
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception,
                                                      HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Malformed or invalid request body", request);
    }

    @ExceptionHandler(InvalidFareEstimateException.class)
    public ResponseEntity<ApiError> handleInvalidEstimate(InvalidFareEstimateException exception,
                                                           HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidFinalFareException.class)
    public ResponseEntity<ApiError> handleInvalidFinalFare(InvalidFinalFareException exception,
                                                            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidPaymentRequestException.class)
    public ResponseEntity<ApiError> handleInvalidPayment(InvalidPaymentRequestException exception,
                                                          HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(FareEstimateNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(FareEstimateNotFoundException exception,
                                                    HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(FinalFareNotFoundException.class)
    public ResponseEntity<ApiError> handleFinalFareNotFound(FinalFareNotFoundException exception,
                                                             HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiError> handlePaymentNotFound(PaymentNotFoundException exception,
                                                           HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({DuplicatePaymentException.class, InvalidPaymentStateException.class})
    public ResponseEntity<ApiError> handlePaymentConflict(RuntimeException exception,
                                                           HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateFinalFareException.class)
    public ResponseEntity<ApiError> handleDuplicateFinalFare(DuplicateFinalFareException exception,
                                                              HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception,
                                                      HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message,
                                            HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(LocalDateTime.now(), status.value(),
                status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
