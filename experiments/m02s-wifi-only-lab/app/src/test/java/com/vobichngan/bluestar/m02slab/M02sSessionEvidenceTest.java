package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import org.junit.Test;

public final class M02sSessionEvidenceTest {
    @Test public void oemRawListCountIsNotFilteredPhotoCount() {
        assertEquals(M02sSessionEvidence.OemFinishBranch.PART_COMPLETED,
                M02sSessionEvidence.theoreticalOemBranch(6, 5));
        assertEquals(M02sSessionEvidence.OemFinishBranch.ALL_COMPLETED,
                M02sSessionEvidence.theoreticalOemBranch(5, 5));
        assertEquals(M02sSessionEvidence.OemFinishBranch.PART_COMPLETED,
                M02sSessionEvidence.theoreticalOemBranch(5, 1));
        assertEquals(M02sSessionEvidence.OemFinishBranch.ZERO_COMPLETED,
                M02sSessionEvidence.theoreticalOemBranch(5, 0));
        assertEquals(M02sSessionEvidence.OemFinishBranch.UNKNOWN_OR_INVALID,
                M02sSessionEvidence.theoreticalOemBranch(0, 0));
        assertEquals(M02sSessionEvidence.OemFinishBranch.UNKNOWN_OR_INVALID,
                M02sSessionEvidence.theoreticalOemBranch(5, 6));
    }

    @Test public void androidReleaseDoesNotProveFirmwareExitOrSecondRound() {
        M02sSessionEvidence m = new M02sSessionEvidence();
        m.onImportFlag(0);
        m.onImportFlag(1);
        m.onRawOemList(6, 5);
        m.onPhoneNetworkReleased(true);
        assertEquals(1, m.getVerifiedReleasedRounds());
        assertEquals(1, m.getLastImportFlag());
        assertFalse(m.sawImportInactiveAfterRelease());
        assertFalse(m.sawPhysicalShutterAfterRelease());
        assertFalse(m.hasObservedSecondSuccessfulRound());
        assertTrue(m.statusText().contains("镜端退出未确认"));
        assertEquals(6, m.getLastRawListEntries());
        assertEquals(5, m.getLastEligibleImages());
    }

    @Test public void importZeroAndNewShutterAreOnlySeparateObservations() {
        M02sSessionEvidence m = new M02sSessionEvidence();
        m.onImportFlag(0); // prior to release: not post-release evidence
        m.onPhoneNetworkReleased(true);
        assertFalse(m.sawImportInactiveAfterRelease());
        m.onImportFlag(0); // valid AFTER the release
        assertTrue(m.sawImportInactiveAfterRelease());
        m.onPhysicalShutterEnded();
        assertTrue(m.sawPhysicalShutterAfterRelease());
        assertFalse(m.hasObservedSecondSuccessfulRound());
    }

    @Test public void onlySecondVerifiedOriginalAdvancesSecondRoundEvidence() {
        M02sSessionEvidence m = new M02sSessionEvidence();
        m.onPhoneNetworkReleased(true);
        m.onPhysicalShutterEnded();
        m.onNextManualImportStarted();
        assertFalse(m.hasObservedSecondSuccessfulRound());
        m.onPhoneNetworkReleased(false); // second failed
        assertFalse(m.hasObservedSecondSuccessfulRound());
        m.onPhoneNetworkReleased(true);  // second full original verified
        assertTrue(m.hasObservedSecondSuccessfulRound());
        assertEquals(2, m.getVerifiedReleasedRounds());
        assertFalse(m.sawPhysicalShutterAfterRelease()); // new baseline
    }

    @Test public void invalidImportStateCannotPretendDeviceHasExited() {
        M02sSessionEvidence m = new M02sSessionEvidence();
        m.onPhoneNetworkReleased(true);
        m.onImportFlag(-1);
        m.onImportFlag(7);
        assertEquals(-1, m.getLastImportFlag());
        assertFalse(m.sawImportInactiveAfterRelease());
        assertTrue(m.statusText().contains("未获得"));
    }
}
