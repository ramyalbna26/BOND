package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class NativeStringsTest {

    @Test
    public void recoversAsciiUtf16Base64AndHex() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write("secret_ascii_value\u0000".getBytes(StandardCharsets.US_ASCII));
        for (char c : "secret_utf16_text".toCharArray()) {
            bytes.write(c);
            bytes.write(0);
        }
        bytes.write(new byte[]{0, 0});
        bytes.write(Base64.getEncoder().encode("secret_base64_payload".getBytes(StandardCharsets.US_ASCII)));
        bytes.write((byte) 0);
        StringBuilder hex = new StringBuilder();
        for (byte b : "secret_hex_payload".getBytes(StandardCharsets.US_ASCII)) {
            hex.append(String.format("%02x", b));
        }
        bytes.write(hex.toString().getBytes(StandardCharsets.US_ASCII));
        bytes.write((byte) 0);
        File so = File.createTempFile("native", ".so");
        FileOutputStream out = new FileOutputStream(so);
        out.write(bytes.toByteArray());
        out.close();
        File outFile = File.createTempFile("strings", ".txt");
        NativeStrings.Result r = NativeStrings.scan(so, outFile, false);
        String text = new String(java.nio.file.Files.readAllBytes(outFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("[A] secret_ascii_value"));
        assertTrue(text.contains("[U16LE]"));
        assertTrue(text.contains("secret_utf16_text"));
        assertTrue(text.contains("[B64] secret_base64_payload"));
        assertTrue(text.contains("[HEX] secret_hex_payload"));
        assertTrue(r.total >= 4);
    }
}
