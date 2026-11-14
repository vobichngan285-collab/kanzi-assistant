package com.vobichngan.bluestar.m02slab;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Android adaptation of E09's MIT-licensed BleProbe.swift AA15 PHOTO_START,
 * PHOTO_TRANS and PHOTO_END reassembly protocol.
 *
 * Upstream: https://github.com/sctg-development/ai-smart-glasses-e09-reverse
 * Copyright (c) 2026 Ronan Le Meillat - SCTG Development (MIT).
 *
 * E09's reference assembler does NOT demand that the transferred byte stream
 * end exactly at the JPEG EOI marker. Its real sample hardware sometimes
 * delivers 500 more bytes than PHOTO_START announces. This class never assumes
 * that M02S has the same fixed delta, and never invents missing JPEG bytes.
 *
 * The assembled photo is memory-only; no packet bytes or files are logged.
 */
public final class M02sE09PhotoAssembler {
    public enum Step { IGNORED, START, CHUNK, COMPLETE, ERROR }
    private static final int MAX_IMAGE = 2 * 1024 * 1024;

    private byte[] image;
    private BitSet received;
    private int highest;
    private int declared;
    private int packetCount;
    private int firstMissingOffset;
    private int jpegEndExclusive;
    private int trailingBytes;
    private boolean jpegStart;
    private boolean completed;
    private String lastError;

    public M02sE09PhotoAssembler() { reset(); }

    public void reset() {
        image = null;
        received = null;
        highest = 0;
        declared = -1;
        packetCount = 0;
        firstMissingOffset = -1;
        jpegEndExclusive = -1;
        trailingBytes = -1;
        jpegStart = false;
        completed = false;
        lastError = "";
    }

    public int declaredLength() { return declared; }
    public int receivedLength() { return highest; }
    public int packets() { return packetCount; }
    public String lastError() { return lastError; }
    public boolean isComplete() { return completed; }
    public boolean hasJpegStart() { return jpegStart; }
    public boolean hasJpegEnd() { return jpegEndExclusive >= 4; }
    public int jpegLength() { return jpegEndExclusive; }
    public int trailingBytes() { return trailingBytes; }
    public int firstMissingOffset() { return firstMissingOffset; }

    /** Safe diagnostic metadata only; contains no image bytes or photo names. */
    public String safeMetrics() {
        return "PHOTO_START声明=" + declared
                + "字节；AA15实际收齐区间=" + highest
                + "字节；差值=" + (declared > 0 ? highest - declared : 0)
                + "；SOI=" + jpegStart
                + "；EOI已找到=" + hasJpegEnd()
                + "；EOI末尾位置=" + jpegEndExclusive
                + "；尾随字节=" + trailingBytes
                + "；首缺失偏移=" + firstMissingOffset
                + "；分片包数=" + packetCount;
    }

    public Step receive(byte[] packet) {
        if (packet == null || packet.length < 8
                || (packet[0] & 255) != 0x52 || (packet[1] & 255) != 0x58
                || (packet[packet.length - 2] & 255) != 0x58
                || (packet[packet.length - 1] & 255) != 0x52) {
            return Step.IGNORED;
        }
        int command = packet[4] & 255;
        int payloadAt = 5;
        int payloadEnd = packet.length - 3; // crc, RX footer
        if (payloadEnd < payloadAt) return Step.IGNORED;

        if (command == 0x97) {
            reset();
            if (payloadEnd - payloadAt < 4) return fail("PHOTO_START长度不足");
            declared = ((packet[payloadAt + 2] & 255) << 8)
                    | (packet[payloadAt + 3] & 255);
            if (declared <= 0 || declared > MAX_IMAGE)
                return fail("PHOTO_START图片长度无效");
            // Grow only when actual offset chunks demand more RAM. The
            // reference hardware sometimes exceeds declared length.
            image = new byte[Math.min(MAX_IMAGE, Math.max(4096, declared + 1024))];
            received = new BitSet();
            packetCount = 1;
            return Step.START;
        }

        if (command == 0x98) {
            if (image == null || completed || payloadEnd - payloadAt <= 4)
                return Step.IGNORED;
            long offset = ((long) (packet[payloadAt] & 255) << 24)
                    | ((long) (packet[payloadAt + 1] & 255) << 16)
                    | ((long) (packet[payloadAt + 2] & 255) << 8)
                    | (packet[payloadAt + 3] & 255);
            int count = payloadEnd - payloadAt - 4;
            if (offset > MAX_IMAGE || offset + count > MAX_IMAGE)
                return fail("PHOTO_TRANS分片偏移超出2MB上限");
            int start = (int) offset;
            int end = start + count;
            if (end > image.length)
                image = Arrays.copyOf(image,
                        (int) Math.min(MAX_IMAGE, Math.max((long)end, (long)image.length * 2)));
            // E09 places an absolute big-endian byte offset in each frame.
            // Duplicate or out-of-order chunks are deliberately idempotent.
            System.arraycopy(packet, payloadAt + 4, image, start, count);
            received.set(start, end);
            highest = Math.max(highest, end);
            packetCount++;
            return Step.CHUNK;
        }

        if (command == 0x99) {
            if (image == null || completed) return Step.IGNORED;
            packetCount++;
            int firstGap = received.nextClearBit(0);
            if (highest < 4 || firstGap < highest) {
                firstMissingOffset = firstGap;
                return fail("PHOTO_END到达时照片分片不连续");
            }
            firstMissingOffset = -1;
            jpegStart = (image[0] & 255) == 0xff && (image[1] & 255) == 0xd8;
            if (!jpegStart) return fail("JPEG SOI起始标记缺失");

            // Do not demand FF D9 at the very last two transported bytes.
            // An EOI followed by firmware-added padding/metadata is valid for
            // decode once the contiguous JPEG portion is identified.
            for (int i = highest - 2; i >= 2; --i) {
                if ((image[i] & 255) == 0xff && (image[i + 1] & 255) == 0xd9) {
                    jpegEndExclusive = i + 2;
                    break;
                }
            }
            trailingBytes = jpegEndExclusive < 0 ? -1
                    : highest - jpegEndExclusive;

            // EOI may be absent in a valid transport but an incomplete JPEG.
            // Let Android's real Bitmap decoder decide whether this candidate
            // is usable, while UI explicitly reports EOI absence (not a
            // verified complete image). Never report success from AA15 only.
            completed = true;
            return Step.COMPLETE;
        }
        return Step.IGNORED;
    }

    /** Decode candidate (trims only after the last observed JPEG EOI). */
    public byte[] result() {
        if (!completed || image == null || !jpegStart) return null;
        return Arrays.copyOf(image,
                jpegEndExclusive >= 4 ? jpegEndExclusive : highest);
    }

    /** Fallback only for Android decode if the EOI-trim candidate fails. */
    public byte[] rawResult() {
        if (!completed || image == null || !jpegStart) return null;
        return Arrays.copyOf(image, highest);
    }

    private Step fail(String description) {
        lastError = description;
        // Keep non-sensitive metrics, release potentially private image bytes.
        image = null;
        received = null;
        completed = false;
        return Step.ERROR;
    }
}
