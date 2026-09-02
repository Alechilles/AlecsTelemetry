package com.alechilles.beacon.api;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryDiagnosticAttachmentTest {

    @Test
    void binaryAttachmentDescribesDecodedBytes() {
        byte[] zip = new byte[]{0x50, 0x4b, 0x03, 0x04};

        TelemetryDiagnosticAttachment attachment = TelemetryDiagnosticAttachment.binary(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "tamework_debugdb_export",
                "tamework-persistence-example.zip",
                "application/zip",
                zip
        );

        assertEquals("base64", attachment.contentEncoding());
        assertEquals(zip.length, attachment.byteCount());
        assertEquals(
                "8dcc7e601606217f3b754766511182a916b17e9a26a94c9d887104eba92e9bb2",
                attachment.sha256()
        );
        assertArrayEquals(zip, Base64.getDecoder().decode(attachment.content()));
    }
}
