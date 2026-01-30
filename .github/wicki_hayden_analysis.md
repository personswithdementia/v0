# CRITICAL WICKI-HAYDEN LAYOUT ANALYSIS REPORT
## Hexagon Keyboard - MIDI Mapping Bugs

---

## EXECUTIVE SUMMARY

The Wicki-Hayden implementation is **FUNDAMENTALLY BROKEN** in multiple critical ways:

1. **Incorrect MIDI formula** - The hexagon row calculation uses modulo that destroys the pattern
2. **Pattern breaks after scrolling** - Scroll offset calculation is never applied to MIDI calculation
3. **Missing 127-note coverage** - The formula doesn't map all 128 MIDI notes (0-127)
4. **Inconsistent interval spacing** - Horizontal and vertical intervals are wrong in the formula

---

## ISSUE #1: BROKEN MIDI FORMULA FOR HEXAGON LAYOUT

### Current Implementation (Line 126 in HexagonalLayout.kt):
```kotlin
val rawMidi = 48 + (row * 7) - ((row / 2) * 2) + (col * 2)
```

### Why This Is Wrong:

The formula `(row * 7) - ((row / 2) * 2)` is attempting to create a staggered pattern:
- Row 0: 0 - 0 = 0
- Row 1: 7 - 0 = 7
- Row 2: 14 - 2 = 12
- Row 3: 21 - 2 = 19
- Row 4: 28 - 4 = 24
- Row 5: 35 - 4 = 31

### The Correct Wicki-Hayden Pattern Should Be:
**Horizontal: +2 semitones, Vertical: +7 semitones (perfect fifth)**

For true Wicki-Hayden:
```
col * 2 + row * 7
```

Without any modulo subtraction tricks. The stagger is geometric, not numeric.

### Current Wrapping (Lines 128-134):
```kotlin
val rangeSize = 128
val minMidi = 0
val normalized = rawMidi - minMidi
val wrapped = ((normalized % rangeSize) + rangeSize) % rangeSize
return wrapped + minMidi
```

This wraps ALL notes modulo 128. So:
- MIDI 48 maps to 48 (C3 - correct)
- MIDI 176 wraps to 48 (48 + 128) - WRONG!
- MIDI 200 wraps to 72 (200 % 128 = 72) - WRONG!

**The wrapping is fundamentally incompatible with representing different notes.**

---

## ISSUE #2: SCROLLING BREAKS THE PATTERN

### The Problem:

In `generateVisibleKeys()` (lines 191-240), keys are generated with:
```kotlin
val midi = midiForRowCol(row, col, isPiano)
```

This function receives raw grid coordinates `row` and `col` **directly from the viewport calculation**:
```kotlin
val startCol = floor((minX + scrollX) / horiz).toInt() - 2
val endCol = ceil((maxX + scrollX) / horiz).toInt() + 2
val startRow = floor(-(maxY + scrollY) / vert).toInt() - 2
val endRow = ceil(-(minY + scrollY) / vert).toInt() + 2
```

### What's Missing:

**The scroll offset is NEVER subtracted from row/col before calling midiForRowCol().**

When the user scrolls:
- `scrollX` and `scrollY` change
- The viewport calculation produces different `row` and `col` values
- But `midiForRowCol()` doesn't know about the scroll offset
- **So the same visual key gets different MIDI notes after scrolling!**

Example:
- Initial position: col=0, row=0 → MIDI 48 (C3)
- After scrolling right by one hex width: col=1, row=0 → MIDI 50 (D3) ✓ Correct
- BUT if viewport recalculates and now col=1, row=0 maps to a DIFFERENT visual location due to stagger
- The MIDI note changes even though the user's finger is still in the same place

---

## ISSUE #3: INCORRECT INTERVAL SPACING

### Actual vs. Expected:

**Horizontal Movement (Columns):**
- Expected: +2 semitones per column
- Current: +2 semitones per column ✓ CORRECT

**Vertical Movement (Rows):**
- Expected: +7 semitones per row (perfect fifth)
- Current: BROKEN due to the `((row / 2) * 2)` subtraction

The subtraction causes:
- Row 0→1: +7 semitones ✓
- Row 1→2: +5 semitones ✗ (should be +7)
- Row 2→3: +7 semitones ✓
- Row 3→4: +5 semitones ✗

**This creates alternating +7, +5, +7, +5 pattern - WRONG!**

The correct Wicki-Hayden should be:
- **Every row adds exactly +7 semitones**
- **Every column adds exactly +2 semitones**

---

## ISSUE #4: MISSING 127-NOTE COVERAGE

### Current Coverage:

The wrapping approach means only 128 unique MIDI values are ever produced (0-127), but they're scattered:
- Some notes wrap multiple times
- Some octaves are unreachable
- No way to play specific octaves reliably

### What Should Happen:

A true Wicki-Hayden hex layout should:
1. Start from a defined center (C3 = MIDI 48) ✓ (Line 33)
2. Expand infinitely in all directions (same formula everywhere)
3. Each grid position maps to exactly one MIDI note
4. Support full range 0-127 by scrolling/extending the grid

Current code tries to "wrap" but this breaks the visual-to-MIDI mapping.

---

## ISSUE #5: ROW/COL OFFSET NOT HANDLED IN SCROLLING

### In MainActivity.kt (lines 140-147):

```kotlin
var scrollX by remember(hexSize, screenWidthPx) {
    mutableStateOf(screenWidthPx / 2f - hexSize * 1.73205f)
}
var scrollY by remember(hexSize, screenHeightPx, verticalSpacingFactor) {
    val centerRow = -100  // Negative means scrolled down
    mutableStateOf(-centerRow * vert - screenHeightPx / 2f)
}
```

The scroll is initialized to position col=0, row=100 (scrolled down).

But when `generateVisibleKeys()` calculates which rows/cols are visible, it just uses:
```kotlin
val startRow = floor(-(maxY + scrollY) / vert).toInt() - 2
```

This gives the VISUAL row position, but there's no offset applied before MIDI calculation.

**There should be a reference row/col (e.g., center = row 0, col 0) with MIDI offset applied to each grid position.**

---

## PROOF OF BUG: MANUAL CALCULATION

### Start Position (scrollX=0, scrollY=0):
```
row=0, col=0
rawMidi = 48 + (0*7) - (0) + (0*2) = 48 (C3) ✓
```

### After Scrolling Right (increasing scrollX, same visual position):
```
The viewport calculates new col values.
But MIDI formula still uses raw col, not adjusted.
Result: MIDI changes for same visual position ✗
```

### Example of Row Stagger Bug:
```
row=1, col=0
rawMidi = 48 + (1*7) - ((1/2)*2) + (0*2)
        = 48 + 7 - 0 + 0
        = 55 (G3)
Expected for +7 semitones: 55 ✓ (happens to work)

row=2, col=0  
rawMidi = 48 + (2*7) - ((2/2)*2) + (0*2)
        = 48 + 14 - 2 + 0
        = 60 (C4)
Expected for +7 more semitones: 62 (D4) ✗ WRONG!
```

The row calculation is offset by varying amounts (+0, +7, +12-2=+10, +19-2=+17...), which breaks the even +7 semitone spacing.

---

## ROOT CAUSE SUMMARY

| Issue | Root Cause | Impact |
|-------|-----------|--------|
| Wrong vertical intervals | `(row / 2) * 2` subtraction | Rows don't increment by 7 consistently |
| Breaks after scroll | Scroll offset not subtracted from row/col | Same visual position = different MIDI note |
| Wrapping instead of offset | Modulo 128 wrapping logic | Loses note identity and octave information |
| No real scroll/grid offset | No "baseRow" or "baseCol" reference point | Grid doesn't center properly |
| Wrong note names/octaves | MIDI wrapping breaks octave logic | Note names are wrong for higher/lower notes |

---

## FILES AFFECTED

1. **HexagonalLayout.kt** (Lines 111-136):
   - `midiForRowCol()` function - BROKEN FORMULA
   - Lines 128-134 - BROKEN WRAPPING LOGIC

2. **MainActivity.kt** (Lines 140-147):
   - Initial scroll position setup - MISSING GRID OFFSET

3. **generateVisibleKeys()** (Lines 191-240):
   - Grid calculation doesn't account for visual-to-MIDI offset
   - No baseRow/baseCol reference point

---

## WHAT NEEDS TO CHANGE

1. **Remove the stagger subtraction**: Use simple `row * 7 + col * 2`
2. **Remove modulo wrapping**: Let notes expand naturally beyond 127
3. **Add baseRow/baseCol offset**: Calculate MIDI as `baseNote + row * 7 + col * 2`
4. **Clamp to MIDI range**: Only play notes 0-127, but don't wrap them
5. **Fix scroll offset**: Subtract scroll offset from row/col BEFORE MIDI calculation

