package com.vobich.bluestar.cloudprobe;
public final class PhoneStillUploadPolicyTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    static void near(double actual,double expected,double error){
        check(Math.abs(actual-expected)<=error,actual+" not near "+expected);
    }
    public static void main(String[] args){
        check(PhoneStillUploadPolicy.normalize(-1)==0,"invalid negative");
        check(PhoneStillUploadPolicy.normalize(3)==0,"invalid high");
        check(PhoneStillUploadPolicy.maxPixels(0)==8000000L,"8mp");
        check(PhoneStillUploadPolicy.maxPixels(1)==3686400L,"1440p");
        check(PhoneStillUploadPolicy.maxPixels(2)==2073600L,"1080p");
        near(PhoneStillUploadPolicy.scaleFor(3712,2088,0),1,1e-10);
        near(3712*PhoneStillUploadPolicy.scaleFor(3712,2088,1),2560,1.1);
        near(2088*PhoneStillUploadPolicy.scaleFor(3712,2088,1),1440,1.1);
        near(3712*PhoneStillUploadPolicy.scaleFor(3712,2088,2),1920,1.1);
        near(2088*PhoneStillUploadPolicy.scaleFor(3712,2088,2),1080,1.1);
        near(PhoneStillUploadPolicy.scaleFor(1280,720,2),1,1e-10);
        check(PhoneStillUploadPolicy.scaleFor(3712,2088,2)<
            PhoneStillUploadPolicy.scaleFor(3712,2088,1),"downscale order");
        try {PhoneStillUploadPolicy.scaleFor(0,1080,1);throw new AssertionError("zero size");}
        catch(IllegalArgumentException expected){}
        System.out.println("PASS: native 8MP; selectable 8MP/1440P/1080P cloud upload");
    }
}
