package com.vobich.bluestar.openocrlab
import org.junit.Assert.*
import org.junit.Test

class DbProfileTest {
 @Test fun baselineUnchanged(){val p=DbProfile.profiles.first();assertEquals(.3f,p.mask,0f);assertEquals(.6f,p.score,0f);assertEquals(3,p.minShort);assertEquals(5,p.minExpanded);assertEquals(1.5,p.unclip,0.0)}
 @Test fun shortExperiment(){val p=DbProfile.profiles[1];assertEquals(2,p.minShort);assertEquals(4,p.minExpanded);assertEquals(.3f,p.mask,0f);assertEquals(.6f,p.score,0f);assertEquals(1.5,p.unclip,0.0)}
 @Test fun expansionIndependent(){assertEquals(DbProfile.profiles[0].copy(id="tight_box",unclip=1.2),DbProfile.profiles[2])}
 @Test fun maskIndependent(){assertEquals(DbProfile.profiles[0].copy(id="mask_low",mask=.25f),DbProfile.profiles[3]);assertEquals(DbProfile.profiles[0].copy(id="mask_high",mask=.35f),DbProfile.profiles[4])}
 @Test fun buckets(){assertEquals(listOf(0,1,2,3,4,5),listOf(0.0,1.0,2.0,3.0,4.0,5.0).map{DbProfile.shortBucket(it)})}
}
