package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.Card;

import java.util.Optional;
import java.util.UUID;

/** Persistence contract for the {@code cards} registry (added 2026-08-06). */
public interface CardRepository {

    Optional<Card> findById(UUID id);
}

