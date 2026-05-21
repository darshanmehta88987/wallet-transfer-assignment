package com.kullu.wallet.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic SHA-256 over the business-meaningful fields of a transfer
 * request. Used to detect "same idempotency key, different payload" reuse.
 */
public final class RequestHasher {

    private RequestHasher() {
    }

    public static String hash(String fromWalletId, String toWalletId, long amount) {
        String canonical = fromWalletId + "|" + toWalletId + "|" + amount;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JDK; this can never happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
