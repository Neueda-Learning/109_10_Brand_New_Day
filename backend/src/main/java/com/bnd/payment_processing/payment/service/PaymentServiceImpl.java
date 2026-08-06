package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.common.exception.AccountBlockedException;
import com.bnd.payment_processing.common.exception.AccountNotFoundException;
import com.bnd.payment_processing.common.exception.DuplicatePaymentException;
import com.bnd.payment_processing.common.exception.InvalidRefundStateException;
import com.bnd.payment_processing.common.exception.InvalidStatusTransitionException;
import com.bnd.payment_processing.common.exception.PaymentNotFoundException;
import com.bnd.payment_processing.common.exception.RefundNotApprovedException;
import com.bnd.payment_processing.common.exception.SegregationOfDutiesException;
import com.bnd.payment_processing.common.exception.UnsupportedCurrencyException;
import com.bnd.payment_processing.payment.dto.ApproveRefundRequest;
import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.PaymentHistoryEntry;
import com.bnd.payment_processing.payment.dto.PaymentMapper;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import com.bnd.payment_processing.payment.dto.ProcessRequest;
import com.bnd.payment_processing.payment.dto.RefundRequest;
import com.bnd.payment_processing.payment.dto.RejectRefundRequest;
import com.bnd.payment_processing.payment.model.Account;
import com.bnd.payment_processing.payment.model.AccountStatus;
import com.bnd.payment_processing.payment.model.ApprovalStatus;
import com.bnd.payment_processing.payment.model.Card;
import com.bnd.payment_processing.payment.model.CardStatus;
import com.bnd.payment_processing.payment.model.ExchangeRate;
import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.PaymentMethod;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentStatusHistory;
import com.bnd.payment_processing.payment.model.PaymentType;
import com.bnd.payment_processing.payment.repository.AccountRepository;
import com.bnd.payment_processing.payment.repository.CardRepository;
import com.bnd.payment_processing.payment.repository.ExchangeRateRepository;
import com.bnd.payment_processing.payment.repository.PaymentRepository;
import com.bnd.payment_processing.payment.repository.PaymentStatusHistoryRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link PaymentService}. Method bodies are stubs until
 * Phase 2 - see spec.md Section 9 for the per-module implementation checklist.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String SYSTEM_TRIGGER = "SYSTEM";

    // Added 2026-08-06 (bank-grade CARD hardening): fixed demo CVV, never stored in
    // the DB anywhere - the incoming request's cvv is compared to this constant and
    // then discarded. Every seeded demo card uses this same CVV for simplicity.
    private static final String DEMO_CARD_CVV = "123";

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository paymentStatusHistoryRepository;
    private final AccountRepository accountRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final CardRepository cardRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              PaymentStatusHistoryRepository paymentStatusHistoryRepository,
                              AccountRepository accountRepository,
                              ExchangeRateRepository exchangeRateRepository,
                              CardRepository cardRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentStatusHistoryRepository = paymentStatusHistoryRepository;
        this.accountRepository = accountRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.cardRepository = cardRepository;
    }

    /**
     * Account existence + status check (bank-grade validation, added 2026-08-06).
     * Why: real core-banking systems reject a payment referencing an unknown or
     * blocked/closed account before any money movement is simulated - this is the
     * account-number-check equivalent that stands in for real authentication, which
     * stays explicitly out of scope for this project.
     */
    private Account requireActiveAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountBlockedException(accountNumber, account.getStatus().name());
        }
        return account;
    }

    /**
     * FX snapshot (added 2026-08-06): looks up the fixed/seeded rate for the
     * received currency and freezes it + the computed INR settlement amount onto
     * the payment at creation time. Frozen means never recomputed by any later
     * transition (process/refund) - a later rate change must not retroactively
     * alter an already-created payment's settled value.
     */
    private void applySettlementSnapshot(Payment payment, String currency, BigDecimal amount) {
        ExchangeRate rate = exchangeRateRepository.findByCurrency(currency)
                .orElseThrow(() -> new UnsupportedCurrencyException(currency));
        payment.setSettlementCurrency("INR");
        payment.setFxRateToInr(rate.getRateToInr());
        payment.setSettlementAmountInr(amount.multiply(rate.getRateToInr()).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Non-throwing counterpart to the old creation-time CARD validation (removed
     * 2026-08-06): a wrong CVV/blocked/expired card must NOT block payment creation
     * anymore - same reasoning as the insufficient-funds and account-not-found fixes
     * below. The CVV itself is still never persisted anywhere (matched against
     * {@link #DEMO_CARD_CVV} right here and then discarded) - only the resulting
     * decline *classification* (a plain errorCode string, no card/CVV data) is
     * written onto the payment so it can be surfaced later when the lifecycle
     * reaches the CREATED -> VALIDATED step. Populates whatever card snapshot fields
     * are available onto {@code payment} even when the card will ultimately be
     * declined, and returns the errorCode to record (or {@code null} if the card is fine).
     */
    private String validateCardForPaymentSoft(Payment payment, String cardId, String cvv) {
        Card card;
        try {
            card = cardRepository.findById(UUID.fromString(cardId)).orElse(null);
        } catch (IllegalArgumentException notUuid) {
            card = null;
        }
        if (card == null) {
            return "CARD_NOT_FOUND";
        }
        payment.setCardId(card.getId());
        payment.setCardLast4(card.getLast4());
        payment.setCardBrand(card.getCardBrand());

        if (card.getStatus() != CardStatus.ACTIVE) {
            return "CARD_DECLINED";
        }
        YearMonth expiry = YearMonth.of(card.getExpiryYear(), card.getExpiryMonth());
        if (expiry.isBefore(YearMonth.now())) {
            return "CARD_DECLINED";
        }
        if (cvv == null || !cvv.matches("\\d{3,4}") || !cvv.equals(DEMO_CARD_CVV)) {
            return "CARD_DECLINED";
        }
        return null;
    }

    /**
     * Non-throwing account existence/status check (changed 2026-08-06, deferred from
     * creation time - same reasoning as {@link #validateCardForPaymentSoft}): a typo'd
     * or blocked account number must not block payment creation anymore. Returns the
     * errorCode to record if either account is missing/blocked (source checked first),
     * or {@code null} if both are fine. Used at the CREATED -> VALIDATED lifecycle step.
     */
    private String findAccountValidationError(String sourceAccount, String destinationAccount) {
        Optional<Account> source = accountRepository.findByAccountNumber(sourceAccount);
        if (source.isEmpty()) {
            return "ACCOUNT_NOT_FOUND";
        }
        if (source.get().getStatus() != AccountStatus.ACTIVE) {
            return "ACCOUNT_BLOCKED";
        }
        Optional<Account> destination = accountRepository.findByAccountNumber(destinationAccount);
        if (destination.isEmpty()) {
            return "ACCOUNT_NOT_FOUND";
        }
        if (destination.get().getStatus() != AccountStatus.ACTIVE) {
            return "ACCOUNT_BLOCKED";
        }
        return null;
    }

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        // M1 validation rule (spec.md Section 9): sourceAccount must differ from
        // destinationAccount. IllegalArgumentException is mapped to 400
        // VALIDATION_ERROR by GlobalExceptionHandler.
        if (request.getSourceAccount().equals(request.getDestinationAccount())) {
            throw new IllegalArgumentException("sourceAccount and destinationAccount must be different");
        }

        // Account existence/active-status is intentionally NOT checked here (changed
        // 2026-08-06, same reasoning as the solvency guard below): a typo'd or
        // blocked account number must not block payment creation. The payment is
        // always created and the accounts are re-checked at the CREATED -> VALIDATED
        // lifecycle step in processTransition() below, which degrades that transition
        // to FAILED/ACCOUNT_NOT_FOUND or FAILED/ACCOUNT_BLOCKED instead of throwing here.

        Instant now = Instant.now();

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setSourceAccount(request.getSourceAccount());
        payment.setDestinationAccount(request.getDestinationAccount());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setType(PaymentType.PAYMENT);
        payment.setOriginalPaymentId(null);
        payment.setRequestedBy(request.getSourceAccount());
        // paymentMethod (spec.md Section 10.1, v2.2): optional in the request, defaults
        // to BANK_TRANSFER server-side if omitted. Reuses the existing parseEnum() helper.
        PaymentMethod method = request.getPaymentMethod() == null || request.getPaymentMethod().isBlank()
                ? PaymentMethod.BANK_TRANSFER
                : parseEnum(PaymentMethod.class, request.getPaymentMethod(), "paymentMethod");
        payment.setPaymentMethod(method);

        // CARD-method validation (changed 2026-08-06): a wrong CVV/blocked/expired
        // card must not block payment creation either - same reasoning as above. The
        // CVV is still verified right here and never persisted anywhere; only the
        // resulting decline classification (a plain errorCode, no card/CVV data) is
        // recorded on the payment so it can surface later at the CREATED -> VALIDATED
        // step. cardId is still required up front - that's a request-shape error
        // (missing field), not a "this card is bad" lifecycle failure.
        if (method == PaymentMethod.CARD) {
            if (request.getCardId() == null || request.getCardId().isBlank()) {
                throw new IllegalArgumentException("cardId is required when paymentMethod is CARD");
            }
            String cardError = validateCardForPaymentSoft(payment, request.getCardId(), request.getCvv());
            if (cardError != null) {
                payment.setErrorCode(cardError);
            }
        }

        // Multi-currency, settle-in-INR FX snapshot (added 2026-08-06) - frozen now,
        // never recomputed later.
        applySettlementSnapshot(payment, request.getCurrency(), request.getAmount());

        // Solvency is intentionally NOT checked here (changed 2026-08-06): a payment
        // must always be allowed to be CREATED regardless of balance - insufficient
        // funds is a lifecycle/settlement-time concern, not a creation-time rejection.
        // The payment gets created, moves through its normal CREATED -> VALIDATED ->
        // ROUTED -> SENT lifecycle, and is only ever flagged/failed for insufficient
        // funds at the authoritative, race-safe atomic debitIfSufficient() re-check in
        // processTransition() below (the -> COMPLETED transition), which degrades the
        // transition to FAILED/INSUFFICIENT_FUNDS instead of throwing at creation.

        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        // Idempotency (spec.md Section 8.3): attempt the insert directly rather than
        // "check then insert" - the unique constraint on idempotency_key is the single
        // source of truth. On a duplicate key, re-fetch and let the caller (via
        // GlobalExceptionHandler) return the existing row as a 200 short-circuit
        // instead of creating a second row.
        try {
            paymentRepository.insert(payment);
        } catch (DuplicateKeyException e) {
            Payment existing = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> e);
            throw new DuplicatePaymentException(existing);
        }

        PaymentStatusHistory initialHistory = new PaymentStatusHistory();
        initialHistory.setId(UUID.randomUUID());
        initialHistory.setPaymentId(payment.getId());
        initialHistory.setFromStatus(null);
        initialHistory.setToStatus(PaymentStatus.CREATED);
        initialHistory.setChangedAt(now);
        initialHistory.setTriggeredBy(SYSTEM_TRIGGER);
        initialHistory.setNote(null);
        paymentStatusHistoryRepository.insert(initialHistory);

        return PaymentMapper.toResponse(payment);
    }

    @Override
    public PaymentResponse getPayment(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse processTransition(UUID id, ProcessRequest request) {
        Payment current = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        // Refund approval gate (spec.md Section 8.1 rule 6, added 2026-08-05): a REFUND
        // row can never advance past CREATED until a business user has approved it via
        // POST /refund/approve. Guarded strictly on type == REFUND so PAYMENT-type
        // transitions (M1/M2) are completely unaffected by this check.
        if (current.getType() == PaymentType.REFUND
                && current.getStatus() == PaymentStatus.CREATED
                && current.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new RefundNotApprovedException(
                    "Refund " + id + " cannot be processed until it has been approved (current approvalStatus: "
                            + current.getApprovalStatus() + ")");
        }

        PaymentStatus nextStatus = getNextStatus(current.getStatus(), request);
        validateTransition(current, nextStatus, request);

        String errorCode = null;
        if (nextStatus == PaymentStatus.FAILED) {
            errorCode = request.getErrorCode().trim();
        }

        // Bank-grade validation guards, deferred from creation time (changed
        // 2026-08-06): a typo'd/blocked account number or a bad CARD (wrong CVV,
        // blocked, expired) no longer reject the payment/refund at creation - they
        // must always be allowed to enter the lifecycle and only get flagged/failed
        // here, at the CREATED -> VALIDATED step, exactly like a real payment
        // "sailing" through validation before being declined. Any CARD decline
        // reason pre-flagged onto errorCode at creation time (never the raw CVV
        // itself - see validateCardForPaymentSoft()) takes priority over the account
        // check since it was already known at creation.
        if (nextStatus == PaymentStatus.VALIDATED) {
            String validationError = (current.getErrorCode() != null && !current.getErrorCode().isBlank())
                    ? current.getErrorCode()
                    : findAccountValidationError(current.getSourceAccount(), current.getDestinationAccount());
            if (validationError != null) {
                nextStatus = PaymentStatus.FAILED;
                errorCode = validationError;
            }
        }

        // Bank-grade settlement guards (added 2026-08-06 hotfix): a payment/refund
        // only actually moves money when it reaches COMPLETED, so this is the one
        // point where solvency/account-status MUST be re-verified atomically -
        // checking once at creation time (as before this hotfix) is not enough,
        // since balances/account status can change in between. If either guard
        // fails, the transition is degraded to FAILED instead of completing, and no
        // partial balance effect (e.g. debit without credit) is ever applied.
        BigDecimal settled = current.getSettlementAmountInr() == null ? BigDecimal.ZERO : current.getSettlementAmountInr();
        boolean creditDestination = false;
        if (nextStatus == PaymentStatus.COMPLETED) {
            boolean sourceStillActive = accountRepository.findByAccountNumber(current.getSourceAccount())
                    .map(a -> a.getStatus() == AccountStatus.ACTIVE).orElse(false);
            boolean destinationStillActive = accountRepository.findByAccountNumber(current.getDestinationAccount())
                    .map(a -> a.getStatus() == AccountStatus.ACTIVE).orElse(false);

            if (!sourceStillActive || !destinationStillActive) {
                nextStatus = PaymentStatus.FAILED;
                errorCode = "ACCOUNT_BLOCKED";
            } else if (accountRepository.debitIfSufficient(current.getSourceAccount(), settled) == 0) {
                // Atomic conditional debit failed -> insufficient funds at this exact
                // moment (this is the real, race-safe guard - not just a read-then-write
                // check). This is the fix for the bug where a payment could complete
                // and drive an account balance negative.
                nextStatus = PaymentStatus.FAILED;
                errorCode = "INSUFFICIENT_FUNDS";
            } else {
                creditDestination = true;
            }
        }

        int rowsAffected = paymentRepository.updateStatusIfCurrent(
                id,
                current.getStatus().name(),
                nextStatus.name(),
                errorCode
        );

        if (rowsAffected == 0) {
            throw new InvalidStatusTransitionException(
                    "Payment status has changed since read; expected " + current.getStatus()
            );
        }

        Instant now = Instant.now();
        PaymentStatusHistory historyEntry = new PaymentStatusHistory();
        historyEntry.setId(UUID.randomUUID());
        historyEntry.setPaymentId(id);
        historyEntry.setFromStatus(current.getStatus());
        historyEntry.setToStatus(nextStatus);
        historyEntry.setChangedAt(now);
        historyEntry.setTriggeredBy(SYSTEM_TRIGGER);

        String requestNote = (request != null && request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote().trim()
                : null;
        if (nextStatus == PaymentStatus.FAILED) {
            historyEntry.setNote(requestNote == null ? errorCode : requestNote + " | errorCode=" + errorCode);
        } else {
            historyEntry.setNote(requestNote);
        }

        paymentStatusHistoryRepository.insert(historyEntry);

        // Only credit the destination if the atomic debit above actually succeeded -
        // never credit without a matching successful debit.
        if (creditDestination) {
            accountRepository.adjustBalance(current.getDestinationAccount(), settled);
        }

        Payment updated = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentMapper.toResponse(updated);
    }

    private PaymentStatus getNextStatus(PaymentStatus currentStatus, ProcessRequest request) {
        switch (currentStatus) {
            case CREATED:
                return PaymentStatus.VALIDATED;
            case VALIDATED:
                return PaymentStatus.SENT;
            case SENT:
                String target = (request != null && request.getTargetStatus() != null)
                        ? request.getTargetStatus()
                        : "COMPLETED";
                if ("COMPLETED".equals(target)) {
                    return PaymentStatus.COMPLETED;
                } else if ("FAILED".equals(target)) {
                    return PaymentStatus.FAILED;
                } else {
                    throw new InvalidStatusTransitionException(
                            "For SENT status, targetStatus must be 'COMPLETED' or 'FAILED', got: " + target
                    );
                }
            case COMPLETED:
            case FAILED:
                throw new InvalidStatusTransitionException(
                        "Cannot transition from terminal status " + currentStatus
                );
            default:
                throw new InvalidStatusTransitionException("Unknown status: " + currentStatus);
        }
    }

    private void validateTransition(Payment payment, PaymentStatus nextStatus, ProcessRequest request) {

        if (request != null && request.getTargetStatus() != null) {
            if (payment.getStatus() != PaymentStatus.SENT) {
                throw new InvalidStatusTransitionException(
                        "targetStatus can only be specified when current status is SENT, got: "
                                + payment.getStatus()
                );
            }
        }

        if (nextStatus == PaymentStatus.FAILED) {
            if (request == null || request.getErrorCode() == null || request.getErrorCode().isBlank()) {
                throw new IllegalArgumentException(
                        "errorCode is required when transitioning to FAILED status"
                );
            }
        }
    }

    @Override
    public List<PaymentHistoryEntry> getHistory(UUID id) {
        // Ensure payment exists (throw 404 if not)
        paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        // Fetch and convert history records
        List<PaymentStatusHistory> historyRecords = paymentStatusHistoryRepository
                .findByPaymentIdOrderByChangedAtAsc(id);

        return historyRecords.stream()
                .map(this::toHistoryEntry)
                .toList();
    }

    /**
     * Convert a PaymentStatusHistory DB record to a DTO for API response.
     */
    private PaymentHistoryEntry toHistoryEntry(PaymentStatusHistory h) {
        PaymentHistoryEntry entry = new PaymentHistoryEntry();
        entry.setFromStatus(h.getFromStatus());
        entry.setToStatus(h.getToStatus());
        entry.setChangedAt(h.getChangedAt());
        entry.setTriggeredBy(h.getTriggeredBy());
        entry.setNote(h.getNote());
        return entry;
    }

    @Override
    @Transactional
    public PaymentResponse createRefund(UUID originalId, RefundRequest request) {
        // Locked read (spec.md Section 8.3, added 2026-08-05): take a row lock on the
        // original payment before computing the cumulative refunded total below, so two
        // near-simultaneous refund requests against the same payment can't both pass the
        // amount check before either commits.
        Payment original = paymentRepository.findByIdForUpdate(originalId)
                .orElseThrow(() -> new PaymentNotFoundException(originalId));

        if (original.getType() != PaymentType.PAYMENT) {
            throw new InvalidRefundStateException(
                    "Payment " + originalId + " is a REFUND and cannot itself be refunded");
        }
        if (original.getStatus() != PaymentStatus.COMPLETED) {
            throw new InvalidRefundStateException(
                    "Payment " + originalId + " must be COMPLETED to be refunded, was " + original.getStatus());
        }

        // Cumulative refund amount check (spec.md Section 8.1, rule 4) - must happen in
        // the same transaction as the insert below so two concurrent refund requests
        // can't both pass the check (spec.md Section 8.3).
        BigDecimal alreadyRefunded = paymentRepository.sumRefundedAmount(originalId);
        BigDecimal newTotal = alreadyRefunded.add(request.getAmount());
        if (newTotal.compareTo(original.getAmount()) > 0) {
            throw new InvalidRefundStateException(
                    "Refund amount " + request.getAmount() + " would exceed the refundable balance of payment "
                            + originalId + " (already refunded " + alreadyRefunded + " of " + original.getAmount() + ")");
        }

        // Bank-grade re-check (added 2026-08-06): an account can be blocked between
        // the original payment completing and the refund being requested - re-verify
        // both accounts (swapped for the refund direction) are still ACTIVE.
        requireActiveAccount(original.getDestinationAccount());
        requireActiveAccount(original.getSourceAccount());

        Instant now = Instant.now();
        Payment refund = new Payment();
        refund.setId(UUID.randomUUID());
        // Refunds have no client-supplied idempotency key (RefundRequest doesn't carry
        // one) but the column is NOT NULL UNIQUE, so generate a synthetic one.
        refund.setIdempotencyKey(request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()
                ? request.getIdempotencyKey()
                : "refund-" + refund.getId());
        // A refund reverses the money flow: the original payee now sends money back to
        // the original payer, so source/destination are swapped relative to the original.
        refund.setSourceAccount(original.getDestinationAccount());
        refund.setDestinationAccount(original.getSourceAccount());
        refund.setAmount(request.getAmount());
        refund.setCurrency(original.getCurrency());
        refund.setStatus(PaymentStatus.CREATED);
        refund.setType(PaymentType.REFUND);
        refund.setOriginalPaymentId(original.getId());
        // Segregation-of-duties (added 2026-08-06): capture who requested this refund
        // so approve/reject can reject a self-approval attempt.
        refund.setRequestedBy(original.getDestinationAccount());
        // Approval gate (spec.md Section 8.1 rule 6, added 2026-08-05): every new refund
        // starts PENDING_APPROVAL and cannot advance past CREATED until approved.
        refund.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        refund.setPaymentMethod(original.getPaymentMethod() == null ? PaymentMethod.BANK_TRANSFER : original.getPaymentMethod());
        // FX snapshot (added 2026-08-06): reuse the original payment's frozen rate
        // rather than re-looking it up, so a refund always settles at the same rate
        // the original payment did - never recomputed against a "current" rate.
        refund.setSettlementCurrency("INR");
        BigDecimal fxRate = original.getFxRateToInr() == null ? BigDecimal.ONE : original.getFxRateToInr();
        refund.setFxRateToInr(fxRate);
        refund.setSettlementAmountInr(request.getAmount().multiply(fxRate).setScale(2, RoundingMode.HALF_UP));
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);

        // Solvency is intentionally NOT checked here (changed 2026-08-06): a refund
        // must always be allowed to be CREATED regardless of balance - insufficient
        // funds is a lifecycle/settlement-time concern (same reasoning as
        // createPayment() above). The authoritative, race-safe check remains the
        // atomic debitIfSufficient() re-check in processTransition() at actual
        // settlement time, which degrades the transition to FAILED/INSUFFICIENT_FUNDS.

        // Refund idempotency (spec.md Section 10.6, added 2026-08-05): mirrors the same
        // duplicate-key-catch-and-refetch short-circuit pattern used by createPayment(),
        // so a double-submitted refund request can't create two rows.
        try {
            paymentRepository.insert(refund);
        } catch (DuplicateKeyException e) {
            Payment existing = paymentRepository.findByIdempotencyKey(refund.getIdempotencyKey())
                    .orElseThrow(() -> e);
            throw new DuplicatePaymentException(existing);
        }

        PaymentStatusHistory initialHistory = new PaymentStatusHistory();
        initialHistory.setId(UUID.randomUUID());
        initialHistory.setPaymentId(refund.getId());
        initialHistory.setFromStatus(null);
        initialHistory.setToStatus(PaymentStatus.CREATED);
        initialHistory.setChangedAt(now);
        initialHistory.setTriggeredBy(SYSTEM_TRIGGER);
        initialHistory.setNote(request.getReason());
        paymentStatusHistoryRepository.insert(initialHistory);

        return PaymentMapper.toResponse(refund);
    }

    @Override
    @Transactional
    public PaymentResponse approveRefund(UUID refundId, ApproveRefundRequest request) {
        Payment refund = paymentRepository.findById(refundId)
                .orElseThrow(() -> new PaymentNotFoundException(refundId));

        // Segregation-of-duties (added 2026-08-06 hotfix): this check already existed
        // on rejectRefund() but was missing here, meaning the requester of a refund
        // could approve their own refund - defeating the whole point of the approval
        // gate. Applied consistently with rejectRefund() below.
        if (refund.getRequestedBy() != null && refund.getRequestedBy().equals(request.getApprovedBy())) {
            throw new SegregationOfDutiesException(
                    "approvedBy cannot be the same account that requested refund " + refundId);
        }

        // Conditional update (spec.md Section 10.8, added 2026-08-05): only applies when
        // type=REFUND and approval_status=PENDING_APPROVAL. A 0-row result means the
        // refund is not a REFUND row, or was already approved/rejected.
        int rowsAffected = paymentRepository.approveRefund(refundId, request.getApprovedBy(), Instant.now());
        if (rowsAffected == 0) {
            throw new RefundNotApprovedException(
                    "Refund " + refundId + " cannot be approved (must be type=REFUND with approvalStatus=PENDING_APPROVAL, was type="
                            + refund.getType() + ", approvalStatus=" + refund.getApprovalStatus() + ")");
        }

        Payment updated = paymentRepository.findById(refundId)
                .orElseThrow(() -> new PaymentNotFoundException(refundId));
        return PaymentMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public PaymentResponse rejectRefund(UUID refundId, RejectRefundRequest request) {
        Payment refund = paymentRepository.findById(refundId)
                .orElseThrow(() -> new PaymentNotFoundException(refundId));

        if (refund.getRequestedBy() != null && refund.getRequestedBy().equals(request.getRejectedBy())) {
            throw new SegregationOfDutiesException(
                    "rejectedBy cannot be the same account that requested refund " + refundId);
        }

        // Conditional update (spec.md Section 10.9, added 2026-08-05): only applies when
        // type=REFUND and approval_status=PENDING_APPROVAL; also moves status straight to
        // FAILED with error_code=REFUND_REJECTED in the same atomic update.
        int rowsAffected = paymentRepository.rejectRefund(refundId, request.getReason());
        if (rowsAffected == 0) {
            throw new RefundNotApprovedException(
                    "Refund " + refundId + " cannot be rejected (must be type=REFUND with approvalStatus=PENDING_APPROVAL, was type="
                            + refund.getType() + ", approvalStatus=" + refund.getApprovalStatus() + ")");
        }

        Instant now = Instant.now();
        PaymentStatusHistory historyEntry = new PaymentStatusHistory();
        historyEntry.setId(UUID.randomUUID());
        historyEntry.setPaymentId(refundId);
        historyEntry.setFromStatus(PaymentStatus.CREATED);
        historyEntry.setToStatus(PaymentStatus.FAILED);
        historyEntry.setChangedAt(now);
        historyEntry.setTriggeredBy(request.getRejectedBy());
        historyEntry.setNote("REFUND_REJECTED: " + request.getReason());
        paymentStatusHistoryRepository.insert(historyEntry);

        Payment updated = paymentRepository.findById(refundId)
                .orElseThrow(() -> new PaymentNotFoundException(refundId));
        return PaymentMapper.toResponse(updated);
    }

    @Override
    public Map<String, Object> searchPayments(Map<String, Object> filters, int page, int size) {
        Map<String, Object> validatedFilters = new LinkedHashMap<>();

        Object status = filters.get("status");
        if (status != null) {
            validatedFilters.put("status", parseEnum(PaymentStatus.class, status.toString(), "status").name());
        }

        Object type = filters.get("type");
        if (type != null) {
            validatedFilters.put("type", parseEnum(PaymentType.class, type.toString(), "type").name());
        }

        Object paymentMethod = filters.get("paymentMethod");
        if (paymentMethod != null) {
            validatedFilters.put("paymentMethod", parseEnum(PaymentMethod.class, paymentMethod.toString(), "paymentMethod").name());
        }

        Object approvalStatus = filters.get("approvalStatus");
        if (approvalStatus != null) {
            validatedFilters.put("approvalStatus", parseEnum(ApprovalStatus.class, approvalStatus.toString(), "approvalStatus").name());
        }

        if (filters.get("sourceAccount") != null) {
            validatedFilters.put("sourceAccount", filters.get("sourceAccount"));
        }
        if (filters.get("destinationAccount") != null) {
            validatedFilters.put("destinationAccount", filters.get("destinationAccount"));
        }
        if (filters.get("fromDate") != null) {
            validatedFilters.put("fromDate", filters.get("fromDate"));
        }
        if (filters.get("toDate") != null) {
            validatedFilters.put("toDate", filters.get("toDate"));
        }

        List<PaymentResponse> content = paymentRepository.search(validatedFilters, page, size).stream()
                .map(PaymentMapper::toResponse)
                .toList();
        long totalElements = paymentRepository.countSearch(validatedFilters);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("page", page);
        result.put("size", size);
        result.put("totalElements", totalElements);
        return result;
    }

    // Query-param enums are validated here (not Bean Validation) since they're plain strings on a GET; invalid values
    // are surfaced as IllegalArgumentException pending M3's GlobalExceptionHandler VALIDATION_ERROR mapping (Section 10.7).
    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String rawValue, String paramName) {
        try {
            return Enum.valueOf(enumType, rawValue.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid " + paramName + " value: " + rawValue);
        }
    }
}