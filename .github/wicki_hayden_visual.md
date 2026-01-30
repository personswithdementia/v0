# WICKI-HAYDEN LAYOUT: VISUAL BREAKDOWN

## Current (BROKEN) Row Interval Pattern

```
Row 0: base + 0*7 - 0 = base + 0     ← Starts here
Row 1: base + 1*7 - 0 = base + 7     ← +7 semitones ✓ (by luck)
Row 2: base + 2*7 - 2 = base + 12    ← +5 semitones ✗ (should be +7)
Row 3: base + 3*7 - 2 = base + 19    ← +7 semitones ✓ (recovered by luck)
Row 4: base + 4*7 - 4 = base + 24    ← +5 semitones ✗ (should be +7)
Row 5: base + 5*7 - 4 = base + 31    ← +7 semitones ✓ (recovered)
Row 6: base + 6*7 - 6 = base + 36    ← +5 semitones ✗ (should be +7)
Row 7: base + 7*7 - 6 = base + 43    ← +7 semitones ✓ (recovered)
```

Notice the alternating pattern: +7, +5, +7, +5, +7, +5, +7...
**This is COMPLETELY WRONG for Wicki-Hayden.**

## Correct Wicki-Hayden Row Interval Pattern

```
Row 0: baseNote + 0*7 + 0*2 = baseNote + 0      ← C3 (MIDI 48)
Row 1: baseNote + 1*7 + 0*2 = baseNote + 7     ← G3 (MIDI 55)
Row 2: baseNote + 2*7 + 0*2 = baseNote + 14    ← D4 (MIDI 62)
Row 3: baseNote + 3*7 + 0*2 = baseNote + 21    ← A4 (MIDI 69)
Row 4: baseNote + 4*7 + 0*2 = baseNote + 28    ← E5 (MIDI 76)
Row 5: baseNote + 5*7 + 0*2 = baseNote + 35    ← B5 (MIDI 83)
Row 6: baseNote + 6*7 + 0*2 = baseNote + 42    ← F#6 (MIDI 90)
Row 7: baseNote + 7*7 + 0*2 = baseNote + 49    ← C#7 (MIDI 97)
```

**Every row advances by exactly +7 semitones (perfect fifth upward).**

## Column Interval Pattern (Both Current and Correct are Same)

```
Col 0: baseNote + 0*2 = baseNote + 0      ← C
Col 1: baseNote + 1*2 = baseNote + 2      ← D (+2 semitones) ✓
Col 2: baseNote + 2*2 = baseNote + 4      ← E (+2 semitones) ✓
Col 3: baseNote + 3*2 = baseNote + 6      ← F# (+2 semitones) ✓
Col 4: baseNote + 4*2 = baseNote + 8      ← G# (+2 semitones) ✓
```

**Every column advances by exactly +2 semitones.**

## Wicki-Hayden Hex Layout Visualization

```
         Col -3    Col -2    Col -1     Col 0     Col 1     Col 2
         
Row 5:     G#5      A#5       B5        C#6       D#6       E6
         (base+33) (base+35) (base+36) (base+37) (base+39) (base+40)
         
       D#5        E5        F#5       G5        A5        B5
     (base+31) (base+32) (base+34) (base+35) (base+37) (base+38)
     
Row 4:     A4       B4        C5        D5        E5        F#5
         (base+21) (base+23) (base+24) (base+26) (base+28) (base+29)
         
       F#4       G#4        A4       Bb4        C5        D5
     (base+18) (base+20) (base+21) (base+22) (base+24) (base+26)
     
Row 3:    D4        E4        F#4       G4        A4        B4
         (base+14) (base+16) (base+18) (base+19) (base+21) (base+23)
         
       Bb3        C4        D4        Eb4        F4        G4
     (base+10) (base+12) (base+14) (base+15) (base+17) (base+19)
     
Row 2:    G3        A3        B3        C4        D4        E4
         (base+7)  (base+9)  (base+11) (base+12) (base+14) (base+16)
         
       Eb3        F3        G3        Ab3        Bb3       C4
     (base+3)  (base+5)  (base+7)  (base+8)  (base+10) (base+12)
     
Row 1:    C3        D3        E3        F3        G3        A3
         (base+0)  (base+2)  (base+4)  (base+5)  (base+7)  (base+9)
```

Where base = DEFAULT_CENTER_MIDI = 48 (C3)

### Reading the Layout:
- **Moving right (+1 col):** Always +2 semitones
- **Moving up (+1 row):** Always +7 semitones (perfect fifth)
- **Moving up-right diagonal:** +9 semitones (perfect fourth up from current note, or major sixth down)
- **Moving up-left diagonal:** +5 semitones (perfect fourth)
- **Staying in same row:** Linear chromatic progression (sort of)

---

## SCROLLING BUG ILLUSTRATION

### Initial State (No Scroll):
```
User places finger on visual key at (x=100, y=200)
↓
Viewport calculates: This is row=2, col=3
↓
MIDI calculation: midiForRowCol(2, 3) = 48 + 14 - 2 + 6 = 66 (F#4)
↓
Audio: Plays F#4 ✓
```

### After Scrolling Right (scrollX increases):
```
User's finger is STILL at (x=100, y=200) on screen
↓
Viewport recalculates: Now this position is row=2, col=4 (different grid index!)
↓
MIDI calculation: midiForRowCol(2, 4) = 48 + 14 - 2 + 8 = 68 (G#4)
↓
Audio: Now plays G#4 instead of F#4 ✗ WRONG!
```

### Why This Happens:

The viewport calculation produces new row/col values based on scroll position. But those row/col values are used directly in MIDI calculation WITHOUT accounting for the shift. The formula doesn't know about scroll offsets.

**The "anchor point" (what grid position corresponds to scrollX=0, scrollY=0) is never defined.**

---

## WRAPPING BUG ILLUSTRATION

### The Problem:

```
Standard wrapping (current code):
MIDI note = ((rawMidi % 128) + 128) % 128

This maps:
- rawMidi=48 → 48 (C3) ✓ Correct
- rawMidi=175 → 47 (B2) ✗ Should be off-screen or unplayable
- rawMidi=200 → 72 (C4) ✗ Completely different note!
- rawMidi=-10 → 118 (F#7) ✗ Wrong octave!
```

### Why Wrapping Breaks Note Identity:

In music, C3 and C4 are the SAME pitch class but different octaves.
Wrapping makes them the same MIDI number, losing octave information.

A key that should play C4 (MIDI 60) after scrolling might instead play C3 (MIDI 48) if the calculation wraps.

---

## THE FIX (Conceptual)

### What Should Happen:

1. **Define a reference grid position**: (refRow=0, refCol=0) → MIDI 48 (C3)
2. **Calculate visible rows/cols** based on scroll offset
3. **Apply formula WITHOUT wrapping**: `midi = 48 + row * 7 + col * 2`
4. **Clamp result to 0-127**: `midi = midi.coerceIn(0, 127)`

### Result:
- Same visual position = same MIDI note before/after scroll
- Vertical intervals are consistent (+7 semitones per row)
- Horizontal intervals are consistent (+2 semitones per col)
- Full 128-note range is reachable by scrolling
- Pattern is mathematically clean and correct

