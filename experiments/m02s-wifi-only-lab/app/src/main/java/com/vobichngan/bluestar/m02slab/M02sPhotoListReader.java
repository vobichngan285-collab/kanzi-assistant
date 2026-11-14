package com.vobichngan.bluestar.m02slab;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OEM AP response: PhotoApBean.info[] -> files[].name/size/createtimestr/type.
 * NOT the legacy P2P XML format. Never log or persist the JSON or photo names.
 */
public final class M02sPhotoListReader {
    private M02sPhotoListReader() { }

    public static final class Result {
        public final int entriesObserved;
        public final int eligibleImages;
        public final List<M02sPhotoPolicy.Candidate> candidates;

        private Result(int observed, List<M02sPhotoPolicy.Candidate> photos) {
            this.entriesObserved = observed;
            this.eligibleImages = photos.size();
            this.candidates = M02sPhotoPolicy.immutableCopy(photos);
        }
    }

    public static Result parse(byte[] bytes) throws JSONException, CharacterCodingException {
        if (bytes == null || bytes.length == 0
                || bytes.length > M02sPhotoPolicy.MAX_LIST_BYTES) {
            throw new JSONException("AP list empty or over size limit");
        }
        String json = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        JSONObject root = new JSONObject(json);
        Object info = root.opt("info");
        if (info == null || info == JSONObject.NULL) {
            throw new JSONException("AP info field missing");
        }
        List<M02sPhotoPolicy.Candidate> list = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int total = 0;
        if (info instanceof JSONArray) {
            JSONArray items = (JSONArray) info;
            if (items.length() > M02sPhotoPolicy.MAX_CANDIDATES) {
                throw new JSONException("AP info group count over limit");
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject group = items.optJSONObject(i);
                if (group == null) continue;
                total = addFiles(group, names, list, total);
            }
        } else if (info instanceof JSONObject) {
            total = addFiles((JSONObject) info, names, list, total);
        } else {
            throw new JSONException("AP info field is not an object or array");
        }
        return new Result(total, list);
    }

    private static int addFiles(JSONObject group, Set<String> names,
                                List<M02sPhotoPolicy.Candidate> result,
                                int observed) throws JSONException {
        JSONArray files = group.optJSONArray("files");
        if (files == null) return observed;
        if (observed + files.length() > M02sPhotoPolicy.MAX_CANDIDATES) {
            throw new JSONException("AP file entry count over limit");
        }
        for (int i = 0; i < files.length(); i++) {
            JSONObject entry = files.optJSONObject(i);
            observed++;
            if (entry == null) continue;
            String name = entry.optString("name", "");
            if (!M02sPhotoPolicy.isImageName(name) || !names.add(name)) continue;
            long size = entry.optLong("size", -1L);
            if (size > M02sPhotoPolicy.MAX_IMAGE_BYTES) continue;
            String created = entry.optString("createtimestr", "");
            String type = entry.optString("type", "");
            result.add(new M02sPhotoPolicy.Candidate(name, size, created, type));
        }
        return observed;
    }
}
