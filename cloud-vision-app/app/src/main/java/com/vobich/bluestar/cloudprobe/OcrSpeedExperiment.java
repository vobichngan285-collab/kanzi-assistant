package com.vobich.bluestar.cloudprobe;

/** Isolated OCR speed experiment. Original whole-result playback remains default. */
final class OcrSpeedExperiment {
    static final String PREF="cloud_ocr_speed_experiment_v1";
    static final String A="A";
    static final String B="B";
    static final String C="C";
    static final String DEFAULT=A;
    static final String[] IDS={A,B,C};
    static final String[] LABELS={
        "A · 原版：全部识别后朗读",
        "B · 流式接收：全部识别后朗读",
        "C · 流式逐行：完整一行就朗读"
    };
    private OcrSpeedExperiment(){}
    static int index(String id){
        for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;
        return -1;
    }
    static String normalize(String id){return index(id)>=0?id:DEFAULT;}
    static boolean streaming(String id){return B.equals(id)||C.equals(id);}
    static boolean earlySpeak(String id){return C.equals(id);}
    static String label(String id){int i=index(id);return LABELS[i<0?0:i];}
    static int diagnosticValue(String id){int i=index(id);return i<0?0:i;}
}
