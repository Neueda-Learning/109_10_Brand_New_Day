package com.bnd.payment_processing.common.exception;

import com.bnd.payment_processing.payment.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/**
 * Maps every exception thrown by the API to the single {@code ErrorResponse}
 * JSON shape defined in spec.md Section 10.7. Mapping logic (status codes /
 * errorCode values / Bean Validation error aggregation) is implemented in Phase 2.
 * Owner: M3 (Tharan).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex, WebRequest request) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M3)");
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(InvalidStatusTransitionException ex, WebRequest request) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M3)");
    }

    @ExceptionHandler(InvalidRefundStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefundState(InvalidRefundStateException ex, WebRequest request) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M3)");
    }

    // TODO (Phase 2, M3): handle MethodArgumentNotValidException / ConstraintViolationException
    // -> 400 VALIDATION_ERROR, and any other uncaught exception -> 500.
}
