package com.vobich.bluestar.cloudprobe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class OcrSpeedExperimentTest {
    private static void ok(boolean val,String message){if(!val)throw new AssertionError(message);}
    private static void same(List<String> actual,String... expected){
        ok(actual.equals(Arrays.asList(expected)),"expected="+Arrays.toString(expected)+" actual="+actual);
    }
    public static void main(String[] args){
        ok(OcrSpeedExperiment.IDS.length==3,"exactly three modes");
        ok(OcrSpeedExperiment.DEFAULT.equals(OcrSpeedExperiment.A),"A default");
        ok(!OcrSpeedExperiment.streaming("A"),"A synchronous");
        ok(OcrSpeedExperiment.streaming("B")&&!OcrSpeedExperiment.earlySpeak("B"),"B no early playback");
        ok(OcrSpeedExperiment.streaming("C")&&OcrSpeedExperiment.earlySpeak("C"),"C early playback");
        for(int i=0;i<3;i++){
            String x=OcrSpeedExperiment.IDS[i];
            ok(OcrSpeedExperiment.normalize(x).equals(x),"valid "+x);
            ok(OcrSpeedExperiment.index(x)==i,"index "+x);
            ok(OcrSpeedExperiment.diagnosticValue(x)==i,"numeric diagnostic "+x);
        }
        for(String invalid:new String[]{null,"","D","a","qwen-vl-ocr"})
            ok(OcrSpeedExperiment.normalize(invalid).equals("A"),"invalid -> A");
        OcrLineChunks chunks=new OcrLineChunks();
        List<String> got=new ArrayList<>();
        chunks.append("门诊",got::add);same(got);
        chunks.append("大厅\r",got::add);same(got,"门诊大厅");
        chunks.append("\n内科",got::add);same(got,"门诊大厅");
        chunks.append("二楼\n检验科",got::add);same(got,"门诊大厅","内科二楼");
        chunks.append("三楼",got::add);same(got,"门诊大厅","内科二楼");
        chunks.finish(got::add);same(got,"门诊大厅","内科二楼","检验科三楼");
        chunks.finish(got::add);same(got,"门诊大厅","内科二楼","检验科三楼");
        OcrLineChunks one=new OcrLineChunks();
        List<String> early=new ArrayList<>();
        one.append("没有换行的完整文字。",early::add);same(early);
        one.finish(early::add);same(early,"没有换行的完整文字。");
        OcrLineChunks blanks=new OcrLineChunks();
        List<String> only=new ArrayList<>();
        blanks.append("\n\r \n\n  第一行  \n第二",only::add);same(only,"第一行");
        blanks.append("行\r\n",only::add);same(only,"第一行","第二行");
        blanks.finish(only::add);same(only,"第一行","第二行");
        System.out.println("RC20_OCR_ABC_STREAM_LINE_ORDER_NO_DUPLICATION=PASS");
    }
}
