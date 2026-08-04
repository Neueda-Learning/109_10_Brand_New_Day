package com.bnd.payment_processing.payment.dto;

import java.time.Instant;

/**
 * Uniform error response shape used by every endpoint via
 * {@code GlobalExceptionHandler} (spec.md Section 10.7).
 */
public class ErrorResponse {

    private Instant timestamp;
    private int status;
    private String errorCode;
    private String message;
    private String path;

    public ErrorResponse() {
    }

    public ErrorResponse(Instant timestamp, int status, String errorCode, String message, String path) {
        this.timestamp = timestamp;
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.path = path;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
