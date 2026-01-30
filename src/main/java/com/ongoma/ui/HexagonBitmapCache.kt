package com.ongoma.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.caverock.androidsvg.SVG

/**
 * Loads SVG assets and converts them to bitmaps using AndroidSVG
 * Optimized with LRU eviction and RGB_565 for opaque bitmaps
 */
object HexagonBitmapCache {
    private const val MAX_CACHE_SIZE = 50  // Limit cache size to prevent memory bloat
    private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    fun getBitmap(context: Context, assetPath: String, sizePx: Int): ImageBitmap? {
        val key = "$assetPath:$sizePx"

        // Check cache first
        cache[key]?.let { return it }

        // Load and cache
        return try {
            val bitmap = loadSVGAsBitmap(context, assetPath, sizePx)
            cache[key] = bitmap
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getBitmapFromVector(context: Context, resId: Int, widthPx: Int, heightPx: Int): ImageBitmap? {
        val key = "res:$resId:$widthPx:$heightPx"
        
        // Check cache first
        cache[key]?.let { return it }

        // Load and cache
        return try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, resId) ?: return null
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            drawable.setBounds(0, 0, widthPx, heightPx)
            drawable.draw(canvas)
            
            val imageBitmap = bitmap.asImageBitmap()
            cache[key] = imageBitmap
            imageBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadSVGAsBitmap(context: Context, assetPath: String, sizePx: Int): ImageBitmap {
        // Load SVG from assets using AndroidSVG
        val svg = SVG.getFromAsset(context.assets, assetPath)

        // Create bitmap to render into
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Set the SVG size to match our bitmap
        svg.documentWidth = sizePx.toFloat()
        svg.documentHeight = sizePx.toFloat()

        // Render SVG to canvas
        svg.renderToCanvas(canvas)

        return bitmap.asImageBitmap()
    }

    fun clear() {
        cache.clear()
    }
}
