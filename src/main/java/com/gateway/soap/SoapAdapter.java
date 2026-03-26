package com.gateway.soap;

import com.gateway.model.SoapTransactionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Adapts between SOAP/XML format used by legacy banking systems
 * and the REST/JSON format used by modern microservices.
 * <p>
 * In production, this would parse actual SOAP XML envelopes.
 * Here we demonstrate the pattern with simplified XML handling.
 */
@Component
@Slf4j
public class SoapAdapter {

    /**
     * Parse a SOAP XML transaction request into our internal model.
     */
    public SoapTransactionRequest parseRequest(String soapXml) {
        SoapTransactionRequest request = new SoapTransactionRequest();

        request.setTransactionId(extractTag(soapXml, "TransactionID",
                "TXN-" + UUID.randomUUID().toString().substring(0, 8)));
        request.setSourceAccount(extractTag(soapXml, "SourceAccount", ""));
        request.setDestinationAccount(extractTag(soapXml, "DestinationAccount", ""));
        request.setAmount(extractTag(soapXml, "Amount", "0"));
        request.setCurrency(extractTag(soapXml, "Currency", "KES"));
        request.setChannel(extractTag(soapXml, "Channel", "SOAP"));
        request.setTimestamp(extractTag(soapXml, "Timestamp",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));

        return request;
    }

    /**
     * Convert a transaction result to SOAP XML response.
     */
    public String buildSuccessResponse(String transactionId, String message) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <TransactionResponse xmlns="http://gateway.com/transaction">
                      <ResultCode>0</ResultCode>
                      <ResultDesc>%s</ResultDesc>
                      <TransactionID>%s</TransactionID>
                      <Timestamp>%s</Timestamp>
                    </TransactionResponse>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(message, transactionId,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    public String buildErrorResponse(String transactionId, String errorCode, String message) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <soap:Fault>
                      <faultcode>%s</faultcode>
                      <faultstring>%s</faultstring>
                      <detail>
                        <TransactionID>%s</TransactionID>
                      </detail>
                    </soap:Fault>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(errorCode, message, transactionId);
    }

    /**
     * Convert a REST/JSON transfer request to SOAP XML for legacy systems.
     */
    public String toSoapRequest(String sourcePhone, String destPhone,
                                 String amount, String transactionId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <TransactionRequest xmlns="http://gateway.com/transaction">
                      <TransactionID>%s</TransactionID>
                      <SourceAccount>%s</SourceAccount>
                      <DestinationAccount>%s</DestinationAccount>
                      <Amount>%s</Amount>
                      <Currency>KES</Currency>
                      <Channel>API</Channel>
                      <Timestamp>%s</Timestamp>
                    </TransactionRequest>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(transactionId, sourcePhone, destPhone, amount,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private String extractTag(String xml, String tag, String defaultValue) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int start = xml.indexOf(open);
        int end = xml.indexOf(close);
        if (start >= 0 && end > start) {
            return xml.substring(start + open.length(), end).trim();
        }
        return defaultValue;
    }
}
