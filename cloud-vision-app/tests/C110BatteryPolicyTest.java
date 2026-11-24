package com.vobich.bluestar.cloudprobe;
public final class C110BatteryPolicyTest {
    private static String xml(String value){
        return "<?xml version=\"1.0\"?><Function><Cmd>3019</Cmd><Status>0</Status><Value>"+value+"</Value></Function>";
    }
    private static void ok(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        ok(C110BatteryPolicy.parse(xml("0")).label.contains("高"));
        ok(C110BatteryPolicy.parse(xml("1")).label.contains("中"));
        ok(C110BatteryPolicy.parse(xml("2")).label.contains("低"));
        ok(C110BatteryPolicy.parse(xml("3")).label.contains("极低"));
        ok(C110BatteryPolicy.parse(xml("4")).label.contains("耗尽"));
        ok(C110BatteryPolicy.parse(xml("5")).label.contains("充电中"));
        ok(C110BatteryPolicy.parse(xml("87%")).label.equals("电量 87%"));
        ok(!C110BatteryPolicy.parse(xml("87")).available);
        ok(!C110BatteryPolicy.parse(xml("87")).label.contains("%"));
        ok(!C110BatteryPolicy.parse(xml("101%")).available);
        ok(!C110BatteryPolicy.parse(xml("-1")).available);
        ok(!C110BatteryPolicy.parse(xml("0").replace("3019","3012")).available);
        ok(!C110BatteryPolicy.parse(xml("0").replace("<Status>0","<Status>-13")).available);
        ok(!C110BatteryPolicy.parse(xml("0").replace("</Value>","</Value><Value>5</Value>")).available);
        ok(!C110BatteryPolicy.parse("<Value>0</Value>").available);
        ok(!C110BatteryPolicy.parse(null).available);
        System.out.println("PASS battery enum, explicit percent, unknown and malformed replies");
    }
}
