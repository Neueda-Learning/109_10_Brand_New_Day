package com.bnd.payment_processing.payment.dto;

import com.bnd.payment_processing.payment.model.Account;

import java.math.BigDecimal;

/**
 * Response body for the universal accounts endpoints (added 2026-08-06):
 * {@code GET /api/accounts?customerRef=} and {@code GET /api/accounts/{accountNumber}}.
 * Works for any customer/account - not hardcoded to any specific demo identity.
 */
public class AccountResponse {

    private String accountNumber;
    private String displayName;
    private String accountType;
    private String status;
    private String currency;
    private BigDecimal balance;

    public static AccountResponse fromAccount(Account account) {
        AccountResponse response = new AccountResponse();
        response.accountNumber = account.getAccountNumber();
        response.displayName = account.getDisplayName();
        response.accountType = account.getAccountType().name();
        response.status = account.getStatus().name();
        response.currency = account.getDefaultCurrency();
        response.balance = account.getBalance();
        return response;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}

