# WICKI-HAYDEN BUG REFERENCE GUIDE
## Quick lookup for all bugs by location

---

## BUG #1: INCORRECT ROW INTERVAL FORMULA

**File:** `/home/ken-thompson/code/kwada/ongoma-v2/src/main/java/com/ongoma/ui/HexagonalLayout.kt`

**Lines:** 111-136 (midiForRowCol function, Wicki-Hayden branch)

**Specific Line:** 126

```kotlin
val rawMidi = 48 + (row * 7) - ((row / 2) * 2) + (col * 2)
```

**The Problem:**
- The `- ((row / 2) * 2)` subtraction creates alternating intervals
- Should be: `48 + (row * 7) + (col * 2)` (no subtraction)

**Why It's Wrong:**
- Row 0→1: 7 semitones ✓
- Row 1→2: 5 semitones ✗ (should be 7)
- Row 2→3: 7 semitones ✓
- Row 3→4: 5 semitones ✗ (should be 7)

**Musical Impact:** Inconsistent vertical intervals destroy Wicki-Hayden layout

---

## BUG #2: MODULO WRAPPING LOSES OCTAVE INFO

**File:** `/home/ken-thompson/code/kwada/ongoma-v2/src/main/java/com/ongoma/ui/HexagonalLayout.kt`

**Lines:** 128-134 (midiForRowCol function, wrapping logic)

```kotlin
val rangeSize = 128
val minMidi = 0

val normalized = rawMidi - minMidi
val wrapped = ((normalized % rangeSize) + rangeSize) % rangeSize
return wrapped + minMidi
```

**The Problem:**
- Wrapping MIDI 176 → 48 (loses octave information)
- Wrapping MIDI 200 → 72 (wrong note entirely)

**Why It's Wrong:**
- Modulo wrapping is for circular ranges (angles, phases)
- MIDI notes are absolute positions (C3 != C4, even with same pitch class)
- Should clamp instead: `midi.coerceIn(0, 127)`

**Musical Impact:** Can't reach full MIDI range, notes wrap to wrong octaves

---

## BUG #3: SCROLL OFFSET NOT APPLIED TO MIDI CALCULATION

**File:** `/home/ken-thompson/code/kwada/ongoma-v2/src/main/java/com/ongoma/ui/HexagonalLayout.kt`

**Lines:** 191-240 (generateVisibleKeys function)

**Specific Problem:** Line 226

```kotlin
val midi = midiForRowCol(row, col, isPiano)
```

**The Context:** 
- Lines 212-217 calculate visible grid cells using scroll:
  ```kotlin
  val startCol = floor((minX + scrollX) / horiz).toInt() - 2
  val endCol = ceil((maxX + scrollX) / horiz).toInt() + 2
  val startRow = floor(-(maxY + scrollY) / vert).toInt() - 2
  val endRow = ceil(-(minY + scrollY) / vert).toInt() + 2
  ```
- These (row, col) values are viewport-relative (shifted by scroll)
- But midiForRowCol() doesn't know about the shift!

**The Problem:**
- Viewport calculates `row=5, col=10` based on scroll position
- But midiForRowCol() treats these as absolute grid coordinates
- When scroll changes, same visual position gets different MIDI note

**Why It's Wrong:**
- midiForRowCol() should receive grid-relative coordinates
- Or should have a baseNote parameter that accounts for scroll
- Need: `midi = midiForRowCol(row - refRow, col - refCol, isPiano)`

**Musical Impact:** Same key plays different notes after scrolling - UNUSABLE

---

## BUG #4: NO REFERENCE GRID POSITION

**File:** `/home/ken-thompson/code/kwada/ongoma-v2/src/main/java/com/ongoma/MainActivity.kt`

**Lines:** 140-147 (Scroll initialization)

```kotlin
var scrollX by remember(hexSize, screenWidthPx) {
    mutableStateOf(screenWidthPx / 2f - hexSize * 1.73205f)
}
var scrollY by remember(hexSize, screenHeightPx, verticalSpacingFactor) {
    val centerRow = -100  // Variable exists but isn't used!
    mutableStateOf(-centerRow * vert - screenHeightPx / 2f)
}
```

**The Problem:**
- Tries to "center on row 0, col 0" conceptually
- But there's no reference point linking scroll to MIDI offset
- No constant defining: "scrollX=0, scrollY=0 corresponds to grid position (0, 0)"

**Why It's Wrong:**
- Without a reference, scroll position can't be converted to grid offset
- Can't tell midiForRowCol() which octave we're in
- Each scroll change produces different MIDI for same position

**Musical Impact:** Breaks mapping between visual position and MIDI note

---

## BUG #5: midiForRowCol DOESN'T ACCOUNT FOR OFFSET

**File:** `/home/ken-thompson/code/kwada/ongoma-v2/src/main/java/com/ongoma/ui/HexagonalLayout.kt`

**Lines:** 111-136 (midiForRowCol function signature)

```kotlin
fun midiForRowCol(row: Int, col: Int, isPiano: Boolean = false): Int {
    // Missing parameters:
    // - baseNote: what MIDI note is at (0, 0)?
    // - or baseRow/baseCol: offset from reference position
}
```

**The Problem:**
- Function signature has no way to specify "what octave/MIDI offset"
- Always uses hardcoded 48 as base
- Can't represent infinite grid with multiple octaves/ranges

**Why It's Wrong:**
- When scrolled, visible row/col might be row=200, col=300
- These are absolute screen-grid coordinates, not relative to reference
- Formula can't distinguish between "same position in different octaves"

**Musical Impact:** Can't properly handle scrolling or represent multiple octaves

---

## SUMMARY: BUG DEPENDENCY CHAIN

```
Bug #1 (Row interval formula)
   ↓ Broken intervals make pattern unlearnable
   
Bug #2 (Wrapping logic)
   ├─ Compounds Bug #1 by wrapping out-of-range notes
   └─ Loses octave information
   
Bug #3 (No scroll adjustment)
   ├─ Calls midiForRowCol() with unadjusted row/col
   └─ Depends on Bug #4 to work (which it doesn't)
   
Bug #4 (No reference grid position)
   └─ Makes Bug #3 impossible to fix properly
   
Bug #5 (Function signature)
   └─ Architectural limitation that prevents proper infinite grid support
```

All bugs must be fixed together for the keyboard to work.

---

## COMPLETE FIX CHECKLIST

- [ ] Remove stagger subtraction from line 126
- [ ] Change wrapping (line 133) to clamping
- [ ] Add baseNote parameter to midiForRowCol()
- [ ] Calculate grid offset from scroll in generateVisibleKeys()
- [ ] Apply offset before MIDI calculation at line 226
- [ ] Define reference grid position constant in MainActivity
- [ ] Update all midiForRowCol() calls with baseNote parameter

---

## TEST CASES TO VERIFY FIX

Test 1: Vertical intervals
- Create key at (0, 0) - note pitch X
- Create key at (1, 0) - note pitch X + 7 semitones
- Create key at (2, 0) - note pitch X + 14 semitones
- Create key at (3, 0) - note pitch X + 21 semitones
- Expected: Each adds exactly 7 semitones

Test 2: Horizontal intervals
- Create key at (0, 0) - note pitch X
- Create key at (0, 1) - note pitch X + 2 semitones
- Create key at (0, 2) - note pitch X + 4 semitones
- Create key at (0, 3) - note pitch X + 6 semitones
- Expected: Each adds exactly 2 semitones

Test 3: Scrolling consistency
- Touch position (100, 200) on screen
- Check MIDI note = M
- Scroll right by one hex width
- Touch same position (100, 200) on screen
- Check MIDI note = M (same as before)
- Expected: Same MIDI note

Test 4: Full range coverage
- Scroll through all directions
- Check that MIDI 0, 48, 60, 72, 100, 127 are all reachable
- Expected: Can reach all 128 notes by scrolling

