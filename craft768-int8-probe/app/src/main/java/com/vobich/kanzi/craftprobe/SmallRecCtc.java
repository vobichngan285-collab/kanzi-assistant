package com.vobich.kanzi.craftprobe;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure Java CTC recognition decoder for fixed PP-OCRv6 Small REC labels.
 * Zero-index is blank; remaining model class i maps to dictionary[i-1].
 * Repeated label requires a blank between emissions to appear twice.
 *
 * Separated from Android Bitmap/ONNX so CPU-only CI can check it byte-for-byte
 * against the reference Python argmax/collapse logic.
 */
final class SmallRecCtc {
    private SmallRecCtc() {}

    static final class Result {
        final String text;
        final float confidence;
        final int rawTimeSteps;
        Result(String text, float confidence, int rawTimeSteps) {
            this.text = text;
            this.confidence = confidence;
            this.rawTimeSteps = rawTimeSteps;
        }
    }

    static Result decode(FloatBuffer tensor, long[] shape, List<String> characters) {
        if (shape.length != 3 || shape[0] != 1 ||
            shape[1] < 1 || shape[1] > 4096 ||
            shape[2] < 2 || shape[2] > 30000) {
            throw new IllegalArgumentException("Expected Small REC CTC output [1,T,C]");
        }
        int steps = (int) shape[1], classes = (int) shape[2];
        if (characters == null || characters.isEmpty() || characters.size() + 1 < classes)
            throw new IllegalArgumentException("Inconsistent Small REC dictionary size");
        if (tensor.limit() < steps * classes)
            throw new IllegalArgumentException("Truncated Small REC output");
        StringBuilder output = new StringBuilder();
        float confidenceSum = 0f;
        int recognized = 0;
        int previous = -1;
        for (int t = 0; t < steps; ++t) {
            int best = 0;
            float score = Float.NEGATIVE_INFINITY;
            final int offset = t * classes;
            for (int c = 0; c < classes; ++c) {
                float value = tensor.get(offset + c);
                if (!Float.isFinite(value))
                    throw new IllegalArgumentException("Nonfinite Small REC score");
                if (value > score) {
                    score = value;
                    best = c;
                }
            }
            if (best != 0 && best != previous && best <= characters.size()) {
                output.append(characters.get(best - 1));
                confidenceSum += score;
                recognized++;
            }
            previous = best;
        }
        return new Result(output.toString(),
            recognized == 0 ? 0f : confidenceSum / recognized, steps);
    }

    /** Basic scene-reading approximation, not validated multi-column document layout. */
    static <T extends Positioned> List<T> topToBottom(List<T> items) {
        List<T> ordered = new ArrayList<>(items);
        // Sort with a TRANSITIVE comparator first. Comparing pairwise line-height
        // overlap directly in the comparator is non-transitive and can make
        // TimSort throw "Comparison method violates its general contract".
        ordered.sort((a,b) -> {
            int y=Float.compare((a.top()+a.bottom())/2f,
                                (b.top()+b.bottom())/2f);
            return y!=0?y:Float.compare(a.left(),b.left());
        });
        List<List<T>> rows=new ArrayList<>();
        float anchor=Float.NaN;
        float rowHeight=0f;
        for(T item:ordered) {
            float center=(item.top()+item.bottom())/2f;
            float height=Math.max(1f,item.bottom()-item.top());
            if(rows.isEmpty() ||
               Math.abs(center-anchor)>Math.max(8f,Math.min(rowHeight,height)*.45f)) {
                rows.add(new ArrayList<>());
                anchor=center;
                rowHeight=height;
            }
            rows.get(rows.size()-1).add(item);
        }
        List<T> result=new ArrayList<>(ordered.size());
        for(List<T> row:rows) {
            row.sort((a,b)->Float.compare(a.left(),b.left()));
            result.addAll(row);
        }
        return result;
    }

    interface Positioned {
        float left();
        float top();
        float bottom();
    }
}
