package com.gateway.model;

import lombok.Data;

/**
 * SOAP-style request model for legacy system integration.
 * Simulates how older banking/telco systems communicate.
 */
@Data
public class SoapTransactionRequest {
    private String transactionId;
    private String sourceAccount;
    private String destinationAccount;
    private String amount;
    private String currency;
    private String channel;
    private String timestamp;
}
