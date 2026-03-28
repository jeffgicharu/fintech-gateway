package com.gateway.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SoapIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("SOAP endpoint processes valid XML transaction")
    void soapTransaction_validXml_returns200() throws Exception {
        String soapXml = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <SourceAccount>+254700000001</SourceAccount>
                    <DestinationAccount>+254700000002</DestinationAccount>
                    <Amount>5000</Amount>
                    <Currency>KES</Currency>
                  </soap:Body>
                </soap:Envelope>
                """;

        mockMvc.perform(post("/soap/transaction")
                .contentType(MediaType.TEXT_XML)
                .content(soapXml))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML))
                .andExpect(xpath("//ResultCode").string("0"));
    }

    @Test
    @DisplayName("SOAP endpoint rejects missing source account")
    void soapTransaction_missingSource_returns400() throws Exception {
        String soapXml = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <DestinationAccount>+254700000002</DestinationAccount>
                    <Amount>5000</Amount>
                  </soap:Body>
                </soap:Envelope>
                """;

        mockMvc.perform(post("/soap/transaction")
                .contentType(MediaType.TEXT_XML)
                .content(soapXml))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SOAP endpoint rejects malformed XML")
    void soapTransaction_badXml_returnsError() throws Exception {
        mockMvc.perform(post("/soap/transaction")
                .contentType(MediaType.TEXT_XML)
                .content("<not valid xml"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("REST to SOAP conversion returns valid envelope")
    void restToSoap_returnsEnvelope() throws Exception {
        mockMvc.perform(post("/soap/convert/rest-to-soap")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourcePhone\":\"+254700000001\",\"destPhone\":\"+254700000002\",\"amount\":\"1000\",\"transactionId\":\"TXN-TEST\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML))
                .andExpect(xpath("//TransactionID").string("TXN-TEST"));
    }
}
