# Glow System Analysis

## Current Implementation

### SVG Assets (assets/hexkeyb/)
- ✅ hex-key-teal-glowing.svg EXISTS
- ✅ hex-key-black-glowing.svg EXISTS  
- ✅ hex-key-teal-outlined.svg EXISTS
- ✅ hex-key-black-outlined.svg EXISTS

### Glow Effect in SVG
The glowing SVG contains:
1. **Drop Shadow** (outer glow): 
   - Gaussian blur: 2px
   - Offset: (0, 2px)
   - Color: Black @ 50% opacity

2. **Inner Shadow** (inner glow):
   - **Gaussian blur: 50px** ← PROBLEM!
   - Color: White @ 100%
   - No offset

### Rendering Pipeline
1. SVG loaded from assets via AndroidSVG
2. Rendered to bitmap at size: `hexSize * 1.85`
3. Typical hexSize ≈ 62px → bitmapSize ≈ 115px

### THE PROBLEM
**The inner glow has 50px blur on a 115px bitmap!**
- Blur radius = 43% of total size
- This makes the glow wash out the entire hexagon
- The effect becomes invisible/too diffuse

### What User Sees
- ✅ "Border glow" (outlined.svg) = WORKS
- ❌ "Inner glow" (glowing.svg) = TOO DIFFUSE, appears missing
- ❌ Glow fade = Animation exists but effect is barely visible

## Solution Options

### Option 1: Scale-Aware Blur
Reduce blur radius proportionally when rendering at small sizes:
- At 115px size: use 10-15px blur (not 50px)
- Requires editing SVG or runtime filter adjustment

### Option 2: Larger Render Size
Render hexagons at 2-3x current size:
- Increase bitmapSize multiplier from 1.85 to 3.0+
- May impact performance

### Option 3: Custom Glow Shader
Draw glow programmatically with Canvas blur:
- Replace glowing.svg with runtime-generated glow
- Full control over blur radius and intensity

## Recommendation
**Option 3: Custom Glow Shader**
- Best visual quality at any size
- More performant than large bitmaps
- Can match the SVG design intent

