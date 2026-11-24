package com.vobich.bluestar.cloudprobe;
public final class C110BatteryPolicyTest {
    private static String xml(String value){
        return "<Function><Cmd>3019</Cmd><Status>0</Status><Value>"+value+"</Value></Function>";
    }
    private static void ok(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        for(int raw=0;raw<=4;raw++){
            C110BatteryPolicy.Reading r=C110BatteryPolicy.parse(xml(""+raw));
            ok(r.available&&!r.charging&&r.bars==4-raw&&!r.label.contains("%"));
        }
        C110BatteryPolicy.Reading charging=C110BatteryPolicy.parse(xml("5"));
        ok(charging.available&&charging.charging&&charging.bars==-1);
        ok(C110BatteryPolicy.parse(xml("1").replace("</Function>","<BatteryPercent>87</BatteryPercent></Function>")).bars==3);
        for(String bad:new String[]{"6","87","87%","-1","NaN",""})
            ok(!C110BatteryPolicy.parse(xml(bad)).available);
        ok(!C110BatteryPolicy.parse(xml("0").replace("3019","3012")).available);
        ok(!C110BatteryPolicy.parse(xml("0").replace("<Status>0","<Status>-13")).available);
        ok(!C110BatteryPolicy.parse(xml("0").replace("</Value>","</Value><Value>5</Value>")).available);
        ok(!C110BatteryPolicy.parse("<Value>0</Value>").available);
        ok(!C110BatteryPolicy.parse(null).available);
        System.out.println("PASS SJCAM X LY 3019 six states, no percent, unknown and malformed replies");
    }
}
