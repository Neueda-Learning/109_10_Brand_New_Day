package com.bnd.payment_processing.common.exception;

/** Thrown when a CARD payment references a cardId that doesn't exist in the registry. */
public class CardNotFoundException extends RuntimeException {
    public CardNotFoundException(String cardId) {
        super("Card " + cardId + " was not found");
    }
}

