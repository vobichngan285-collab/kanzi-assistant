package com.vobichngan.kanzi;

/**
 * Explicit CPU profile for the independent X200 Pro test app.
 * These are hypotheses, not measured optimizations. Results need same-phone A/B.
 * No public APK or release code uses this class.
 */
public final class OcrPerformanceProfile {
    public static final OcrPerformanceProfile BASELINE =
        new OcrPerformanceProfile("baseline", "对照：2线程/批1", 2, 1, 0);
    public static final OcrPerformanceProfile BALANCED =
        new OcrPerformanceProfile("balanced", "均衡：3线程/批2", 3, 2, 2);
    public static final OcrPerformanceProfile QUALITY =
        new OcrPerformanceProfile("quality", "高质量：4线程/批2", 4, 2, 4);
    public static final OcrPerformanceProfile THROUGHPUT =
        new OcrPerformanceProfile("throughput", "吞吐实验：4线程/批4", 4, 4, 4);

    public final String id;
    public final String label;
    public final int cpuThreads;
    public final int recBatchSize;
    public final int maxShadowRescuesPerDiagnostic;

    private OcrPerformanceProfile(String id, String label, int threads,
                                  int batch, int shadowBudget) {
        if (threads < 1 || threads > 8 || batch < 1 || batch > 4 ||
            shadowBudget < 0 || shadowBudget > 4) throw new IllegalArgumentException();
        this.id = id;
        this.label = label;
        this.cpuThreads = threads;
        this.recBatchSize = batch;
        this.maxShadowRescuesPerDiagnostic = shadowBudget;
    }

    public static OcrPerformanceProfile forId(String id) {
        if (BALANCED.id.equals(id)) return BALANCED;
        if (QUALITY.id.equals(id)) return QUALITY;
        if (THROUGHPUT.id.equals(id)) return THROUGHPUT;
        return BASELINE;
    }

    /** Default preserves prior phone behavior until the tester selects a profile. */
    public static String defaultId() { return BASELINE.id; }
}
