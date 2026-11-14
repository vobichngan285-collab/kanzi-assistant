package com.vobichngan.bluestar.m02slab;

import android.net.Network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

/** Only read-only GET via the already verified dedicated AP Network. */
public final class M02sPhotoTransport {
    private M02sPhotoTransport() { }

    public static final class Response {
        public final int httpCode;
        public final byte[] body;
        public final long durationMs;

        private Response(int code, byte[] bytes, long millis) {
            this.httpCode = code;
            this.body = bytes;
            this.durationMs = millis;
        }
    }

    public static Response getList(Network network, String verifiedHost) throws IOException {
        String host = ProbePolicy.checkedLocalHost(verifiedHost);
        return boundedGet(Objects.requireNonNull(network, "verified AP Network required"),
                "http://" + host + "/app/getfilelist",
                M02sPhotoPolicy.MAX_LIST_BYTES);
    }

    public static Response getOriginalPhoto(Network network, String verifiedHost,
                                            M02sPhotoPolicy.Candidate photo) throws IOException {
        if (photo == null) throw new IOException("No image candidate selected");
        String url = M02sPhotoPolicy.safePhotoUrl(verifiedHost, photo.remoteName);
        if (url == null) throw new IOException("Unsafe/unsupported remote photo path");
        if (photo.declaredBytes > M02sPhotoPolicy.MAX_IMAGE_BYTES) {
            throw new IOException("Image declared size exceeds memory cap");
        }
        return boundedGet(Objects.requireNonNull(network, "verified AP Network required"),
                url, M02sPhotoPolicy.MAX_IMAGE_BYTES);
    }

    private static Response boundedGet(Network verifiedNetwork, String url, int max)
            throws IOException {
        HttpURLConnection connection = null;
        long begin = android.os.SystemClock.elapsedRealtime();
        try {
            connection = (HttpURLConnection) verifiedNetwork.openConnection(new URL(url));
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "*/*");
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return new Response(code, new byte[0],
                        android.os.SystemClock.elapsedRealtime() - begin);
            }
            int reportedLength = connection.getContentLength();
            if (reportedLength > max) throw new IOException("Response larger than cap");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream(
                         Math.min(Math.max(reportedLength, 0), 64 * 1024))) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (n == 0) continue;
                    if (output.size() > max - n)
                        throw new IOException("Response exceeded memory cap");
                    output.write(buffer, 0, n);
                }
                return new Response(code, output.toByteArray(),
                        android.os.SystemClock.elapsedRealtime() - begin);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
