#!/usr/bin/env python3
"""Verify both actual WAVs in the final APK: decode, distinct content, sharp onset."""
import hashlib
import io
import math
import struct
import sys
import wave
import zipfile

def decode(zf, name):
    contents=zf.read("res/raw/"+name+".wav")
    with wave.open(io.BytesIO(contents),"rb") as wf:
        assert wf.getnchannels()==1, (name,"channels")
        assert wf.getsampwidth()==2, (name,"encoding")
        assert wf.getframerate()==32000, (name,"samplerate")
        n=wf.getnframes()
        raw=wf.readframes(n)
    assert len(raw)==n*2
    samples=struct.unpack("<"+"h"*n,raw)
    seconds=n/32000
    peak=max(abs(x) for x in samples)/32768
    def rms(part):
        return math.sqrt(sum(v*v for v in part)/max(1,len(part)))/32768
    first=rms(samples[:480])
    end=rms(samples[-800:])
    total=rms(samples)
    print("AUDIO_DECODE_PASS",name,
          "duration_ms",int(seconds*1000),"peak",round(peak,3),
          "attack_15ms_rms",round(first,3),"tail_25ms_rms",round(end,3),
          "total_rms",round(total,3),
          "sha256",hashlib.sha256(contents).hexdigest())
    return seconds,peak,first,end,total,contents

if __name__=="__main__":
    with zipfile.ZipFile(sys.argv[1]) as z:
        click=decode(z,"metal_sharp_click")
        whoosh=decode(z,"voice_send_whoosh")
        assert .110<=click[0]<=.135,"click must be short and sharp"
        assert click[1]>.90 and click[2]>.12,"click is too weak"
        assert click[3]<.045,"click has too long/soft tail"
        assert .185<=whoosh[0]<=.215,"whoosh duration is incorrect"
        assert whoosh[1]>.90 and whoosh[4]>.055,"whoosh is inaudible/weak"
        assert click[5]!=whoosh[5],"send must not reuse camera sound"
        assert "res/raw/metal_lid_click.wav" not in z.namelist(),\
            "obsolete soft camera sound must be removed"
        print("TWO_DISTINCT_SHARP_AUDIO_ASSETS_VERIFIED=PASS")
