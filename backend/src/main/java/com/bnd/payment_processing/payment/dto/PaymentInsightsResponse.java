package com.bnd.payment_processing.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Response body for {@code GET /api/payments/insights} (spec.md Section 10.10).
 * Aggregate-only - never carries individual payment rows.
 */
public class PaymentInsightsResponse {

    private long totalCount;
    private BigDecimal totalAmount;
    private Map<String, Long> countByStatus;
    private Map<String, Long> countByType;
    private Map<String, BigDecimal> amountByType;
    private Double successRate;
    private Double refundRate;
    private long pendingApprovalCount;
    private java.util.List<DailyVolumeEntry> dailyVolume;

    public PaymentInsightsResponse() {
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Map<String, Long> getCountByStatus() {
        return countByStatus;
    }

    public void setCountByStatus(Map<String, Long> countByStatus) {
        this.countByStatus = countByStatus;
    }

    public Map<String, Long> getCountByType() {
        return countByType;
    }

    public void setCountByType(Map<String, Long> countByType) {
        this.countByType = countByType;
    }

    public Map<String, BigDecimal> getAmountByType() {
        return amountByType;
    }

    public void setAmountByType(Map<String, BigDecimal> amountByType) {
        this.amountByType = amountByType;
    }

    public Double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(Double successRate) {
        this.successRate = successRate;
    }

    public Double getRefundRate() {
        return refundRate;
    }

    public void setRefundRate(Double refundRate) {
        this.refundRate = refundRate;
    }

    public long getPendingApprovalCount() {
        return pendingApprovalCount;
    }

    public void setPendingApprovalCount(long pendingApprovalCount) {
        this.pendingApprovalCount = pendingApprovalCount;
    }

    public java.util.List<DailyVolumeEntry> getDailyVolume() {
        return dailyVolume;
    }

    public void setDailyVolume(java.util.List<DailyVolumeEntry> dailyVolume) {
        this.dailyVolume = dailyVolume;
    }

    /** One day's worth of volume, ordered oldest-to-newest in {@code dailyVolume}. */
    public static class DailyVolumeEntry {
        private LocalDate date;
        private long count;
        private BigDecimal amount;

        public DailyVolumeEntry() {
        }

        public DailyVolumeEntry(LocalDate date, long count, BigDecimal amount) {
            this.date = date;
            this.count = count;
            this.amount = amount;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }
}
