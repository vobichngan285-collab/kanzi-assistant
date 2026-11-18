# SJCAM Zone 6.7.3.16 C110 OEM video-only minimal experiment

Dedicated branch exp/c110-oem-minimal-20261119, original repository. Does not alter the existing Android app, RC25, photo JPEG, cloud OCR/TTS or USB/M02S.

Original APK classes5.dex static evidence:
- LYCamera.connection -> getCameraVersion -> cmd3012 (read-only info)
- LYCamera$3$1.onResponse -> getCameraInfoByRequest -> handlerConnection -> cmd3016 (read-only current mode)
- LYCamera.changeCameraModel(1) -> cmd3001/par1 then, via LYCamera$12 callback, cmd2015/par1.
- LYCamera.getVideoPlayUrl returns mVideoUrl for ordinary LY camera; mode1 sets rtsp://192.168.1.254:554/xxx.mp4. C110_PRO special case has different rules; do not assume it is an ordinary C110.
- VlcLyCameraPlayer.playByUrl uses LibVLC, a visible VLCVideoLayout and a 300 ms delayed playback setup.

One tap explicitly reads 3012/3016, changes mode1, starts 2015 and starts LibVLC. Original VideoHelper handles its own Surface; no handmade Surface gate or RTSP SETUP/PLAY. No automatic retries. Camera serial, response XML, photo, TTS or keys are never persisted or uploaded.

The previous 11 builds never showed a real C110 video frame. This program must only be marked successful after a phone test shows real continuous frames. Because current device CameraVersion, SDK routing and actual original-app traffic have not been captured, its choice of the mode1 RTSP path remains a test hypothesis, not a proven OEM runtime value. Do not keep iterating without new evidence.
