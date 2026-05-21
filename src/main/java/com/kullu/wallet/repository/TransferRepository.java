package com.kullu.wallet.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kullu.wallet.entity.Transfer;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
}
