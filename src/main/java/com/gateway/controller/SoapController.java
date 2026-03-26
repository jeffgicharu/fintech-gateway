package com.gateway.controller;

import com.gateway.model.SoapTransactionRequest;
import com.gateway.soap.SoapAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * SOAP endpoint for legacy system integration.
 * Accepts SOAP/XML requests and translates them to internal REST calls.
 */
@RestController
@RequestMapping("/soap")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "SOAP", description = "Legacy SOAP/XML interface for banking system integration")
public class SoapController {

    private final SoapAdapter soapAdapter;

    @PostMapping(value = "/transaction",
            consumes = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_XML_VALUE},
            produces = MediaType.TEXT_XML_VALUE)
    @Operation(summary = "Process a SOAP transaction", description = "Accepts SOAP XML and returns SOAP XML response")
    public ResponseEntity<String> processTransaction(@RequestBody String soapXml) {
        log.info("SOAP request received");

        try {
            SoapTransactionRequest request = soapAdapter.parseRequest(soapXml);

            log.info("SOAP transaction: {} → {} amount={} currency={}",
                    request.getSourceAccount(), request.getDestinationAccount(),
                    request.getAmount(), request.getCurrency());

            // Validate
            if (request.getSourceAccount().isEmpty() || request.getDestinationAccount().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        soapAdapter.buildErrorResponse(request.getTransactionId(),
                                "INVALID_REQUEST", "Source and destination accounts are required"));
            }

            if (Double.parseDouble(request.getAmount()) <= 0) {
                return ResponseEntity.badRequest().body(
                        soapAdapter.buildErrorResponse(request.getTransactionId(),
                                "INVALID_AMOUNT", "Amount must be greater than zero"));
            }

            // In production, this would route to the wallet service
            String response = soapAdapter.buildSuccessResponse(
                    request.getTransactionId(),
                    "Transaction processed successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("SOAP processing error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(
                    soapAdapter.buildErrorResponse("UNKNOWN", "SERVER_ERROR", e.getMessage()));
        }
    }

    @PostMapping(value = "/convert/rest-to-soap",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_XML_VALUE)
    @Operation(summary = "Convert a REST transfer request to SOAP XML format")
    public ResponseEntity<String> convertToSoap(@RequestBody java.util.Map<String, String> request) {
        String xml = soapAdapter.toSoapRequest(
                request.getOrDefault("sourcePhone", ""),
                request.getOrDefault("destPhone", ""),
                request.getOrDefault("amount", "0"),
                request.getOrDefault("transactionId", "TXN-CONVERT"));
        return ResponseEntity.ok(xml);
    }
}
