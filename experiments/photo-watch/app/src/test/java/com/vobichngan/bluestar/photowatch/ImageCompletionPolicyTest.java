package com.vobichngan.bluestar.photowatch;

import static org.junit.Assert.*;
import org.junit.Test;

public class ImageCompletionPolicyTest {
    @Test public void completeJpegNeedsFinalEndMarker() {
        assertTrue(ImageCompletionPolicy.hasReliableEndMarker("IMG.JPG"));
        assertTrue(ImageCompletionPolicy.hasValidEndMarker("a.jpg",
                new byte[]{1, 2, (byte) 0xff, (byte) 0xd9}));
        assertFalse(ImageCompletionPolicy.hasValidEndMarker("a.jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8}));
        assertFalse(ImageCompletionPolicy.hasValidEndMarker("a.jpg", new byte[]{1}));
    }

    @Test public void pngRequiresIendChunkAtTail() {
        byte[] end = new byte[]{0, 0, 0, 0, 'I', 'E', 'N', 'D',
                (byte)0xae, 0x42, 0x60, (byte)0x82};
        assertTrue(ImageCompletionPolicy.hasValidEndMarker("a.PNG", end));
        end[4] = 'X';
        assertFalse(ImageCompletionPolicy.hasValidEndMarker("a.png", end));
    }

    @Test public void nonJpegDecodingRemainsHeuristic() {
        assertFalse(ImageCompletionPolicy.hasReliableEndMarker("a.heic"));
        assertTrue(ImageCompletionPolicy.hasValidEndMarker("a.heic", new byte[0]));
        assertTrue(ImageCompletionPolicy.isSupported("portrait.heif"));
        assertFalse(ImageCompletionPolicy.isSupported("a.tmp"));
        assertFalse(ImageCompletionPolicy.hasValidEndMarker("a.tmp", new byte[0]));
    }

    @Test public void stabilityRequiresTwoEqualPositiveSamples() {
        assertTrue(ImageCompletionPolicy.stable(500, 500, 1000, 1000));
        assertFalse(ImageCompletionPolicy.stable(500, 600, 1000, 1000));
        assertFalse(ImageCompletionPolicy.stable(0, 0, 1000, 1000));
        assertFalse(ImageCompletionPolicy.stable(500, 500, 1000, 1001));
    }
}
