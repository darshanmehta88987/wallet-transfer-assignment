package com.kullu.wallet.service;

import com.kullu.wallet.entity.Wallet;
import com.kullu.wallet.exception.WalletNotFoundException;
import com.kullu.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class WalletService {

    private final WalletRepository wallets;

    public WalletService(final WalletRepository wallets) {
        this.wallets = wallets;
    }

    /**
     * Acquire a {@code FOR UPDATE} row lock on the wallet, returning the
     * managed entity.
     *
     * @throws WalletNotFoundException if no wallet exists for the given id
     */
    public Wallet lockAndGet(final String id) {
        return wallets.lockById(id)
            .orElseThrow(() -> new WalletNotFoundException(id));
    }

    /**
     * Lookup a wallet without locking. Primarily for read-only scenarios.
     */
    public Optional<Wallet> findById(final String id) {
        return wallets.findById(id);
    }

    public Wallet save(final Wallet wallet) {
        return wallets.save(wallet);
    }
}
