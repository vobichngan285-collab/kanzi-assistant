package com.vobich.bluestar.cloudprobe;

/**
 * HTTP 200 with an empty OCR message means "no readable text", not a transport failure.
 * Keep nonempty source text intact; callers decide how to notify the user.
 * Does not catch authentication, protocol or network errors.
 */
final class OcrResponseGate {
    static final class NoReadableTextException extends Exception {
        final String serverModel;
        final int inputTokens, outputTokens;
        NoReadableTextException() { this("",-1,-1); }
        NoReadableTextException(String model,int input,int output) {
            super("OCR returned no readable text");
            serverModel=model;inputTokens=input;outputTokens=output;
        }
    }

    private OcrResponseGate() {}

    static String requireReadable(String raw) throws NoReadableTextException {
        if (raw == null || raw.trim().isEmpty()) throw new NoReadableTextException();
        return raw.trim();
    }
}
