package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conservative, fully offline recognition of likely *upright traditional vertical*
 * Chinese reading order. No per-scan manual switching is required; normal
 * horizontal writing must fail closed to the original algorithm.
 *
 * Uses ONLY existing OCR quadrilaterals/text. Fails closed on unstable geometry
 * and leaves normal live OCR text untouched. No preview matrix, frame rotation,
 * model inference, language completion, insertion or character replacement.
 *
 * The upstream OCR pixel-crop repair is a separate narrowly gated SDK change.
 */
public final class VerticalLongColumnLive {
    private VerticalLongColumnLive() {}

    public static final class Result {
        public final boolean used;
        public final String text;
        public final String status;
        public final List<Integer> columnIds;
        public final int trailingGlyphId;
        public final int preservedBoxCount;

        private Result(boolean used, String text, String status, List<Integer> ids,
                       int glyphId, int n) {
            this.used=used;this.text=text;this.status=status;
            this.columnIds=Collections.unmodifiableList(new ArrayList<>(ids));
            this.trailingGlyphId=glyphId;this.preservedBoxCount=n;
        }
    }

    private static Result fallback(String reason, int n) {
        return new Result(false,"",reason,Collections.emptyList(),-1,n);
    }

    private static int countHan(String s) {
        int count=0;
        for(int i=0;i<s.length();) {
            int c=s.codePointAt(i);
            if(Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN) count++;
            i+=Character.charCount(c);
        }
        return count;
    }

    public static Result tryArrange(boolean explicitVerticalMode,
                                    List<ReadingOrder.Box> boxes,
                                    int bitmapWidth, int bitmapHeight) {
        if(!explicitVerticalMode) return fallback("自动竖排判断已关闭",boxes==null?0:boxes.size());
        if(boxes==null || boxes.size()<4 || bitmapWidth<240 || bitmapHeight<320)
            return fallback("画面/文字框不足",boxes==null?0:boxes.size());
        VerticalLongColumnShadow.Result order=VerticalLongColumnShadow.inspect(boxes);
        if(!order.proposed || !order.exactPermutation || order.columnIds.size()<4)
            return fallback("竖排长列几何校验未通过："+order.reason,boxes.size());

        // Long Korean/English horizontal page turned sideways may also have
        // tall quads. Never infer true writing direction from geometry alone.
        // Even in explicit mode, require many already-recognized Han glyphs.
        int allCodepoints=0,allHan=0;
        float shortest=Float.MAX_VALUE;
        for(int id:order.columnIds) {
            ReadingOrder.Box b=boxes.get(id);
            if(b==null || b.text==null) return fallback("存在空长列",boxes.size());
            int len=b.text.codePointCount(0,b.text.length());
            int han=countHan(b.text);
            if(len<12 || b.confidence<0.80f) return fallback("长列文本未达到保守门控",boxes.size());
            allCodepoints+=len;allHan+=han;
            shortest=Math.min(shortest,b.bottom()-b.top());
        }
        if(allCodepoints<75 || allHan<allCodepoints*.65f || shortest<240f)
            return fallback("并非可信的成段中文竖排长列",boxes.size());

        // A horizontal book rotated sideways may have identical tall bounding
        // rectangles and perfectly readable Han text. Requiring at least one
        // clearly *upright* OCR quad among the body columns protects that case.
        // On true vertical signs, some slanted columns have OpenCV's long
        // edge first while another gives a clean short, near-horizontal
        // first edge. Use these existing quadrilaterals, no extra inference.
        int uprightAnchors = 0;
        for (int id:order.columnIds) {
            ReadingOrder.Box b = boxes.get(id);
            float firstX=b.x[1]-b.x[0], firstY=b.y[1]-b.y[0];
            float secondX=b.x[2]-b.x[1], secondY=b.y[2]-b.y[1];
            float e1=(float)Math.hypot(firstX,firstY);
            float e2=(float)Math.hypot(secondX,secondY);
            if(e1<=e2*.45f && Math.abs(firstY)<=Math.abs(firstX)*.45f)
                uprightAnchors++;
        }
        if(uprightAnchors < 1)
            return fallback("可能是横排书页旋转拍摄：没有可靠的竖排朝向锚点",boxes.size());

        // A visibly tall *unrecovered* crop between neighboring OCR columns
        // must fail the whole proposal. Otherwise four good columns can hide
        // one missing paragraph and wrongly sound like complete real speech.
        float firstX=boxes.get(order.columnIds.get(0)).centerX();
        float lastColumnX=boxes.get(order.columnIds.get(order.columnIds.size()-1)).centerX();
        for(int id:order.remainingIds) {
            ReadingOrder.Box b=boxes.get(id);
            if(b==null || b.text==null) return fallback("残余框无效",boxes.size());
            float h=b.bottom()-b.top(),w=b.right()-b.left();
            if(h>=Math.max(180f,shortest*.65f) && w>0 && h>=w*4f &&
                b.centerX()>=lastColumnX-85f && b.centerX()<=firstX+85f &&
                (b.text.codePointCount(0,b.text.length())<12 || b.confidence<.80f)) {
                return fallback("存在仍未恢复的长竖列，禁止把不完整正文作为连续语音",boxes.size());
            }
        }

        // A separate one-glyph short continuation at top LEFT of the final
        // column can be the last printed character; preserve its provenance.
        // If not provable, leave it among the remaining raw OCR boxes.
        ReadingOrder.Box last=boxes.get(order.columnIds.get(order.columnIds.size()-1));
        int trailing=-1;
        float lastX=last.centerX();
        float lastHeight=last.bottom()-last.top();
        for(int id:order.remainingIds) {
            ReadingOrder.Box b=boxes.get(id);
            if(b==null || b.text==null || b.confidence<.92f ||
               b.text.codePointCount(0,b.text.length())!=1 ||
               countHan(b.text)!=1) continue;
            float dx=lastX-b.centerX();
            float by=b.centerY();
            if(dx>=Math.max(8f,(last.right()-last.left())*.12f) &&
               dx<=Math.max(46f,(last.right()-last.left())*.95f) &&
               by>=last.top()-.06f*lastHeight &&
               by<=last.top()+.14f*lastHeight) {
                if(trailing>=0) { // Ambiguous glyphs: do not merge either.
                    trailing=-1;
                    break;
                }
                trailing=id;
            }
        }

        StringBuilder main=new StringBuilder();
        for(int id:order.columnIds) main.append(boxes.get(id).text);
        if(trailing>=0) main.append(boxes.get(trailing).text);
        // Do not discard titles, captions or small detections. Keep their
        // original raw OCR order in a separate spoken segment.
        StringBuilder others=new StringBuilder();
        int preserved=order.columnIds.size()+(trailing>=0?1:0);
        for(int id:order.remainingIds) {
            if(id==trailing)continue;
            String item=boxes.get(id).text;
            if(item==null || item.isBlank())continue;
            if(others.length()>0)others.append('\n');
            others.append(item);
            preserved++;
        }
        // If an empty string, invalid source box or duplicate would be lost,
        // fail closed to the unchanged real-time sorting logic.
        if(preserved!=boxes.size() || main.length()==0)
            return fallback("原始OCR文字框无法一一追溯",boxes.size());
        String speech=main.toString();
        if(others.length()>0) speech+="\n\n"+others;
        return new Result(true,speech,
            "自动判定为竖排：原始OCR "+order.columnIds.size()+
            " 个长列从右往左；独立续字="+(trailing>=0?"#"+(trailing+1):"无")+
            "；全部"+preserved+"个OCR框不丢弃，不补字",
            order.columnIds,trailing,preserved);
    }
}
