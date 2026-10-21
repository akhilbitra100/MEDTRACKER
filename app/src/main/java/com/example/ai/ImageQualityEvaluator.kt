package com.example.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ImageQualityReport(
    val isBlurry: Boolean = false,
    val isLowLight: Boolean = false,
    val hasShadows: Boolean = false,
    val hasPoorContrast: Boolean = false,
    val isLowResolution: Boolean = false,
    val isPotentiallyCropped: Boolean = false,
    val isRotatedFixed: Boolean = false,
    val warnings: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val autoEnhanced: Boolean = false,
    val enhancedBitmap: Bitmap? = null,
    val overallReadability: String = "Good"
)

object ImageQualityEvaluator {

    /**
     * Evaluates prescription image quality on a background dispatcher.
     * Checks for blur, darkness, shadows, contrast, resolution, cropping, and rotation.
     * Applies restrained contrast and brightness enhancement if beneficial.
     */
    suspend fun evaluateImage(
        bitmap: Bitmap,
        wasRotated: Boolean = false
    ): ImageQualityReport = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width.toLong() * height.toLong()

        val warnings = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        // 1. Resolution Check
        val isLowResolution = width < 640 || height < 640 || totalPixels < 400_000L
        if (isLowResolution) {
            warnings.add("The image is low resolution ($width×$height). Small text, numbers, and dosage units may be difficult to read.")
            suggestions.add("Capture with a higher resolution camera setting or hold the phone closer to the prescription.")
        }

        // Downsample to sample size (e.g. max 240px) for fast pixel analysis (<20ms)
        val sampleMaxDim = 240
        val sampleScale = min(1.0f, sampleMaxDim.toFloat() / max(width, height))
        val sampleW = max(32, (width * sampleScale).toInt())
        val sampleH = max(32, (height * sampleScale).toInt())

        val sampleBitmap = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
        val pixels = IntArray(sampleW * sampleH)
        sampleBitmap.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

        // 2. Luminance, Contrast & Shadow Calculation
        var sumLuminance = 0.0
        val luminances = DoubleArray(pixels.size)

        var q1Sum = 0.0
        var q2Sum = 0.0
        var q3Sum = 0.0
        var q4Sum = 0.0
        var q1Count = 0
        var q2Count = 0
        var q3Count = 0
        var q4Count = 0

        val halfW = sampleW / 2
        val halfH = sampleH / 2

        for (y in 0 until sampleH) {
            for (x in 0 until sampleW) {
                val idx = y * sampleW + x
                val c = pixels[idx]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                // Standard ITU-R BT.601 perceptual luminance
                val lum = 0.299 * r + 0.587 * g + 0.114 * b
                luminances[idx] = lum
                sumLuminance += lum

                // Quadrant tracking for uneven shadows
                if (x < halfW && y < halfH) {
                    q1Sum += lum; q1Count++
                } else if (x >= halfW && y < halfH) {
                    q2Sum += lum; q2Count++
                } else if (x < halfW && y >= halfH) {
                    q3Sum += lum; q3Count++
                } else {
                    q4Sum += lum; q4Count++
                }
            }
        }

        val meanLuminance = sumLuminance / pixels.size
        var varianceSum = 0.0
        for (lum in luminances) {
            val diff = lum - meanLuminance
            varianceSum += diff * diff
        }
        val stdDevLuminance = sqrt(varianceSum / pixels.size)

        // Low-light check
        val isLowLight = meanLuminance < 65.0
        if (isLowLight) {
            warnings.add("The image appears too dark (average brightness is low).")
            suggestions.add("Retake in brighter natural light or turn on the camera flash.")
        }

        // Shadows / Uneven lighting check
        val q1Mean = if (q1Count > 0) q1Sum / q1Count else meanLuminance
        val q2Mean = if (q2Count > 0) q2Sum / q2Count else meanLuminance
        val q3Mean = if (q3Count > 0) q3Sum / q3Count else meanLuminance
        val q4Mean = if (q4Count > 0) q4Sum / q4Count else meanLuminance
        val maxQuad = max(max(q1Mean, q2Mean), max(q3Mean, q4Mean))
        val minQuad = min(min(q1Mean, q2Mean), min(q3Mean, q4Mean))
        val quadDiff = maxQuad - minQuad

        val hasShadows = quadDiff > 65.0
        if (hasShadows) {
            warnings.add("Some text may be hidden by shadows or uneven lighting across the prescription.")
            suggestions.add("Position the phone directly above the prescription to avoid casting phone shadows.")
        }

        // Contrast check
        val hasPoorContrast = stdDevLuminance < 26.0
        if (hasPoorContrast) {
            warnings.add("The image has low contrast between ink writing and paper background.")
            suggestions.add("Flatten the paper and capture against a plain, contrasting surface.")
        }

        // 3. Blur Detection via High-Frequency Gradient Variance
        var gradSum = 0.0
        var gradCount = 0
        val gradients = mutableListOf<Double>()

        for (y in 1 until sampleH - 1) {
            for (x in 1 until sampleW - 1) {
                val center = luminances[y * sampleW + x]
                val right = luminances[y * sampleW + (x + 1)]
                val down = luminances[(y + 1) * sampleW + x]
                val grad = (center - right) * (center - right) + (center - down) * (center - down)
                gradients.add(grad)
                gradSum += grad
                gradCount++
            }
        }

        val meanGrad = if (gradCount > 0) gradSum / gradCount else 0.0
        var gradVarSum = 0.0
        for (g in gradients) {
            val d = g - meanGrad
            gradVarSum += d * d
        }
        val gradVariance = if (gradCount > 0) gradVarSum / gradCount else 0.0

        // In focused text documents with dark letters on light paper, edge gradient variance is typically > 100
        val isBlurry = gradVariance < 45.0 && !isLowResolution
        if (isBlurry) {
            warnings.add("The image appears blurry. Text details may be out of focus.")
            suggestions.add("Hold the phone steady and tap the screen to focus before capturing.")
        }

        // 4. Cropped / Edge Proximity Check
        // Check if dark text pixels touch the outer 2% margins on multiple borders
        val marginX = max(1, sampleW / 50)
        val marginY = max(1, sampleH / 50)
        var topDark = 0
        var bottomDark = 0
        var leftDark = 0
        var rightDark = 0

        for (x in 0 until sampleW) {
            for (y in 0 until marginY) {
                if (luminances[y * sampleW + x] < 110.0) topDark++
            }
            for (y in (sampleH - marginY) until sampleH) {
                if (luminances[y * sampleW + x] < 110.0) bottomDark++
            }
        }

        for (y in 0 until sampleH) {
            for (x in 0 until marginX) {
                if (luminances[y * sampleW + x] < 110.0) leftDark++
            }
            for (x in (sampleW - marginX) until sampleW) {
                if (luminances[y * sampleW + x] < 110.0) rightDark++
            }
        }

        var edgesWithInk = 0
        if (topDark > sampleW / 8) edgesWithInk++
        if (bottomDark > sampleW / 8) edgesWithInk++
        if (leftDark > sampleH / 8) edgesWithInk++
        if (rightDark > sampleH / 8) edgesWithInk++

        val isPotentiallyCropped = edgesWithInk >= 2
        if (isPotentiallyCropped) {
            warnings.add("The prescription appears cropped or close to the edges. Some text may be cut off.")
            suggestions.add("Ensure all 4 corners and margins of the prescription are inside the frame.")
        }

        // 5. Rotation Feedback
        if (wasRotated) {
            suggestions.add("Image orientation was corrected automatically from camera metadata.")
        }

        // 6. Handwriting Advisory Note
        warnings.add("Some handwriting may be difficult to read. Uncertain fields will be flagged for your review.")

        // 7. Restrained Preprocessing (Brightness & Contrast Adjustment when beneficial)
        var autoEnhanced = false
        var enhancedBitmap: Bitmap? = null

        if (isLowLight || hasPoorContrast) {
            try {
                enhancedBitmap = applyRestrainedEnhancement(bitmap, isLowLight, hasPoorContrast)
                autoEnhanced = true
            } catch (_: Exception) {
                enhancedBitmap = null
                autoEnhanced = false
            }
        }

        val overallReadability = when {
            warnings.size > 3 -> "Poor (Review Recommended)"
            warnings.size > 1 -> "Moderate (Check Uncertain Fields)"
            else -> "Good"
        }

        ImageQualityReport(
            isBlurry = isBlurry,
            isLowLight = isLowLight,
            hasShadows = hasShadows,
            hasPoorContrast = hasPoorContrast,
            isLowResolution = isLowResolution,
            isPotentiallyCropped = isPotentiallyCropped,
            isRotatedFixed = wasRotated,
            warnings = warnings,
            suggestions = suggestions,
            autoEnhanced = autoEnhanced,
            enhancedBitmap = enhancedBitmap,
            overallReadability = overallReadability
        )
    }

    /**
     * Applies restrained contrast and brightness enhancement without clipping text details.
     * Note: "Never claim that preprocessing can recover text that is genuinely unreadable."
     */
    private fun applyRestrainedEnhancement(
        source: Bitmap,
        isLowLight: Boolean,
        isLowContrast: Boolean
    ): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val contrastScale = if (isLowContrast) 1.25f else 1.10f
        val brightnessShift = if (isLowLight) 30f else 10f

        val cm = ColorMatrix(
            floatArrayOf(
                contrastScale, 0f, 0f, 0f, brightnessShift,
                0f, contrastScale, 0f, 0f, brightnessShift,
                0f, 0f, contrastScale, 0f, brightnessShift,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }
}
