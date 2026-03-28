package com.gateway.soap;

import com.gateway.model.SoapTransactionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Translates between SOAP/XML (used by legacy core banking systems) and
 * the internal domain model. Uses DOM parsing so it handles namespaces,
 * entities, and malformed input correctly.
 */
@Component
@Slf4j
public class SoapAdapter {

    private final DocumentBuilderFactory dbFactory;

    public SoapAdapter() {
        dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
    }

    public SoapTransactionRequest parseRequest(String soapXml) {
        SoapTransactionRequest request = new SoapTransactionRequest();
        try {
            Document doc = dbFactory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(soapXml)));
            doc.getDocumentElement().normalize();

            request.setTransactionId(extract(doc, "TransactionID",
                    "TXN-" + UUID.randomUUID().toString().substring(0, 8)));
            request.setSourceAccount(extract(doc, "SourceAccount", ""));
            request.setDestinationAccount(extract(doc, "DestinationAccount", ""));
            request.setAmount(extract(doc, "Amount", "0"));
            request.setCurrency(extract(doc, "Currency", "KES"));
            request.setChannel(extract(doc, "Channel", "SOAP"));
            request.setTimestamp(extract(doc, "Timestamp", now()));
        } catch (Exception e) {
            log.error("Failed to parse SOAP request: {}", e.getMessage());
            throw new SoapParseException("Invalid SOAP XML: " + e.getMessage());
        }
        return request;
    }

    public String buildSuccessResponse(String transactionId, String message) {
        return envelope("""
                    <TransactionResponse xmlns="http://gateway.com/transaction">
                      <ResultCode>0</ResultCode>
                      <ResultDesc>%s</ResultDesc>
                      <TransactionID>%s</TransactionID>
                      <Timestamp>%s</Timestamp>
                    </TransactionResponse>
                """.formatted(esc(message), esc(transactionId), now()));
    }

    public String buildErrorResponse(String transactionId, String errorCode, String message) {
        return envelope("""
                    <soap:Fault>
                      <faultcode>%s</faultcode>
                      <faultstring>%s</faultstring>
                      <detail><TransactionID>%s</TransactionID></detail>
                    </soap:Fault>
                """.formatted(esc(errorCode), esc(message), esc(transactionId)));
    }

    public String toSoapRequest(String sourcePhone, String destPhone,
                                 String amount, String transactionId) {
        return envelope("""
                    <TransactionRequest xmlns="http://gateway.com/transaction">
                      <TransactionID>%s</TransactionID>
                      <SourceAccount>%s</SourceAccount>
                      <DestinationAccount>%s</DestinationAccount>
                      <Amount>%s</Amount>
                      <Currency>KES</Currency>
                      <Channel>API</Channel>
                      <Timestamp>%s</Timestamp>
                    </TransactionRequest>
                """.formatted(esc(transactionId), esc(sourcePhone),
                esc(destPhone), esc(amount), now()));
    }

    public String jsonResultToSoapResponse(String transactionId, String status,
                                            String amount, String reference) {
        String code = "COMPLETED".equals(status) ? "0" : "1";
        return envelope("""
                    <TransactionResponse xmlns="http://gateway.com/transaction">
                      <ResultCode>%s</ResultCode>
                      <ResultDesc>%s</ResultDesc>
                      <TransactionID>%s</TransactionID>
                      <Amount>%s</Amount>
                      <Reference>%s</Reference>
                      <Timestamp>%s</Timestamp>
                    </TransactionResponse>
                """.formatted(code, esc(status), esc(transactionId),
                esc(amount), esc(reference), now()));
    }

    public boolean isValidXml(String xml) {
        try {
            dbFactory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String envelope(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                %s  </soap:Body>
                </soap:Envelope>
                """.formatted(body);
    }

    private String extract(Document doc, String tag, String fallback) {
        NodeList nodes = doc.getElementsByTagName(tag);
        if (nodes.getLength() > 0 && nodes.item(0).getTextContent() != null) {
            return nodes.item(0).getTextContent().trim();
        }
        return fallback;
    }

    private String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static class SoapParseException extends RuntimeException {
        public SoapParseException(String msg) { super(msg); }
    }
}
