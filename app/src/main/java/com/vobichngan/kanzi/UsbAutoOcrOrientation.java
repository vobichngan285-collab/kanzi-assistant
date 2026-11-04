package com.vobichngan.kanzi;

import java.util.List;

/**
 * OCR-evidence-only automatic USB frame orientation. Never reads the USB
 * preview TextureView transform. Never generates or rewrites recognized text.
 *
 * The only optional extra inference is an occasional rotated ORIGINAL
 * bitmap when initial horizontal text crops are implausibly short.
 */
public final class UsbAutoOcrOrientation {
    private UsbAutoOcrOrientation(){}

    public static int normalizePreference(int value) {
        return value == -1 ? -1 : OcrInputOrientation.normalize(value);
    }

    public static final class Quality {
        public final int readableCharacters, plausibleVerticalColumns, brokenWideRows;
        // Landscape USB can contain readable but SIDEWAYS horizontal text:
        // 6+ short (3..11 chars) tall lines do not prove correct image direction.
        public final int sidewaysTallRows, uprightWideRows;
        public final double weightedCharacters;
        Quality(int chars,int columns,int broken,int sideways,int upright,double weight) {
            readableCharacters=chars;
            plausibleVerticalColumns=columns;
            brokenWideRows=broken;
            sidewaysTallRows=sideways;
            uprightWideRows=upright;
            weightedCharacters=weight;
        }
    }

    public static Quality quality(List<ReadingOrder.Box> boxes) {
        if(boxes==null) return new Quality(0,0,0,0,0,0);
        int chars=0,columns=0,broken=0,sideways=0,upright=0;
        double weight=0;
        for(ReadingOrder.Box b:boxes) {
            if(b==null||b.text==null||b.x.length!=4||b.y.length!=4)continue;
            float left=b.left(),right=b.right(),top=b.top(),bottom=b.bottom();
            float w=right-left,h=bottom-top;
            if(w<=0||h<=0||!Float.isFinite(w)||!Float.isFinite(h))continue;
            int n=b.text.codePointCount(0,b.text.length());
            float conf=Math.max(0f,Math.min(1f,b.confidence));
            if(n>0) {
                chars+=n;
                weight+=Math.min(n,120)*Math.pow(conf,1.7);
            }
            if(w>=Math.max(240f,h*5f) && n<=8)broken++;
            if(h>=Math.max(170f,w*3.3f) && n>=12 && conf>=.80f)
                columns++;
            if(h>=Math.max(120f,w*3f) && n>=3 && n<=11 && conf>=.75f)
                sideways++;
            if(w>=Math.max(95f,h*3f) && n>=5 && conf>=.75f)
                upright++;
        }
        return new Quality(chars,columns,broken,sideways,upright,weight);
    }

    /** An obvious baseline side-on OCR failure, not a detector-only guess. */
    public static boolean needsProbe(Quality q,int rawWidth,int rawHeight) {
        return q!=null && rawWidth>rawHeight*1.18f &&
            ((q.brokenWideRows>=3 && q.plausibleVerticalColumns<3) ||
             // Price lists can be transposed and STILL recognized correctly.
             // Compare actual rotated OCR once; never auto-rotate on shapes alone.
             (q.sidewaysTallRows>=6 && q.uprightWideRows<=2 &&
              q.plausibleVerticalColumns<3));
    }

    /** Requires genuinely readable OCR strings from the additional pixel inference. */
    public static boolean accept(Quality base,Quality trial) {
        if(base==null||trial==null)return false;
        // Source 0° contains complete but sideways short price-list lines.
        // A genuine 270° OCR trial should turn most into upright long rows,
        // with little or no loss of the source characters/recognition score.
        // This accepts *real OCR* evidence, not guesses about the image.
        if(base.sidewaysTallRows>=6 && base.uprightWideRows<=2 &&
           trial.uprightWideRows>=Math.max(5,base.uprightWideRows+4) &&
           trial.sidewaysTallRows<=Math.max(1,base.sidewaysTallRows/4) &&
           trial.brokenWideRows<=base.brokenWideRows &&
           trial.readableCharacters>=Math.max(45,base.readableCharacters*.78) &&
           trial.weightedCharacters>=Math.max(35,base.weightedCharacters*.78))
            return true;
        if(trial.plausibleVerticalColumns>=3 &&
           trial.readableCharacters>=Math.max(45,base.readableCharacters+22) &&
           trial.weightedCharacters>=base.weightedCharacters*1.6)
            return true;
        // A sideways horizontal label may also become fully readable after
        // rotating its original pixels, without forming tall columns.
        return base.brokenWideRows>=3 &&
            trial.readableCharacters>=Math.max(55,base.readableCharacters*2) &&
            trial.weightedCharacters>=Math.max(24,base.weightedCharacters*2.7) &&
            trial.brokenWideRows<base.brokenWideRows;
    }

    /** Require multiple bad subsequent frames before revisiting calibration. */
    public static boolean shouldRevisit(Quality q, int badFrames) {
        return q!=null && badFrames>=3 &&
            (q.brokenWideRows>=3 ||
             (q.sidewaysTallRows>=6 && q.uprightWideRows<=2)) &&
            q.plausibleVerticalColumns<3;
    }
}
