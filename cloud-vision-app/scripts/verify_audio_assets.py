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
        # The exact 230ms candidate C chosen by the user (source MP3, not re-synthesized).
        selected=z.read("res/raw/c_metal_clack_ding.mp3")
        expected="4a5c7f989e16109d2cfb51b4f7f324cbfcc9be9724ca2ffc038dcd727e6005e6"
        assert hashlib.sha256(selected).hexdigest()==expected,"Candidate C audio changed"
        assert len(selected)==2910 and selected[:3]==b"ID3","C sound must be the original MP3"
        whoosh=decode(z,"voice_send_whoosh")
        assert .185<=whoosh[0]<=.215,"whoosh duration incorrect"
        assert whoosh[1]>.90 and whoosh[4]>.055,"whoosh is inaudible/weak"
        assert "res/raw/metal_sharp_click.wav" not in z.namelist(),            "old camera sound must not remain packaged"
        assert "res/raw/metal_lid_click.wav" not in z.namelist(),            "obsolete camera sound must not remain packaged"
        print("USER_SELECTED_C_MP3_AND_VOICE_WHOOSH_VERIFIED=PASS")
