package com.vobich.bluestar.cloudprobe;
import java.util.LinkedHashSet;
public final class CloudServerListTest {
  public static void main(String[] args) {
    LinkedHashSet<String> all=new LinkedHashSet<>();
    for(String id:OcrModelCatalog.IDS)all.add(id);
    for(String id:VisionModelCatalog.IDS)all.add(id);
    all.add("qwen3-asr-flash");
    if(all.size()!=5)throw new AssertionError("Expected 5, got "+all);
    for(String id:new String[]{"qwen-vl-ocr","qwen3.7-flash","qwen3.8-flash",
      "qwen3.8-max","qwen3-asr-flash"})
      if(!all.contains(id))throw new AssertionError("Missing "+id);
    if(!all.iterator().next().equals("qwen-vl-ocr"))
      throw new AssertionError("Catalog order changed");
    System.out.println("BUILTIN_SERVER_DEDUP=PASS");
  }
}
