package com.vobichngan.bluestar.m02slab;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Android adaptation of the MIT-licensed E09 BleProbe.swift AA15 framing and
 * PHOTO_START/TRANS/END offset reassembly.
 *
 * Based on: https://github.com/sctg-development/ai-smart-glasses-e09-reverse
 * Copyright (c) 2026 Ronan Le Meillat - SCTG Development (MIT).
 *
 * No photo bytes are persisted or logged. This is an exploratory compatibility
 * probe for M02S, not evidence the M02S implements E09's AA15 photo channel.
 */
public final class M02sE09PhotoAssembler {
    public enum Step { IGNORED, START, CHUNK, COMPLETE, ERROR }
    private static final int MAX_IMAGE = 2 * 1024 * 1024;
    private byte[] image;
    private BitSet received;
    private int highest;
    private int declared;
    private int packets;
    private boolean completed;
    private String lastError = "";

    public void reset() {
        image = null;
        received = null;
        highest = 0;
        declared = -1;
        packets = 0;
        completed = false;
        lastError = "";
    }

    public M02sE09PhotoAssembler() { reset(); }

    public int declaredLength() { return declared; }
    public int receivedLength() { return highest; }
    public int packets() { return packets; }
    public String lastError() { return lastError; }
    public boolean isComplete() { return completed; }

    public Step receive(byte[] value) {
        if (value == null || value.length < 8
                || (value[0] & 255) != 0x52 || (value[1] & 255) != 0x58
                || (value[value.length - 2] & 255) != 0x58
                || (value[value.length - 1] & 255) != 0x52) {
            return Step.IGNORED;
        }
        int cmd = value[4] & 255;
        int first = 5;
        int last = value.length - 3; // CRC byte, then two-byte trailer
        if (last < first) return Step.IGNORED;
        if (cmd == 0x97) {
            reset();
            if (last - first < 4) return fail("PHOTO_START长度不足");
            declared = ((value[first + 2] & 255) << 8) | (value[first + 3] & 255);
            if (declared <= 0 || declared > MAX_IMAGE) return fail("照片长度异常");
            image = new byte[MAX_IMAGE];
            received = new BitSet(MAX_IMAGE);
            packets = 1;
            return Step.START;
        }
        if (cmd == 0x98) {
            if (image == null || completed || last - first <= 4) return Step.IGNORED;
            long rawOffset = ((long)(value[first] & 255) << 24)
                    | ((long)(value[first + 1] & 255) << 16)
                    | ((long)(value[first + 2] & 255) << 8)
                    | (value[first + 3] & 255);
            int chunk = last - first - 4;
            if (rawOffset > MAX_IMAGE || rawOffset + chunk > MAX_IMAGE)
                return fail("照片分片越界");
            int offset = (int) rawOffset;
            System.arraycopy(value, first + 4, image, offset, chunk);
            received.set(offset, offset + chunk);
            highest = Math.max(highest, offset + chunk);
            packets++;
            return Step.CHUNK;
        }
        if (cmd == 0x99) {
            if (image == null || completed) return Step.IGNORED;
            packets++;
            if (highest < 4 || received.nextClearBit(0) < highest) return fail("照片分片缺失");
            // E09 field tests report a fixed discrepancy (+500B) between
            // PHOTO_START's length and the fully transferred JPEG. Do not
            // require an equality check; validate actual JPEG envelope.
            if ((image[0] & 255) != 0xff || (image[1] & 255) != 0xd8
                    || (image[highest - 2] & 255) != 0xff
                    || (image[highest - 1] & 255) != 0xd9)
                return fail("JPEG文件头尾校验失败");
            completed = true;
            return Step.COMPLETE;
        }
        return Step.IGNORED;
    }

    public byte[] result() {
        if (!completed || image == null) return null;
        return Arrays.copyOf(image, highest);
    }

    private Step fail(String reason) {
        lastError = reason;
        image = null;
        received = null;
        completed = false;
        return Step.ERROR;
    }
}
