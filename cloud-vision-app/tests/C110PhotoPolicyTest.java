package com.vobich.bluestar.cloudprobe;
public final class C110PhotoPolicyTest {
 private static void must(boolean ok){if(!ok)throw new AssertionError("C110 photo protocol test failed");}
 public static void main(String[] args) {
  must("/DCIM/PHOTO/P1.JPG".equals(C110PhotoPolicy.path("P1.JPG","/DCIM/PHOTO/","192.168.1.254",false)));
  must(C110PhotoPolicy.path("http://evil.com/DCIM/x.jpg","/DCIM/","192.168.1.254",false)==null);
  must(C110PhotoPolicy.path("../../secret.jpg","/DCIM/","192.168.1.254",false)==null);
  must(C110PhotoPolicy.jpegs("<a href=\"one.jpg\">A</a><FPATH>/DCIM/PHOTO/two.JPG</FPATH>","/DCIM/PHOTO/","192.168.1.254").size()==2);
  must(Integer.valueOf(0).equals(C110PhotoPolicy.tagInt("<Status>0</Status>","Status")));
  must(Integer.valueOf(-13).equals(C110PhotoPolicy.tagInt("<Status>-13</Status>","Status")));
  must("命令不存在".equals(C110PhotoPolicy.statusName(-256)));
  must(C110PhotoPolicy.jpegHeaderTail(new byte[]{(byte)255,(byte)216,(byte)255},0xffd9));
  System.out.println("C110 photo protocol unit tests PASS");
 }
}
