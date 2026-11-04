#!/usr/bin/env python3
"""Catch long-reading regressions without an Android device."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/"app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text()
reading=(root/"app/src/main/java/com/vobichngan/kanzi/ReadingOrder.java").read_text()
assert "APP_VERSION = \"0.9.19\"" in main
assert "if (recognizer == null || !recognizer.isReady()) return;" in main
assert "if (candidateCount < 2) return;" not in main
assert "needsConfirmation" not in main
assert "final String confirmed = recognizedText;" in main
assert "longReadingMode && speechPageActive" in main
assert "frameChangeGate.accept(data, width, height, now, samePageLocked)" in main
assert "lastProcess = 0L;" in main
assert "voice" not in reading.lower() or "Column" in reading
assert "overlap >= Math.max(typicalHeight * 0.35f, narrow * 0.37f)" in reading
assert "result.removeIf(List::isEmpty)" in reading
print("BOOK_LAYOUT_ONE_PASS=PASS")

focus=(root/"app/src/main/java/com/vobichngan/kanzi/ReadingFocus.java").read_text()
ocr=(root/"app/src/main/java/com/vobichngan/kanzi/PaddleOcrEngine.kt").read_text()
assert 'PREF_READING_FOCUS' in main
assert 'pageFocusGroup' in main and 'readingFocusMode = requestedFocus' in main
assert 'getFocusDescription()' in main and 'getFullPageText()' in main
assert 'setReadingFocusMode(readingFocusMode)' in main
assert 'ReadingFocus.Tracker' in ocr
assert 'arrangeInReadingOrder(result.results, bitmap.width,' in ocr
assert 'fullPageText = ReadingOrder.arrange(toReadingBoxes(result.results))' in ocr
assert 'enum Mode { AUTO, ALL, LEFT, RIGHT }' in focus
assert 'r.medianHeight >= strongest.medianHeight * .78f' in focus
print("BOOK_PAGE_FOCUS_WIRING=PASS")

speech=(root/"app/src/main/java/com/vobichngan/kanzi/SpeechText.java").read_text()
assert 'mode == Mode.AUTO || mode == Mode.ALL' in focus
assert 'boxes.size(), boxes.size(), .5f, false' in focus
assert '连续文字流（推荐，保留所有检测文字）' in main
assert '单次' not in speech or 'CHUNK_LIMIT' in speech
assert 'if (breaks >= 2)' in speech
assert 'wrapped = true;' in speech
assert 'selectedBoxes==diag.size()' not in focus
print("CONTINUITY_CORE_WIRING=PASS")

flow=(root/"app/src/main/java/com/vobichngan/kanzi/TextFlowRebuilder.java").read_text()
# The dedicated real-time path must remain separate from legacy book flow.
assert "TextFlowRebuilder.arrangeRealtime(boxes)" in reading
assert "TextFlowRebuilder.arrange(boxes, regions)" in reading
assert "sameVisualRow" in flow and "adjacentVertical" in flow
assert 'b.alignedY(seamX)' in flow
assert "Math.max(ha,hb)*4.3f" in flow
print("PERSPECTIVE_FLOW_REBUILD_WIRING=PASS")

layout=(root/"app/src/main/java/com/vobichngan/kanzi/LayoutAiEngine.kt").read_text()
region=(root/"app/src/main/java/com/vobichngan/kanzi/LayoutRegion.java").read_text()
ocr=(root/"app/src/main/java/com/vobichngan/kanzi/PaddleOcrEngine.kt").read_text()
assert "PREF_AI_LAYOUT" in main and "aiLayoutEnabled = layoutAiCheck.isChecked()" in main
assert "layoutAi.analyze(bitmap" in ocr
assert "layout.regions" in ocr and "layoutElapsedMs" in ocr
assert "OnnxTensor.createTensor" in layout and "pp_doclayout_s.onnx" in layout
assert "return assigned;" in region and "covered<" in region
print("LAYOUT_AI_HYBRID_WIRING=PASS")
