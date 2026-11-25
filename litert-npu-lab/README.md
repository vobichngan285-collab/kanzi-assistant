# Blue Star OCR Stream Lab v0.1.9
Independent CPU6 LiteRT 0.18.1 experiment. Existing model SHA and raw-image OCR prompt/sampling are unchanged.
- Import the same previously downloaded 1698202736-byte LiteRT model, no GGUF. Tap prewarm after import; on later launches with verified model, prewarm automatically using a built-in sample, without speech.
- Select a real image and tap original OCR with speech. Reuse the isolated engine while the activity lives; release explicitly or on activity destruction. No claim of permanent background survival.
- Stream visible text to Android system TTS, require an installed offline Chinese voice; no network permission or cloud voice integration. New OCR or stop cancels prior speech. Logs record first request and engine onStart (not acoustic playback) relative to job and button timestamps.
- Compare cold without warmup (release, then OCR) against prewarm plus repeated new images. Use optional reference text for CER. No accuracy improvement is claimed.
- Independent package, old experimental APKs and production app unaffected. Import the existing original model file again; no need to download if retained.
