# CODE WALKTHROUGH: WHERE THE BUGS ARE

## FILE: HexagonalLayout.kt

### ISSUE #1: Line 111-136 - midiForRowCol() Function

```kotlin
fun midiForRowCol(row: Int, col: Int, isPiano: Boolean = false): Int {
    if (isPiano) {
        // Chromatic Fourth Layout
        // Base(48) + Row*5 (fourths) + Col*1 (semitones)
        val rawMidi = 48 + (row * 5) + col
        
        // Wrap to full MIDI range 0-127 (128 notes)
        val rangeSize = 128
        val minMidi = 0
        
        val normalized = rawMidi - minMidi
        val wrapped = ((normalized % rangeSize) + rangeSize) % rangeSize
        return wrapped + minMidi
    } else {
        // Wicki-Hayden Layout ← THIS IS BROKEN
        val rawMidi = 48 + (row * 7) - ((row / 2) * 2) + (col * 2)
                      ^^^^ base  ^^^^^^^^^^^^^^^^^^^^^^^^^   ^^^^^^^^^^
                           OK    WRONG! Stagger logic       CORRECT (±2)
        
        // Wrap to full MIDI range 0-127 (128 notes)
        val rangeSize = 128
        val minMidi = 0
        
        val normalized = rawMidi - minMidi
        val wrapped = ((normalized % rangeSize) + rangeSize) % rangeSize
                      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                      WRONG! Wrapping destroys note identity
        return wrapped + minMidi
    }
}
```

### BUG ANALYSIS:

#### Bug A: Stagger Subtraction `(row / 2) * 2`
- **Intent:** Attempt to handle hexagon row offset (even/odd rows shifted in visual space)
- **Problem:** Mixes visual geometry with MIDI calculation
- **Result:** Inconsistent row intervals (+7, +5, +7, +5...)

#### Bug B: Modulo Wrapping
- **Intent:** Constrain MIDI to 0-127 range
- **Problem:** Wraps numbers, doesn't clamp them. Causes same MIDI note for different octaves.
- **Example:** MIDI 176 (off-range) wraps to 48 (C3), but should remain out-of-range or map to valid high note

#### Bug C: No Scroll Offset Parameter
- **Intent:** Function should account for "where in the infinite grid are we?"
- **Problem:** Function only sees raw row/col, not which "chunk" of grid these are in
- **Missing:** A baseNote or (baseRow, baseCol) parameter

---

### ISSUE #2: Line 166-186 - centerForRowCol() Function

```kotlin
fun centerForRowCol(
    row: Int, 
    col: Int, 
    hexSize: Float, 
    scrollX: Float = 0f,    ← Takes scroll parameters
    scrollY: Float = 0f,    ← Takes scroll parameters
    verticalSpacingFactor: Float = VERTICAL_SPACING_FACTOR,
    isPiano: Boolean = false
): Offset {
    val horiz = hexSize * HORIZONTAL_SPACING_FACTOR
    val vert = hexSize * verticalSpacingFactor
    
    // Piano mode: Simple grid (no stagger)
    // Hex mode: Staggered rows (even rows shifted right)
    val xOffset = if (!isPiano && row % 2 == 0) horiz / 2f else 0f
                                                  ^^^^^^^^^^^^
                                                  Stagger in visual space ✓
    
    val x = col * horiz + xOffset - scrollX       ← Scroll applied ✓
    val y = -row * vert - scrollY                 ← Scroll applied ✓
    
    return Offset(x, y)
}
```

**Key observation:** This function DOES apply scroll offsets to visual positions (x, y).
But midiForRowCol() doesn't! That's the disconnect.

---

### ISSUE #3: Line 191-240 - generateVisibleKeys() Function

```kotlin
fun generateVisibleKeys(
    hexSize: Float,
    screenWidth: Float,
    screenHeight: Float,
    scrollX: Float,
    scrollY: Float,
    approxVisibleCols: Int = 18,
    approxVisibleRows: Int = 30,
    verticalSpacingFactor: Float = VERTICAL_SPACING_FACTOR,
    isPiano: Boolean = false
): List<HexKey> {
    val keys = mutableListOf<HexKey>()
    
    val horiz = hexSize * HORIZONTAL_SPACING_FACTOR
    val vert = hexSize * verticalSpacingFactor
    
    val minX = -hexSize
    val maxX = screenWidth + hexSize
    val minY = -hexSize
    val maxY = screenHeight + hexSize
    
    // Calculate which rows/cols are visible
    val startCol = floor((minX + scrollX) / horiz).toInt() - 2
    val endCol = ceil((maxX + scrollX) / horiz).toInt() + 2
    
    val startRow = floor(-(maxY + scrollY) / vert).toInt() - 2
    val endRow = ceil(-(minY + scrollY) / vert).toInt() + 2
    
    for (row in startRow..endRow) {
        for (col in startCol..endCol) {
            val center = centerForRowCol(
                row, col, hexSize, scrollX, scrollY, 
                verticalSpacingFactor, isPiano
            )
            
            val midi = midiForRowCol(row, col, isPiano)
                       ^^^^^^^^^^^^^^
                       ← Uses raw row/col WITHOUT scroll adjustment!
            
            keys.add(
                HexKey(
                    row = row,
                    col = col,
                    midiNote = midi,
                    noteName = noteName(midi, showOctaveOnlyOnC = true),
                    center = center,
                    isBlackKey = isBlackKey(midi)
                )
            )
        }
    }
    return keys
}
```

**The Bug:** Line 226 calls `midiForRowCol(row, col, isPiano)` with the viewport-calculated row/col.
But those values are already shifted by scroll! The MIDI formula doesn't account for this.

**Correct approach would be:**
```kotlin
val centerRow = 0  // Reference point
val centerCol = 0  // Reference point
val gridRow = row - centerRow  // Relative to center
val gridCol = col - centerCol  // Relative to center

val midi = midiForRowCol(gridRow, gridCol, isPiano)
```

Or pass baseNote as parameter and let midiForRowCol() use it.

---

## FILE: MainActivity.kt

### ISSUE #4: Lines 140-147 - Scroll Initialization

```kotlin
// Infinite 2D scroll state - initialized to center on row 0, col 0
val vert = hexSize * verticalSpacingFactor
var scrollX by remember(hexSize, screenWidthPx) {
    mutableStateOf(screenWidthPx / 2f - hexSize * 1.73205f)
}
var scrollY by remember(hexSize, screenHeightPx, verticalSpacingFactor) {
    // Default scroll position: 100 rows down from center (instant on boot)
    val centerRow = -100  // Negative means scrolled down
    mutableStateOf(-centerRow * vert - screenHeightPx / 2f)
}
```

**The Problem:** 
- The intent is to "center on row 0, col 0"
- But there's no reference MIDI note defined
- No mapping between (scrollX, scrollY) and (baseNote)

**Missing:** A constant or variable that defines what MIDI note is at scrollX=0, scrollY=0.

---

### ISSUE #5: Lines 154-168 - Key Generation Call

```kotlin
val visibleKeys by remember(scrollX, scrollY, hexSize, verticalSpacingFactor, isPiano) {
    derivedStateOf {
        HexagonalLayout.generateVisibleKeys(
            hexSize = hexSize,
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx,
            scrollX = scrollX,
            scrollY = scrollY,
            approxVisibleCols = 18,
            approxVisibleRows = 30,
            verticalSpacingFactor = verticalSpacingFactor,
            isPiano = isPiano
        )
    }
}
```

**The Call:** Passes scrollX/scrollY to generateVisibleKeys().

**But:** generateVisibleKeys() uses them to calculate which grid cells are visible, 
then passes those raw grid coordinates to midiForRowCol() without adjustment.

**Missing:** No conversion from "scroll position" to "base MIDI offset".

---

## SUMMARY: ROOT CAUSE CHAIN

```
1. midiForRowCol() formula is mathematically wrong
   ↓
2. Formula can't handle infinite grid properly
   ↓
3. No concept of "where am I in the infinite grid?"
   ↓
4. Scroll position doesn't map to MIDI offset
   ↓
5. Same visual position produces different MIDI notes after scrolling
   ↓
6. Pattern appears broken (alternating intervals, wrapping errors)
   ↓
7. User hears wrong notes, pattern inconsistencies
   ↓
8. VERDICT: Not worth shipping ✗
```

---

## THE COMPLETE MAPPING PROBLEM

Current broken architecture:

```
Touch position (x, y)
    ↓ [via centerForRowCol with scroll applied]
Visual hex grid position (row, col) ← Relative to viewport
    ↓ [via midiForRowCol with NO scroll adjustment]
MIDI note (0-127) ← Should be consistent visual position
    ↓ [wrapped via modulo]
Possibly wrong note or wrapped octave
    ↓
Audio plays wrong note or unexpected octave
```

Correct architecture should be:

```
Touch position (x, y)
    ↓ [via centerForRowCol with scroll applied]
Visual hex grid position (row, col)
    ↓ [adjust for infinite grid: baseRow, baseCol]
Infinite grid position (absRow, absCol)
    ↓ [via clean formula: baseNote + absRow * 7 + absCol * 2]
Calculated MIDI note (possibly > 127)
    ↓ [clamp to 0-127]
Valid MIDI note
    ↓
Audio plays correct note
```

