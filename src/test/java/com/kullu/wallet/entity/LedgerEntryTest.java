package com.kullu.wallet.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerEntryTest {

    @Test
    void signedAmountIsPositiveForCredit() {
        LedgerEntry e = new LedgerEntry("wallet-a", UUID.randomUUID(), EntryType.CREDIT, 100);
        assertThat(e.signedAmount()).isEqualTo(100);
    }

    @Test
    void signedAmountIsNegativeForDebit() {
        LedgerEntry e = new LedgerEntry("wallet-a", UUID.randomUUID(), EntryType.DEBIT, 100);
        assertThat(e.signedAmount()).isEqualTo(-100);
    }

    @Test
    void rejectsNullsAndNonPositiveAmount() {
        String w = "wallet-a";
        UUID t = UUID.randomUUID();
        assertThatThrownBy(() -> new LedgerEntry(null, t, EntryType.DEBIT, 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LedgerEntry(w, null, EntryType.DEBIT, 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LedgerEntry(w, t, null, 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LedgerEntry(w, t, EntryType.DEBIT, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
