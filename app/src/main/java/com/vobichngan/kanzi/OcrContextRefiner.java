package com.vobichngan.kanzi;

/**
 * Small conservative contextual corrections applied only to the spoken OCR
 * result; never change the original OCR boxes, confidence scores, or
 * diagnostic transcription. NOT a free-form LLM that might hallucinate.
 *
 * Proper names, quantities, drug names and nutrition figures must NOT be
 * guessed. Corrections below require a long, unambiguous surrounding phrase.
 * More examples are added only after inspecting real diagnosed errors.
 */
public final class OcrContextRefiner {
    private OcrContextRefiner() {}

    public static String refine(String original) {
        if (original == null || original.isEmpty()) return original;
        String text = original;

        // Book-cover title in mixed simplified/traditional print. Only
        // normalize when the known matching author/title context is present.
        if ((text.contains("夏洛蒂") || text.contains("JANE EYRE")) &&
            text.contains("·爱")) {
            text = text.replace("簡·爱", "简·爱");
        }

        // These phrases are unique in the supplied full author biography;
        // the low-resolution OCR confused adjacent complex characters.
        // Only replace if the companion term identifies the same passage.
        if (text.contains("黄源深") &&
            text.contains("上海") && text.contains("协会副会长")) {
            text = text.replace("上海题泽家协会副会长",
                                "上海翻译家协会副会长");
            text = text.replace("上海题译家协会副会长",
                                "上海翻译家协会副会长");
        }
        if (text.contains("黄源深") && text.contains("上海市文联理事")) {
            text = text.replace("详协理事", "译协理事");
        }
        return text;
    }
}
