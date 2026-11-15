package com.vobichngan.bluestar.m02slab;

import java.util.HashSet;
import java.util.Set;

/**
 * Detect a NEW OEM original on the SAME verified Wi-Fi AP.
 * Never use current filename timestamp alone: require baseline membership
 * difference and a strictly increased original inventory.
 */
public final class M02sApPhotoProbePolicy {
    private M02sApPhotoProbePolicy() { }

    public enum Status { NO_CHANGE, NOT_VERIFIABLE, NEW_UNIQUE_ORIGINAL }

    public static final class Delta {
        public final Status status;
        public final M02sPhotoPolicy.Candidate newOriginal;
        public final int addedCount;
        private Delta(Status status, M02sPhotoPolicy.Candidate candidate, int additions) {
            this.status=status; this.newOriginal=candidate; this.addedCount=additions;
        }
    }

    public static Delta compare(M02sPhotoListReader.Result before,
                                M02sPhotoListReader.Result after) {
        if(before==null || after==null
                || before.entriesObserved<1 || after.entriesObserved<before.entriesObserved)
            return new Delta(Status.NOT_VERIFIABLE,null,0);
        Set<String> prior=new HashSet<>();
        for(M02sPhotoPolicy.Candidate p:before.candidates)
            if(!prior.add(p.remoteName))
                return new Delta(Status.NOT_VERIFIABLE,null,0);
        Set<String> current=new HashSet<>();
        M02sPhotoPolicy.Candidate newcomer=null;
        int added=0;
        for(M02sPhotoPolicy.Candidate p:after.candidates) {
            if(!current.add(p.remoteName))
                return new Delta(Status.NOT_VERIFIABLE,null,0);
            if(!prior.contains(p.remoteName)) { added++; newcomer=p; }
        }
        if(!current.containsAll(prior) || after.entriesObserved!=before.entriesObserved+added
                || after.eligibleImages!=before.eligibleImages+added)
            return new Delta(Status.NOT_VERIFIABLE,null,added);
        if(added==0) return new Delta(Status.NO_CHANGE,null,0);
        if(added!=1) return new Delta(Status.NOT_VERIFIABLE,null,added);
        return new Delta(Status.NEW_UNIQUE_ORIGINAL,newcomer,1);
    }
}
