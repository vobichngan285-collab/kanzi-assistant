#!/usr/bin/env python3
"""Catch long-reading regressions without an Android device."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/"app/src/main/java/com/vobichngan/kanzi/MainActivity.java").read_text()
reading=(root/"app/src/main/java/com/vobichngan/kanzi/ReadingOrder.java").read_text()
assert "APP_VERSION = \"0.9.5\"" in main
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
