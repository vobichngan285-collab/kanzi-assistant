package com.vobich.bluestar.cloudprobe;

/** Verified OCR playback policy: fast complete-line streaming by default, full-result compatibility fallback. */
final class OcrSpeedExperiment {
    static final String PREF="cloud_ocr_speed_experiment_v1";
    static final String A="A";
    static final String B="B";
    static final String C="C";
    static final String DEFAULT=C;
    static final String[] IDS={A,B,C};
    static final String[] LABELS={
        "兼容全文 · 全部识别完成后朗读",
        "B · 流式接收：全部识别后朗读",
        "快速逐行（推荐） · 完整一行就朗读"
    };
    private OcrSpeedExperiment(){}
    static int index(String id){
        for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;
        return -1;
    }
    static String normalize(String id){return A.equals(id)||C.equals(id)?id:DEFAULT;}
    static boolean streaming(String id){return B.equals(id)||C.equals(id);}
    static boolean earlySpeak(String id){return C.equals(id);}
    static String label(String id){int i=index(id);return LABELS[i<0?0:i];}
    static int diagnosticValue(String id){int i=index(id);return i<0?0:i;}
}
