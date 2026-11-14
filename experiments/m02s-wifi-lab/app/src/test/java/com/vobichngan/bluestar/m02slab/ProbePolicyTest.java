package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;

public class ProbePolicyTest {
    @Test public void acceptOnlyLocalIpv4() {
        assertEquals("192.168.49.207", ProbePolicy.checkedLocalHost(" 192.168.49.207 "));
        assertEquals("192.168.169.1", ProbePolicy.checkedLocalHost("192.168.169.1"));
        for (String host : new String[]{"", "example.com", "8.8.8.8",
                "10.0.0.1", "192.168.0.1", "192.168.49.999",
                "192.168.49.1:8080", "192.168.49.1/app/getfilelist"}) {
            try {
                ProbePolicy.checkedLocalHost(host);
                fail("accepted " + host);
            } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void fingerprintNeverContainsPhotoNames() {
        byte[] raw = "{\"files\":[\"private_photo_2026.jpg\"]}".getBytes(StandardCharsets.UTF_8);
        String sha = ProbePolicy.fingerprint(raw);
        assertEquals(12, sha.length());
        assertFalse(sha.contains("private_photo"));
        assertEquals(sha, ProbePolicy.fingerprint(raw));
        assertNotEquals(sha, ProbePolicy.fingerprint("different".getBytes(StandardCharsets.UTF_8)));
    }

    @Test public void httpStatusDoesNotClaimConnectedWithoutProof() {
        assertEquals("未连接/没有有效网络", ProbePolicy.stateLabel(false, 200, 20));
        assertTrue(ProbePolicy.stateLabel(true, 404, 500).contains("404"));
    }
}
