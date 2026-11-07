package com.vobich.bluestar.cloudprobe;
import java.util.Arrays;
public final class FollowupPromptTest {
    private static void ok(boolean c,String m){if(!c)throw new AssertionError(m);}
    public static void main(String[] unused){
        String txt=FollowupPrompt.build("餐厅菜单","鱼香肉丝 28元，米饭 3元",
            Arrays.asList("问：有什么？答：有菜","问：价格？答：28元"),
            "一份鱼香肉丝加米饭一共多少钱？");
        ok(txt.contains("鱼香肉丝 28元"),"retain exact source");
        ok(txt.contains("一共多少钱"),"retain current request");
        ok(txt.contains("不得编造"),"no image hallucination");
        ok(txt.contains("此前问答："),"use dialogue context");
        ok(txt.contains("没有重新查看照片的权限"),"image-free guard");
        String review=FollowupPrompt.buildForImage("店铺招牌","一家超市",
            Arrays.asList("问：哪里？答：前方"),"再仔细看旧照片左边");
        ok(review.contains("明确要求重新查看旧照片"),"explicit image review permitted");
        ok(!review.contains("没有重新查看照片的权限"),"no contradictory image-review context");
        String huge="示".repeat(9000);
        String bounded=FollowupPrompt.build("task",huge,Arrays.asList(huge,huge,huge,huge),huge);
        ok(bounded.length()<5900,"strict prompt cap");
        boolean failed=false;
        try{FollowupPrompt.build("task"," ",null,"问什么");}
        catch(IllegalArgumentException expected){failed=true;}
        ok(failed,"reject empty source");
        System.out.println("TEXT_ONLY_FOLLOWUP_PROMPT_TESTS=PASS");
    }
}
