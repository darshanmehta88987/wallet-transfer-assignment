package com.kullu.wallet.controller;

import com.kullu.wallet.dto.request.CreateTransferRequest;
import com.kullu.wallet.dto.response.TransferOutcome;
import com.kullu.wallet.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(final TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<String> create(@Valid @RequestBody final CreateTransferRequest request) {
        TransferOutcome outcome = transferService.createTransfer(request);
        int responseStatus = outcome.isReplayed() ? 200 : outcome.getStatus();
        return ResponseEntity.status(responseStatus).body(outcome.getResponseBody());
    }
}
