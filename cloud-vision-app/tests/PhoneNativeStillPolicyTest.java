package com.vobich.bluestar.cloudprobe;
public final class PhoneNativeStillPolicyTest {
    static void test(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    public static void main(String[] args){
        test(PhoneNativeStillPolicy.normalize(-1)==0,"invalid defaults 8MP");
        test(PhoneNativeStillPolicy.normalize(1)==1,"3MP allowed");
        test(PhoneNativeStillPolicy.target(0)==8000000L,"default original");
        test(PhoneNativeStillPolicy.target(1)==3686400L,"native target");
        test(PhoneNativeStillPolicy.eligible(3712,2088,0),"8MP accepted");
        test(!PhoneNativeStillPolicy.eligible(3712,2088,1),"8MP NOT native 3MP");
        test(PhoneNativeStillPolicy.eligible(2560,1440,1),"true 3.7MP");
        test(PhoneNativeStillPolicy.eligible(2048,1536,1),"true 3.1MP");
        test(!PhoneNativeStillPolicy.eligible(1920,1080,1),"2MP forbidden");
        test(!PhoneNativeStillPolicy.eligible(2300,1900,1),"4.37MP forbidden");
        test(PhoneNativeStillPolicy.score(3712,2088,1)==Double.POSITIVE_INFINITY,"no fake fallback");
        test(PhoneNativeStillPolicy.score(2560,1440,1)<
            PhoneNativeStillPolicy.score(2048,1536,1),"nearest to 3.7MP");
        test(PhoneNativeStillPolicy.score(3712,2088,0)<
            PhoneNativeStillPolicy.score(2560,1440,0),"old 8MP preference");
        System.out.println("PASS: Camera2 native 3–4MP selected only if truly supported");
    }
}
