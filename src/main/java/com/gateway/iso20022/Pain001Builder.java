package com.gateway.iso20022;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds ISO 20022 pain.001 (CustomerCreditTransferInitiation) messages.
 *
 * ISO 20022 is the global standard for financial messaging. M-Pesa Africa
 * uses it for cross-border payments and interoperability with banking systems.
 * This builder creates compliant XML messages that can be sent to SWIFT,
 * central banks, or partner financial institutions.
 */
@Component
@Slf4j
public class Pain001Builder {

    private static final String NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09";

    /**
     * Build a pain.001 CustomerCreditTransferInitiation message.
     */
    public String build(String messageId, String debtorName, String debtorAccount,
                        String creditorName, String creditorAccount,
                        String amount, String currency) {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().newDocument();

            Element root = doc.createElementNS(NAMESPACE, "Document");
            doc.appendChild(root);

            Element cstmrCdtTrfInitn = addElement(doc, root, "CstmrCdtTrfInitn");

            // Group Header
            Element grpHdr = addElement(doc, cstmrCdtTrfInitn, "GrpHdr");
            addTextElement(doc, grpHdr, "MsgId", messageId);
            addTextElement(doc, grpHdr, "CreDtTm",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            addTextElement(doc, grpHdr, "NbOfTxs", "1");

            Element initgPty = addElement(doc, grpHdr, "InitgPty");
            addTextElement(doc, initgPty, "Nm", debtorName);

            // Payment Information
            Element pmtInf = addElement(doc, cstmrCdtTrfInitn, "PmtInf");
            addTextElement(doc, pmtInf, "PmtInfId", messageId + "-PMT");
            addTextElement(doc, pmtInf, "PmtMtd", "TRF");

            Element reqdExctnDt = addElement(doc, pmtInf, "ReqdExctnDt");
            addTextElement(doc, reqdExctnDt, "Dt",
                    LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

            // Debtor
            Element dbtr = addElement(doc, pmtInf, "Dbtr");
            addTextElement(doc, dbtr, "Nm", debtorName);

            Element dbtrAcct = addElement(doc, pmtInf, "DbtrAcct");
            Element dbtrId = addElement(doc, dbtrAcct, "Id");
            addTextElement(doc, dbtrId, "IBAN", debtorAccount);

            // Credit Transfer
            Element cdtTrfTxInf = addElement(doc, pmtInf, "CdtTrfTxInf");

            Element pmtId = addElement(doc, cdtTrfTxInf, "PmtId");
            addTextElement(doc, pmtId, "EndToEndId", messageId);

            Element amt = addElement(doc, cdtTrfTxInf, "Amt");
            Element instdAmt = doc.createElement("InstdAmt");
            instdAmt.setAttribute("Ccy", currency);
            instdAmt.setTextContent(amount);
            amt.appendChild(instdAmt);

            // Creditor
            Element cdtr = addElement(doc, cdtTrfTxInf, "Cdtr");
            addTextElement(doc, cdtr, "Nm", creditorName);

            Element cdtrAcct = addElement(doc, cdtTrfTxInf, "CdtrAcct");
            Element cdtrAcctId = addElement(doc, cdtrAcct, "Id");
            addTextElement(doc, cdtrAcctId, "IBAN", creditorAccount);

            return toXmlString(doc);
        } catch (Exception e) {
            log.error("Failed to build pain.001 message: {}", e.getMessage());
            throw new RuntimeException("ISO 20022 message generation failed", e);
        }
    }

    private Element addElement(Document doc, Element parent, String name) {
        Element el = doc.createElement(name);
        parent.appendChild(el);
        return el;
    }

    private void addTextElement(Document doc, Element parent, String name, String text) {
        Element el = doc.createElement(name);
        el.setTextContent(text);
        parent.appendChild(el);
    }

    private String toXmlString(Document doc) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
