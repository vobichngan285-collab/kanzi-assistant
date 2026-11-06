package com.vobich.bluestar.cloudprobe;

/**
 * Pure Java model of a single press-hold-release gesture.
 * Cancel never sends; a second tap after release cannot send a second time.
 */
final class VoicePressState {
    enum Decision { NONE, SEND, CANCEL }
    private boolean held,cancelling;
    void down(boolean microphoneAccepted){
        held=microphoneAccepted;cancelling=false;
    }
    boolean move(float fingerY,float cancelAtY){
        if(held&&fingerY<cancelAtY)cancelling=true;
        return cancelling;
    }
    Decision up(){
        if(!held)return Decision.NONE;
        Decision result=cancelling?Decision.CANCEL:Decision.SEND;
        held=false;cancelling=false;
        return result;
    }
    Decision interrupt(){
        if(!held)return Decision.NONE;
        held=false;cancelling=false;
        return Decision.CANCEL;
    }
    boolean isHeld(){return held;}
}
