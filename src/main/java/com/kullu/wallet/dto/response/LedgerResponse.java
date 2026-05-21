package com.kullu.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kullu.wallet.entity.EntryType;
import com.kullu.wallet.entity.LedgerEntry;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LedgerResponse {

    private Long id;
    private String walletId;
    private UUID transferId;
    private EntryType type;
    private long amount;
    private Instant createdAt;

    public LedgerResponse(final LedgerEntry entry) {
        this.id = entry.getId();
        this.walletId = entry.getWalletId();
        this.transferId = entry.getTransferId();
        this.type = entry.getType();
        this.amount = entry.getAmount();
        this.createdAt = entry.getCreatedAt();
    }

}