# v1.0.69 — GLM-4.6V / FlashX: reuse the verified Qwen image OCR path

## Scope
User specifically requested the exact previously working Qwen capture → prompt → streaming lines → Microsoft/Guangrong TTS flow for the two new Zhipu visual-chat models, without rebuilding the camera, playback, scheduler, UI or model settings.

Base is the published v1.0.68 commit ec5ea4ae7039d48c9a188d9d135671b9c005b76d. Only two production Java files change:
- CloudProviderPolicy.java: strictly recognize official Zhipu Chat Completions endpoints with glm-4.6v, glm-4.6v-flashx (and compatible GLM-4.6V-Flash), excluding other hosts, Layout Parsing and future models.
- CloudVisionClient.java: these models use the **same existing Qwen Chat OCR path**: a JPEG, the task's same editable OCR prompt, the same messages/image_url, stream SSE selected in settings, same OcrLineChunks and existing local TTS callback. The only vendor-specific JSON is `"thinking":{"type":"disabled"}`, since GLM-4.6V supports disabling thinking; it must never be sent to Bailian or unrelated providers. GLM-OCR Layout Parsing remains separate and unchanged.

No camera capture, 1.5s tap interval, cloudBusy, request queue, timeout, layout, C110 OEM, USB, sounds, grtts, KeyStore, API credentials, original Bailian routing, or source TTS logic changed.

## Tests and evidence
- Pure Java CloudProviderPolicyTest includes exact accepted/rejected model and endpoint combinations.
- GitHub Actions compile full Android ARM64 with pinned UVC patches and original Launcher/assets.
- Strict diff permits only CloudProviderPolicy, CloudVisionClient, their pure Java test, app/build.gradle version metadata, this document and the dedicated CI workflow.
- A successful build does **not** prove the user's Zhipu credentials, actual cloud latency/accuracy, vendor 200/400 behavior, or physical audible output. Test on vivo: same scene under Qwen3.7 then GLM-4.6V / GLM-4.6V-FlashX, stream enabled, task '朗读文字', mode C '快速逐行', 5-10 consecutive taps, compare first-speech and recognition errors.

## API protocol
For these two models use Chat Completions at https://open.bigmodel.cn/api/paas/v4/chat/completions, not Layout Parsing. Configure same existing editable OCR prompt and SSE on, don't add new profile/protocol screens.
