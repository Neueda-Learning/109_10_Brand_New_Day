package com.bnd.payment_processing.paymentmethod.service;

import com.bnd.payment_processing.payment.model.PaymentMethodType;
import com.bnd.payment_processing.paymentmethod.model.PaymentMethod;
import com.bnd.payment_processing.paymentmethod.repository.PaymentMethodRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentMethodServiceImpl implements PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;

    public PaymentMethodServiceImpl(PaymentMethodRepository paymentMethodRepository) {
        this.paymentMethodRepository = paymentMethodRepository;
    }

    @Override
    public PaymentMethod createForPayment(
            UUID customerId,
            PaymentMethodType methodType,
            String maskedIdentifier,
            String tokenRef,
            String provider) {

        Instant now = Instant.now();
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId(UUID.randomUUID());
        paymentMethod.setCustomerId(customerId);
        paymentMethod.setMethodType(methodType);
        paymentMethod.setDisplayLabel(buildDisplayLabel(methodType, maskedIdentifier));
        paymentMethod.setMaskedIdentifier(maskedIdentifier);
        paymentMethod.setTokenRef(tokenRef);
        paymentMethod.setProvider(provider);
        paymentMethod.setCreatedAt(now);
        paymentMethod.setUpdatedAt(now);

        return paymentMethodRepository.insert(paymentMethod);
    }

    private String buildDisplayLabel(PaymentMethodType methodType, String maskedIdentifier) {
        String suffix = maskedIdentifier == null || maskedIdentifier.isBlank() ? "" : " ending " + lastFour(maskedIdentifier);
        return switch (methodType) {
            case CARD -> "Card" + suffix;
            case BANK_TRANSFER -> "Bank Transfer" + suffix;
        };
    }

    private String lastFour(String maskedIdentifier) {
        String digitsOnly = maskedIdentifier.replaceAll("\\D", "");
        return digitsOnly.length() <= 4 ? digitsOnly : digitsOnly.substring(digitsOnly.length() - 4);
    }
}
