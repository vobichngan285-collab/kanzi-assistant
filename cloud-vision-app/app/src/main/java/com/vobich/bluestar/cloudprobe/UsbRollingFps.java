package com.vobich.bluestar.cloudprobe;

/** Last 10 seconds of real TextureView buffer-update samples (NOT LCD FPS). */
final class UsbRollingFps {
    static final class Stats {
        final int count,lastFps10,meanFps10,minFps10,maxFps10;
        Stats(int count,int last,int mean,int min,int max){
            this.count=count;lastFps10=last;meanFps10=mean;
            minFps10=min;maxFps10=max;
        }
    }
    private static final int CAPACITY=8;
    private final long[] times=new long[CAPACITY];
    private final int[] fps10=new int[CAPACITY];
    private int cursor,count;
    void reset(){cursor=0;count=0;}
    void add(long now,int previewFps10,long sampleElapsedMs,boolean opened){
        if(!opened||sampleElapsedMs<500)return; // skip first 0-FPS baseline
        times[cursor]=now;
        fps10[cursor]=Math.max(0,Math.min(10000,previewFps10));
        cursor=(cursor+1)%CAPACITY;
        if(count<CAPACITY)count++;
    }
    Stats stats(long now){
        int n=0,total=0,last=0,min=Integer.MAX_VALUE,max=0;
        long latest=-1;
        for(int i=0;i<count;i++){
            if(times[i]>now||now-times[i]>10000L)continue;
            int value=fps10[i];
            n++;total+=value;min=Math.min(min,value);max=Math.max(max,value);
            if(times[i]>=latest){latest=times[i];last=value;}
        }
        return n==0?new Stats(0,0,0,0,0):
            new Stats(n,last,(total+n/2)/n,min,max);
    }
}
