package dev.signaldock.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HmacSignatureServiceTest {
    private final HmacSignatureService service = new HmacSignatureService();

    @Test
    void signsTimestampAndRawPayloadWithHmacSha256() {
        String signature = service.sign("secret", "1704067200", "{\"orderId\":\"ord_123\"}");

        assertThat(signature).isEqualTo("sha256=1c2af6bc17a239eaecf8088cd8b0b121be760cc38ca37311001922af0b182425");
    }
}

