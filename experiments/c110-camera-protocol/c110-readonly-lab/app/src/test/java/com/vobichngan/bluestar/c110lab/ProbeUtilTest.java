package com.vobichngan.bluestar.c110lab;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProbeUtilTest {
 @Test public void localPath(){assertEquals("/DCIM/PHOTO/P1.JPG",ProbeUtil.path("P1.JPG","/DCIM/PHOTO/","192.168.1.254",false));}
 @Test public void noExternal(){assertNull(ProbeUtil.path("http://evil.test/DCIM/x.jpg","/DCIM/","192.168.1.254",false));}
 @Test public void noTraversal(){assertNull(ProbeUtil.path("../../key.jpg","/DCIM/","192.168.1.254",false));}
 @Test public void list(){assertEquals(2,ProbeUtil.jpegs("<a href=\"one.jpg\">a</a><FPATH>/DCIM/PHOTO/two.JPG</FPATH>","/DCIM/PHOTO/","192.168.1.254").size());}
 @Test public void jpeg(){assertTrue(ProbeUtil.jpegHeaderTail(new byte[]{(byte)255,(byte)216,(byte)255},0xffd9));}
}
