import com.vobichngan.kanzi.RealtimeSpeechGuard;

/**
 * Pure-Java simulation of 3 minutes of camera OCR callbacks.
 * This tests speech scheduling state, not OCR recognition accuracy or
 * actual Android TTS voice timing. Test data is synthetic, no user images.
 */
public final class QuietSpeechStabilitySmokeTest {
    private static int checks=0;
    private static void check(boolean pass,String why){
        checks++; if(!pass)throw new AssertionError(why);
    }
    private static final String A="文具文创书屋今日营业欢迎光临儿童图书九十元";
    private static final String A_NOISE="文具文创书屋今日营业欢迎光临儿童图书九土元";
    private static final String A_PARTIAL="文具文创书屋今日营业欢迎光临";
    private static final String B="医院耳鼻喉科诊室请保持安静挂号在左边三零二室";
    private static final String C="从右往左阅读中医传统竖排文字甲乙丙丁戊己庚辛壬癸";
    public static void main(String[] args){
        RealtimeSpeechGuard guard=new RealtimeSpeechGuard();
        check(!guard.acceptStable("",1,false),"empty OCR never speaks");
        check(guard.acceptStable(A,1,false),"first confirmed text reads immediately");
        int replay=0;
        // 180-second stable target, an OCR every 0.5 sec, with jitter and
        // brightness-scene generation changes every couple of seconds.
        for(int frame=0;frame<360;frame++){
            String text=(frame%3)==0?A:(frame%3)==1?A_NOISE:A_PARTIAL;
            long scene=frame<120?1:2+frame/14;
            if(guard.acceptStable(text,scene,false))replay++;
        }
        check(replay==0,"no repeated speech on same visible target for 3 min");

        // While an entire long multi-chunk utterance is playing, new camera
        // OCR results must not interrupt, even if their page really changed.
        for(int i=0;i<100;i++)
            check(!guard.acceptStable(B,200+i,true),
                "busy TTS cannot be preempted at frame "+i);
        // After finishing, a distinct new target must be recognized TWICE
        // in a coherent changed scene before it starts.
        check(!guard.acceptStable(B,305,false),"first new OCR is provisional");
        check(guard.acceptStable(B,305,false),"second coherent new OCR starts speech");
        check(!guard.acceptStable(B,305,false),"the new page never repeats itself");
        check(!guard.acceptStable(A,305,false),
            "different words in same scene cannot restart speech");
        check(!guard.acceptStable(C,306,false),"first vertical paragraph provisional");
        check(guard.acceptStable(C,306,false),"second vertical paragraph accepted");
        check(!guard.acceptStable(C,306,true),"busy vertical paragraph preserved");

        // Alternating noisy readings may not count as two stable confirmations.
        guard.reset();
        check(guard.acceptStable(A,1,false),"new session starts immediately");
        for(int i=0;i<100;i++) {
            String v=(i&1)==0?B:C;
            check(!guard.acceptStable(v,2,false),
                "unreliable B/C flicker never starts a fresh utterance");
        }
        check(!guard.acceptStable("老字号",3,false),"one short street sign provisional");
        check(guard.acceptStable("老字号",3,false),"short sign needs 2 consistent reads");
        check(!guard.acceptStable("老字号",4,false),"same short sign never echoes");
        guard.reset();
        check(guard.acceptStable(A,5,false),"explicit manual restart can read A again");
        System.out.println("QUIET_SPEECH_STABILITY=PASS checks="+checks+
            " stable_same_scene_minutes=3 auto_interruptions=0 beeps=0");
    }
}
