package com.kullu.wallet.repository;

import com.kullu.wallet.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findAllByTransferId(UUID transferId);

    List<LedgerEntry> findAllByWalletIdOrderByCreatedAtDesc(String walletId);
}
