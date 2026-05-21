package com.kullu.wallet.service;

import com.kullu.wallet.dto.response.LedgerResponse;
import com.kullu.wallet.entity.LedgerEntry;
import com.kullu.wallet.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;

@Service
public class LedgerService {

    private final LedgerEntryRepository ledger;

    public LedgerService(final LedgerEntryRepository ledger) {
        this.ledger = ledger;
    }

    public LedgerResponse save(final LedgerEntry entry) {
        LedgerEntry saved = ledger.save(entry);
        return new LedgerResponse(saved);
    }
}
