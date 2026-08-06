package com.bnd.payment_processing.common.exception;

import com.bnd.payment_processing.payment.dto.ErrorResponse;
import com.bnd.payment_processing.payment.dto.PaymentMapper;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Maps every exception thrown by the API to the single {@code ErrorResponse}
 * JSON shape defined in spec.md Section 10.7. Mapping logic (status codes /
 * errorCode values / Bean Validation error aggregation) is implemented in Phase 2.
 * Owner: M3 (Tharan).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex, WebRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(InvalidStatusTransitionException ex, WebRequest request) {
        return buildError(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRefundStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefundState(InvalidRefundStateException ex, WebRequest request) {
        return buildError(HttpStatus.CONFLICT, "INVALID_REFUND_STATE", ex.getMessage(), request);
    }

    // Added 2026-08-05 (spec.md Section 8.1 rule 6 / Section 10.8-10.9, v2.2)
    @ExceptionHandler(RefundNotApprovedException.class)
    public ResponseEntity<ErrorResponse> handleRefundNotApproved(RefundNotApprovedException ex, WebRequest request) {
        return buildError(HttpStatus.CONFLICT, "REFUND_NOT_APPROVED", ex.getMessage(), request);
    }

    // --- Added 2026-08-06 (bank-grade account/card/currency validation hardening) ---

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex, WebRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(AccountBlockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountBlocked(AccountBlockedException ex, WebRequest request) {
        return buildError(HttpStatus.CONFLICT, "ACCOUNT_BLOCKED", ex.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedCurrencyException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedCurrency(UnsupportedCurrencyException ex, WebRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CURRENCY", ex.getMessage(), request);
    }

    @ExceptionHandler(CardNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCardNotFound(CardNotFoundException ex, WebRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(CardDeclinedException.class)
    public ResponseEntity<ErrorResponse> handleCardDeclined(CardDeclinedException ex, WebRequest request) {
        return buildError(HttpStatus.CONFLICT, "CARD_DECLINED", ex.getMessage(), request);
    }

    @ExceptionHandler(SegregationOfDutiesException.class)
    public ResponseEntity<ErrorResponse> handleSegregationOfDuties(SegregationOfDutiesException ex, WebRequest request) {
        return buildError(HttpStatus.CONFLICT, "SEGREGATION_OF_DUTIES_VIOLATION", ex.getMessage(), request);
    }

    /**
     * Not a real error - a duplicate idempotency_key on POST /api/payments means
     * "return the original resource" (spec.md Section 10.7), so this bypasses the
     * ErrorResponse envelope entirely and returns the existing payment with 200 OK.
     */
    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<PaymentResponse> handleDuplicatePayment(DuplicatePaymentException ex) {
        return ResponseEntity.ok(PaymentMapper.toResponse(ex.getExistingPayment()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    // Covers manual query-param validation (e.g. M4's status/type filters) that isn't Bean Validation.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception on {}", request.getDescription(false), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> buildError(HttpStatus status, String errorCode, String message, WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), errorCode, message, path);
        return ResponseEntity.status(status).body(body);
    }
}
