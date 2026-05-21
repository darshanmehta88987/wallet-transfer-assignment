package com.kullu.wallet.support;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import com.kullu.wallet.entity.Wallet;
import com.kullu.wallet.repository.WalletRepository;

@SpringBootTest
@ContextConfiguration(initializers = PostgresContainerInitializer.class)
public abstract class AbstractPostgresIntegrationTest {

    private static final AtomicInteger WALLET_SEQUENCE = new AtomicInteger();

    protected static String seedWallet(WalletRepository wallets, long balance) {
        Wallet w = new Wallet("wallet-" + WALLET_SEQUENCE.incrementAndGet(), balance);
        wallets.saveAndFlush(w);
        return w.getId();
    }
}
