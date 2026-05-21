package com.kullu.wallet.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    @Test
    void initialBalanceCannotBeNegative() {
        assertThatThrownBy(() -> new Wallet("wallet-a", -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debitReducesBalance() {
        Wallet w = new Wallet("wallet-a", 1_000);
        w.debit(400);
        assertThat(w.getBalance()).isEqualTo(600);
    }

    @Test
    void creditIncreasesBalance() {
        Wallet w = new Wallet("wallet-a", 1_000);
        w.credit(250);
        assertThat(w.getBalance()).isEqualTo(1_250);
    }

    @Test
    void debitRejectsOverdraft() {
        Wallet w = new Wallet("wallet-a", 100);
        assertThatThrownBy(() -> w.debit(101)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void amountsMustBePositive() {
        Wallet w = new Wallet("wallet-a", 100);
        assertThatThrownBy(() -> w.debit(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> w.credit(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
