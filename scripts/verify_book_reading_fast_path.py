#!/usr/bin/env python3
"""Catch long-reading regressions without an Android device."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/"app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text()
reading=(root/"app/src/main/java/com/vobichngan/kanzi/ReadingOrder.java").read_text()
assert "APP_VERSION = \"0.9.6\"" in main
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
assert 'arrangeInReadingOrder(result.results, bitmap.width, bitmap.height)' in ocr
assert 'fullPageText = ReadingOrder.arrange(toReadingBoxes(result.results))' in ocr
assert 'enum Mode { AUTO, ALL, LEFT, RIGHT }' in focus
assert 'r.medianHeight >= strongest.medianHeight * .78f' in focus
print("BOOK_PAGE_FOCUS_WIRING=PASS")
