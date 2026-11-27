package com.vobich.bluestar.cloudprobe;
import java.util.*;
public final class HybridOrderTest {
 public static void main(String[] args){
  List<String> t=Arrays.asList("肩颈调理","128元","68元","全身经络");
  if(!HybridOrder.restore("1,3\n4,2",t).equals("肩颈调理 68元\n全身经络 128元"))throw new AssertionError();
  for(String bad:new String[]{"1,3\n4","1,3\n4,2,2","1,3\n4,5","1,3\n4,0","1,3\n4,2\n说明","1,3\n4,02","[1,3]\n[4,2]","1,3\n4,2\n\n1","1,3\n4,9999999999999999"}) {
   try{HybridOrder.restore(bad,t);throw new AssertionError("accepted "+bad);}catch(IllegalArgumentException expected){}
  }
  System.out.println("PASS hybrid exact-once ID grouping and original amounts");
 }
}
