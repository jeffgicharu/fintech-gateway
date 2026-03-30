package com.gateway.iso20022;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/iso20022")
@RequiredArgsConstructor
@Tag(name = "ISO 20022", description = "Generate and parse ISO 20022 financial messages")
public class Iso20022Controller {

    private final Pain001Builder pain001Builder;

    @PostMapping(value = "/pain001", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Generate a pain.001 CustomerCreditTransferInitiation message",
               description = "Creates an ISO 20022 compliant payment initiation XML for cross-border or interbank transfers")
    public ResponseEntity<String> generatePain001(@RequestBody Map<String, String> request) {
        String xml = pain001Builder.build(
                request.getOrDefault("messageId", "MSG-" + System.currentTimeMillis()),
                request.getOrDefault("debtorName", ""),
                request.getOrDefault("debtorAccount", ""),
                request.getOrDefault("creditorName", ""),
                request.getOrDefault("creditorAccount", ""),
                request.getOrDefault("amount", "0"),
                request.getOrDefault("currency", "KES")
        );
        return ResponseEntity.ok(xml);
    }
}
