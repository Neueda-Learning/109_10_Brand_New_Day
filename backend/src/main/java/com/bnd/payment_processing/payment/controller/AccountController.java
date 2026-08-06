package com.bnd.payment_processing.payment.controller;

import com.bnd.payment_processing.common.exception.AccountNotFoundException;
import com.bnd.payment_processing.payment.dto.AccountResponse;
import com.bnd.payment_processing.payment.repository.AccountRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Universal accounts read API (added 2026-08-06) - works for any customer/account,
 * not hardcoded to any specific demo identity. Powers the customer-side "My
 * Accounts" balance display (frontend-user), but is a general-purpose endpoint.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<AccountResponse> listByCustomerRef(@RequestParam String customerRef) {
        return accountRepository.findByCustomerRef(customerRef).stream()
                .map(AccountResponse::fromAccount)
                .toList();
    }

    @GetMapping("/{accountNumber}")
    public AccountResponse getByAccountNumber(@PathVariable String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .map(AccountResponse::fromAccount)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }
}

