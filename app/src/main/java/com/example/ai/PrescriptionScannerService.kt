package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ExtractedMedicine(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var strength: String,
    var form: String = "Tablet",
    var dosageInstructions: String,
    var originalFrequency: String,
    var timingInstructions: String,
    var additionalInstructions: String = "",
    var morningSlot: Boolean = true,
    var afternoonSlot: Boolean = false,
    var nightSlot: Boolean = false,
    var durationDays: Int = 14,
    var isUncertain: Boolean = false,
    var uncertainFields: List<String> = emptyList(),
    var uncertaintyReason: String = "",
    // Feature 2: Meal-Time Scheduling
    var mealInstruction: String = "No meal instruction specified",
    var mealInstructionRaw: String = "",
    var mealInstructionFlagged: Boolean = false,
    // Feature 3: Medicine Refill Alerts
    var currentQuantity: Int = 30,
    var quantityUnit: String = "tablets",
    var doseQuantity: Int = 1,
    var lowStockThreshold: Int = 5,
    var refillAlertEnabled: Boolean = true,
    // Feature 4: Per-Medicine Reminder Notification Times
    var reminderTimes: List<String> = emptyList(),
    var specifiedTimeInPrescription: String = ""
)


sealed class ScanResult {
    data class Success(
        val medicines: List<ExtractedMedicine>,
        val isAiLive: Boolean,
        val isSyntheticDemo: Boolean = false,
        val rawAiNotes: String = ""
    ) : ScanResult()

    data class Error(val message: String) : ScanResult()
}

class PrescriptionScannerService(private val context: Context) {

    companion object {
        private const val TAG = "PrescriptionScanner"
        private val CANDIDATE_MODELS = listOf(
            "gemini-flash-latest",
            "gemini-3-flash-preview",
            "gemini-3.5-flash"
        )

        fun loadAndOrientBitmapWithRotation(context: Context, uri: Uri): OrientedBitmapResult {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open content stream from URI: $uri")
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) {
                throw IllegalArgumentException("The selected image contains no data.")
            }

            // Extract EXIF orientation from bytes
            val orientation = try {
                ByteArrayInputStream(bytes).use { bais ->
                    val exif = ExifInterface(bais)
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            } catch (_: Throwable) {
                ExifInterface.ORIENTATION_NORMAL
            }

            // Measure bounds first to prevent OutOfMemoryError on high-res camera captures
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

            val reqMaxDim = 1920
            var inSampleSize = 1
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth > reqMaxDim || origHeight > reqMaxDim) {
                while ((origWidth / inSampleSize) > reqMaxDim || (origHeight / inSampleSize) > reqMaxDim) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                ?: throw IllegalArgumentException("Could not decode image bytes into a picture.")

            val matrix = Matrix()
            var wasRotated = false
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> { matrix.postRotate(90f); wasRotated = true }
                ExifInterface.ORIENTATION_ROTATE_180 -> { matrix.postRotate(180f); wasRotated = true }
                ExifInterface.ORIENTATION_ROTATE_270 -> { matrix.postRotate(270f); wasRotated = true }
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> { matrix.postScale(-1f, 1f); wasRotated = true }
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.postScale(1f, -1f); wasRotated = true }
            }

            val orientedBitmap = if (!matrix.isIdentity) {
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }

            // Downscale to max dimension 1920 to keep under memory limits while preserving high-res OCR
            val maxDim = 1920
            val width = orientedBitmap.width
            val height = orientedBitmap.height
            val scale = Math.min(1.0f, maxDim.toFloat() / Math.max(width, height))
            val finalBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    orientedBitmap,
                    (width * scale).toInt(),
                    (height * scale).toInt(),
                    true
                )
            } else {
                orientedBitmap
            }
            return OrientedBitmapResult(finalBitmap, wasRotated)
        }

        fun loadAndOrientBitmap(context: Context, uri: Uri): Bitmap {
            return loadAndOrientBitmapWithRotation(context, uri).bitmap
        }

        fun getSyntheticDemoResult(presetId: Int, note: String = ""): ScanResult {
            return if (presetId == 2) {
                ScanResult.Success(
                    medicines = listOf(
                        ExtractedMedicine(
                            name = "Atorvastatin Calcium",
                            strength = "20 mg",
                            form = "Tablet",
                            dosageInstructions = "Take 1 tablet daily",
                            originalFrequency = "Once daily (q.h.s.)",
                            timingInstructions = "At bedtime (Night) with water",
                            additionalInstructions = "Refills: 5",
                            morningSlot = false,
                            afternoonSlot = false,
                            nightSlot = true,
                            durationDays = 30,
                            isUncertain = false,
                            mealInstruction = "No meal instruction specified",
                            mealInstructionRaw = "with water",
                            mealInstructionFlagged = false,
                            currentQuantity = 30,
                            quantityUnit = "tablets",
                            doseQuantity = 1,
                            lowStockThreshold = 5,
                            refillAlertEnabled = true
                        ),
                        ExtractedMedicine(
                            name = "Omeprazole",
                            strength = "20 mg",
                            form = "Capsule",
                            dosageInstructions = "Take 1 capsule daily",
                            originalFrequency = "Once daily (q.a.m.)",
                            timingInstructions = "30 minutes before morning breakfast",
                            additionalInstructions = "Delayed release - do not chew",
                            morningSlot = true,
                            afternoonSlot = false,
                            nightSlot = false,
                            durationDays = 14,
                            isUncertain = false,
                            mealInstruction = "Before food",
                            mealInstructionRaw = "30 minutes before morning breakfast",
                            mealInstructionFlagged = false,
                            currentQuantity = 14,
                            quantityUnit = "capsules",
                            doseQuantity = 1,
                            lowStockThreshold = 4,
                            refillAlertEnabled = true
                        ),
                        ExtractedMedicine(
                            name = "Metoprolol Succinate",
                            strength = "25 mg",
                            form = "Tablet",
                            dosageInstructions = "Take 1 tablet once daily",
                            originalFrequency = "Once daily",
                            timingInstructions = "Morning with water",
                            additionalInstructions = "Extended release tablet. Rx Note: Faint print on dosage - verify 25mg vs 50mg.",
                            morningSlot = true,
                            afternoonSlot = false,
                            nightSlot = false,
                            durationDays = 30,
                            isUncertain = true,
                            uncertainFields = listOf("Strength"),
                            uncertaintyReason = "Strength printing is partially faded on prescription. Please verify with pharmacist whether 25mg or 50mg is intended.",
                            mealInstruction = "No meal instruction specified",
                            mealInstructionRaw = "",
                            mealInstructionFlagged = false,
                            currentQuantity = 30,
                            quantityUnit = "tablets",
                            doseQuantity = 1,
                            lowStockThreshold = 5,
                            refillAlertEnabled = true
                        )
                    ),
                    isAiLive = false,
                    isSyntheticDemo = true,
                    rawAiNotes = note.ifEmpty { "[DEMO PRESET] Synthetic Cardiology Care Prescription (Dr. Marcus Vance, MD)" }
                )
            } else {
                ScanResult.Success(
                    medicines = listOf(
                        ExtractedMedicine(
                            name = "Amoxicillin",
                            strength = "500 mg",
                            form = "Capsule",
                            dosageInstructions = "Take 1 capsule twice daily",
                            originalFrequency = "BID (Twice daily)",
                            timingInstructions = "Morning and Night with meals",
                            additionalInstructions = "Finish entire 7 day course. Refills: 0.",
                            morningSlot = true,
                            afternoonSlot = false,
                            nightSlot = true,
                            durationDays = 7,
                            isUncertain = false,
                            mealInstruction = "With food",
                            mealInstructionRaw = "with meals",
                            mealInstructionFlagged = false,
                            currentQuantity = 14,
                            quantityUnit = "capsules",
                            doseQuantity = 1,
                            lowStockThreshold = 4,
                            refillAlertEnabled = true
                        ),
                        ExtractedMedicine(
                            name = "Lisinopril",
                            strength = "10 mg",
                            form = "Tablet",
                            dosageInstructions = "Take 1 tablet once daily",
                            originalFrequency = "Once daily in AM",
                            timingInstructions = "Every morning with full glass of water",
                            additionalInstructions = "Refills: 3",
                            morningSlot = true,
                            afternoonSlot = false,
                            nightSlot = false,
                            durationDays = 30,
                            isUncertain = false,
                            mealInstruction = "No meal instruction specified",
                            mealInstructionRaw = "",
                            mealInstructionFlagged = false,
                            currentQuantity = 30,
                            quantityUnit = "tablets",
                            doseQuantity = 1,
                            lowStockThreshold = 5,
                            refillAlertEnabled = true
                        ),
                        ExtractedMedicine(
                            name = "Metformin HCl",
                            strength = "500 mg",
                            form = "Tablet",
                            dosageInstructions = "Take 1 tablet daily",
                            originalFrequency = "Once daily with food",
                            timingInstructions = "Afternoon with lunch",
                            additionalInstructions = "Take with meals to prevent stomach upset. Refills: 2.",
                            morningSlot = false,
                            afternoonSlot = true,
                            nightSlot = false,
                            durationDays = 30,
                            isUncertain = false,
                            mealInstruction = "With food",
                            mealInstructionRaw = "with lunch / with food",
                            mealInstructionFlagged = false,
                            currentQuantity = 30,
                            quantityUnit = "tablets",
                            doseQuantity = 1,
                            lowStockThreshold = 6,
                            refillAlertEnabled = true
                        )
                    ),
                    isAiLive = false,
                    isSyntheticDemo = true,
                    rawAiNotes = note.ifEmpty { "[DEMO PRESET] Synthetic Community Health Prescription (Dr. Evelyn Reed, MD)" }
                )
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyzePrescription(
        imageUri: Uri?,
        cachedBitmap: Bitmap? = null,
        syntheticPresetId: Int? = null,
        forceSyntheticPreset: Boolean = false
    ): ScanResult = withContext(Dispatchers.IO) {
        // If explicitly requested to test synthetic demonstration preset
        if (forceSyntheticPreset && syntheticPresetId != null) {
            return@withContext getSyntheticDemoResult(syntheticPresetId)
        }

        val rawKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Throwable) { null }
        val apiKey = rawKey?.trim()?.removeSurrounding("\"")?.removeSurrounding("'").orEmpty()
        val hasCustomApiKey = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"

        var bitmap: Bitmap? = cachedBitmap

        // 1. If no cached bitmap provided and image URI exists, load and orient it
        if (bitmap == null && imageUri != null) {
            try {
                bitmap = loadAndOrientBitmap(context, imageUri)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load/orient image from URI: ${e.message}")
                return@withContext ScanResult.Error("Failed to read image from device: ${e.message ?: "Invalid file or URI"}")
            }
        } else if (bitmap == null && syntheticPresetId != null) {
            // User selected synthetic demo preset image
            val doc = if (syntheticPresetId == 2) {
                SyntheticPrescriptionGenerator.PRESET_2
            } else {
                SyntheticPrescriptionGenerator.PRESET_1
            }
            bitmap = SyntheticPrescriptionGenerator.createPrescriptionBitmap(doc)
        }

        if (bitmap == null) {
            return@withContext ScanResult.Error("No prescription image was selected. Please choose a photo or load the demo prescription.")
        }

        // 2. Validate API key
        if (!hasCustomApiKey) {
            Log.w(TAG, "API key check failed: rawKey configured=${rawKey != null}, isBlank=${apiKey.isBlank()}, isPlaceholder=${apiKey == "MY_GEMINI_API_KEY"}")
            // If analyzing a synthetic preset and no key is set, provide labeled demo data
            if (syntheticPresetId != null) {
                return@withContext getSyntheticDemoResult(
                    syntheticPresetId,
                    "Loaded from synthetic demo preset (Live Gemini AI scanning requires GEMINI_API_KEY in Secrets panel)."
                )
            }
            // For user-provided camera or gallery photos, inform user clearly
            return@withContext ScanResult.Error(
                "Gemini API key is not configured. Please add your GEMINI_API_KEY in the Secrets panel in AI Studio or .env file to enable live prescription image scanning."
            )
        }

        // 3. Make real Gemini Vision API call with safe exception handling
        try {
            return@withContext callGeminiVisionApi(apiKey, bitmap)
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Network connection error: Unknown host ${e.message}")
            return@withContext ScanResult.Error("No internet connection available. Please check your network and try again.")
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Network timeout during Gemini call: ${e.message}")
            return@withContext ScanResult.Error("Connection to Gemini AI timed out. Please check your network connection and try again.")
        } catch (e: IOException) {
            Log.e(TAG, "IO/Network error during Gemini call: ${e.message}")
            return@withContext ScanResult.Error("Network error communicating with Gemini AI: ${e.localizedMessage ?: "Connection interrupted"}. Please try again.")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in prescription scanner: ${e.javaClass.simpleName}: ${e.message}")
            return@withContext ScanResult.Error("Prescription extraction encountered an issue: ${e.localizedMessage ?: "Unexpected error"}. Please try again.")
        }
    }

    private fun callGeminiVisionApi(apiKey: String, bitmap: Bitmap): ScanResult {
        val base64Image = bitmapToBase64(bitmap)
        Log.d(TAG, "Preparing prescription extraction (image=${bitmap.width}x${bitmap.height})")

        val prompt = """
            You are a specialized clinical prescription analysis engine for the MedTracker app.
            Examine this prescription image thoroughly and extract all prescribed medications into a JSON array.

            REQUIRED EXTRACTION FIELDS:
            For each medication order clearly present, extract:
            - "name": Medicine brand or generic name. NEVER invent, fabricate, or assume missing medicine names.
            - "strength": Exact dosage strength (e.g., "500 mg", "10 mg", "20 mg"). If absent or illegible, write "Not specified".
            - "form": Dosage form (e.g., "Tablet", "Capsule", "Suspension", "Inhaler", "Drops", "Ointment").
            - "dosageInstructions": Specific dose amount (e.g., "Take 1 capsule", "Take 1 tablet").
            - "originalFrequency": Original frequency notation from Rx (e.g., "BID", "1x daily", "Every morning", "q8h", "prn").
            - "timingInstructions": Timing instructions (e.g., "Morning & Night with meals", "Before breakfast", "At bedtime").
            - "additionalInstructions": Extra directions or warnings (e.g., "Take with food", "Finish entire course", "Refills: 3").
            - "morningSlot": true if instructions indicate taking in morning / AM / breakfast / once daily AM / BID / TID.
            - "afternoonSlot": true if instructions indicate taking in afternoon / noon / lunch / TID / QID.
            - "nightSlot": true if instructions indicate taking at night / PM / dinner / bedtime / BID / TID.
            - "durationDays": Duration in days if written (integer), or 14 if not stated.
            - "mealInstruction": Exactly one of: "Before food", "After food", "With food", or "No meal instruction specified". NEVER invent a meal rule if absent from the prescription.
            - "mealInstructionRaw": Preserved verbatim wording regarding food/meals from the prescription (e.g. "Take before meals", "with food", "pc"). Empty string if not stated.
            - "mealInstructionFlagged": true if meal directions are conflicting, contradictory, or illegible/unclear, false otherwise.
            - "specifiedTimeInPrescription": Extract ONLY if an exact clock time (e.g., "8:00 AM", "9:00 PM", "14:00") is explicitly written on the prescription. If no exact clock time is written, write empty string "". DO NOT invent clock times.

            CRITICAL CLINICAL INTEGRITY & SAFETY RULES:
            1. NEVER invent, extrapolate, or hallucinate missing medicine names, strengths, dosages, frequencies, or instructions.
            2. NEVER invent notification times. If the prescription does not explicitly state an exact clock time, leave "specifiedTimeInPrescription" empty.
            3. If any handwriting or printed field is smudged, illegible, ambiguous, cut off, or questionable:
               * Set "isUncertain": true
               * List the questionable field names in "uncertainFields" (e.g., ["strength", "dosageInstructions"])
               * Provide a clear clinical explanation in "uncertaintyReason" explaining what needs review.
            4. If the image is not a prescription or contains no legible medications, return an empty array [].

            OUTPUT FORMAT:
            Return strictly a valid JSON array of objects with the keys specified above. No preamble, no commentary, no markdown outside the JSON.
        """.trimIndent()

        val jsonPayload = JSONObject().apply {
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            partsArray.put(JSONObject().put("text", prompt))
            partsArray.put(JSONObject().put("inlineData", JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Image)
            }))

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            put("contents", contentsArray)

            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
            })
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        var lastError: ScanResult.Error? = null

        for (modelName in CANDIDATE_MODELS) {
            Log.d(TAG, "Executing prescription extraction with Gemini model=$modelName")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .post(requestBody)
                .build()

            try {
                val callResult = client.newCall(request).execute().use { response ->
                    val responseString = response.body?.string() ?: ""
                    val responseCode = response.code

                    // Safe diagnostic logging: records HTTP status and model without exposing API key or image data
                    Log.d(TAG, "Gemini API HTTP response: model=$modelName, status=$responseCode")

                    if (!response.isSuccessful) {
                        var errorReason = ""
                        var errorStatus = ""
                        var errorMessage = ""
                        try {
                            val errRoot = JSONObject(responseString)
                            val errObj = errRoot.optJSONObject("error")
                            if (errObj != null) {
                                errorMessage = errObj.optString("message", "")
                                errorStatus = errObj.optString("status", "")
                                val details = errObj.optJSONArray("details")
                                if (details != null && details.length() > 0) {
                                    val firstDetail = details.optJSONObject(0)
                                    errorReason = firstDetail?.optString("reason", "").orEmpty()
                                }
                            }
                        } catch (_: Exception) {}

                        // Safe diagnostic logging: records error code, status, and reason without sensitive parameters
                        Log.e(TAG, "Gemini API request failed: model=$modelName, HTTP $responseCode, status=$errorStatus, reason=$errorReason")

                        val isQuotaExhausted = responseCode == 429
                        val isServiceOverloaded = responseCode == 503 || responseCode in 500..599
                        val isModelNotFound = responseCode == 404

                        // Try next candidate model if quota exhausted or model temporarily unavailable
                        if ((isQuotaExhausted || isServiceOverloaded || isModelNotFound) && modelName != CANDIDATE_MODELS.last()) {
                            Log.w(TAG, "Model $modelName encountered HTTP $responseCode ($errorStatus); attempting fallback model...")
                            return@use null
                        }

                        val userFriendlyMessage = when {
                            responseCode == 400 && (errorReason.contains("API_KEY", ignoreCase = true) || errorMessage.contains("API key", ignoreCase = true)) -> {
                                "Gemini API key is invalid or unrecognized. Please verify your GEMINI_API_KEY in the AI Studio Secrets panel."
                            }
                            responseCode == 400 -> {
                                "Gemini AI could not process this prescription request (${errorMessage.ifBlank { "Invalid payload" }}). Please ensure the image is a clear prescription."
                            }
                            responseCode == 403 -> {
                                "Gemini API access denied (HTTP 403). Please verify that the Generative Language API is enabled and quota is available."
                            }
                            responseCode == 404 -> {
                                "Gemini AI model is temporarily unavailable (HTTP 404). Please try again."
                            }
                            responseCode == 429 -> {
                                "Gemini AI rate limit or quota reached (HTTP 429). The daily limit for the Gemini API free tier has been reached. Please try again later or check quota settings in Google AI Studio."
                            }
                            responseCode in 500..599 -> {
                                "Gemini AI service is temporarily experiencing high demand (HTTP $responseCode). Please try again in a moment."
                            }
                            else -> {
                                "Gemini API returned an error (HTTP $responseCode: ${errorMessage.ifBlank { "Request failed" }}). Please check your connection and try again."
                            }
                        }
                        lastError = ScanResult.Error(userFriendlyMessage)
                        return@use null
                    }

                    val jsonRoot = JSONObject(responseString)

                    val promptFeedback = jsonRoot.optJSONObject("promptFeedback")
                    val blockReason = promptFeedback?.optString("blockReason")
                    if (!blockReason.isNullOrBlank()) {
                        Log.w(TAG, "Prescription prompt was filtered by safety system: blockReason=$blockReason")
                        return@use ScanResult.Error("Prescription analysis could not be completed due to safety filtering ($blockReason). Please try another photo.")
                    }

                    val candidates = jsonRoot.optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) {
                        Log.w(TAG, "Gemini API returned 0 candidates in response")
                        return@use ScanResult.Error("Gemini AI was unable to generate an analysis for this image. Please ensure the prescription is clearly in focus.")
                    }

                    val firstCandidate = candidates.getJSONObject(0)
                    val finishReason = firstCandidate.optString("finishReason", "UNKNOWN")
                    Log.d(TAG, "Gemini candidate received: model=$modelName, finishReason=$finishReason")

                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")

                    val textBuilder = StringBuilder()
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val partObj = parts.optJSONObject(i) ?: continue
                            if (partObj.optBoolean("thought", false)) continue
                            val t = partObj.optString("text", "")
                            if (t.isNotBlank()) {
                                textBuilder.append(t)
                            }
                        }
                    }
                    val rawText = textBuilder.toString()

                    if (rawText.isBlank()) {
                        Log.w(TAG, "Extracted text content from candidate is empty")
                        return@use ScanResult.Error("No medication text was extracted. Please ensure the prescription is clearly in focus.")
                    }

                    val parsedMedicines = parseJsonArrayText(rawText)
                    Log.d(TAG, "Extracted ${parsedMedicines.size} medications from prescription using model=$modelName")
                    if (parsedMedicines.isNotEmpty()) {
                        ScanResult.Success(
                            medicines = parsedMedicines,
                            isAiLive = true,
                            rawAiNotes = "Analyzed with Gemini Vision ($modelName)"
                        )
                    } else {
                        ScanResult.Error("No prescription medications were recognized in the uploaded image. Please verify the document is a readable prescription.")
                    }
                }

                if (callResult != null) {
                    return callResult
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception calling Gemini model=$modelName: ${e.message}")
                if (modelName == CANDIDATE_MODELS.last()) {
                    throw e
                }
            }
        }

        return lastError ?: ScanResult.Error("Prescription extraction could not be completed. Please check your network and try again.")
    }

    private fun parseJsonArrayText(rawText: String): List<ExtractedMedicine> {
        val list = mutableListOf<ExtractedMedicine>()
        try {
            var cleanText = rawText.trim()
            val startIdx = cleanText.indexOf('[')
            val endIdx = cleanText.lastIndexOf(']')
            if (startIdx != -1 && endIdx != -1 && endIdx >= startIdx) {
                cleanText = cleanText.substring(startIdx, endIdx + 1)
            } else {
                if (cleanText.startsWith("```json")) {
                    cleanText = cleanText.removePrefix("```json")
                }
                if (cleanText.startsWith("```")) {
                    cleanText = cleanText.removePrefix("```")
                }
                if (cleanText.endsWith("```")) {
                    cleanText = cleanText.removeSuffix("```")
                }
                cleanText = cleanText.trim()
            }

            val jsonArray = JSONArray(cleanText)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                val uncertainFieldsList = mutableListOf<String>()
                val jsonUncertainFields = obj.optJSONArray("uncertainFields")
                if (jsonUncertainFields != null) {
                    for (j in 0 until jsonUncertainFields.length()) {
                        uncertainFieldsList.add(jsonUncertainFields.getString(j))
                    }
                }

                val dosage = obj.optString("dosageInstructions", "").ifEmpty {
                    obj.optString("instructions", "Take as directed")
                }
                val originalFreq = obj.optString("originalFrequency", "").ifEmpty {
                    obj.optString("frequency", "Once daily")
                }
                val timing = obj.optString("timingInstructions", "").ifEmpty {
                    if (obj.optBoolean("morningSlot", false)) "Morning"
                    else if (obj.optBoolean("afternoonSlot", false)) "Afternoon"
                    else if (obj.optBoolean("nightSlot", false)) "Night"
                    else "As directed"
                }

                // Parse meal instruction
                var mealInst = obj.optString("mealInstruction", "").trim()
                if (mealInst.isBlank() || (!mealInst.equals("Before food", ignoreCase = true) &&
                        !mealInst.equals("After food", ignoreCase = true) &&
                        !mealInst.equals("With food", ignoreCase = true))) {
                    mealInst = "No meal instruction specified"
                } else {
                    mealInst = when {
                        mealInst.contains("before", ignoreCase = true) -> "Before food"
                        mealInst.contains("after", ignoreCase = true) -> "After food"
                        mealInst.contains("with", ignoreCase = true) -> "With food"
                        else -> "No meal instruction specified"
                    }
                }
                val mealRaw = obj.optString("mealInstructionRaw", "")
                val mealFlagged = obj.optBoolean("mealInstructionFlagged", false)

                // Detect form / unit
                val formVal = obj.optString("form", "Tablet")
                val unitVal = when {
                    formVal.contains("capsule", ignoreCase = true) -> "capsules"
                    formVal.contains("drop", ignoreCase = true) -> "drops"
                    formVal.contains("inhal", ignoreCase = true) -> "puffs"
                    formVal.contains("ml", ignoreCase = true) || formVal.contains("syrup", ignoreCase = true) -> "ml"
                    else -> "tablets"
                }

                // Parse dose quantity from dosageInstructions if present (e.g. "Take 2 tablets" -> 2)
                var doseQty = 1
                try {
                    val digits = Regex("\\b([1-9])\\b").find(dosage)?.value
                    if (digits != null) {
                        doseQty = digits.toIntOrNull() ?: 1
                    }
                } catch (_: Exception) {}

                val specifiedTime = obj.optString("specifiedTimeInPrescription", "").trim()
                val parsedReminderTimes = mutableListOf<String>()
                if (specifiedTime.isNotBlank()) {
                    parsedReminderTimes.add(specifiedTime)
                } else {
                    val timesArray = obj.optJSONArray("reminderTimes")
                    if (timesArray != null) {
                        for (k in 0 until timesArray.length()) {
                            val t = timesArray.optString(k, "").trim()
                            if (t.isNotBlank()) parsedReminderTimes.add(t)
                        }
                    }
                }

                list.add(
                    ExtractedMedicine(
                        name = obj.optString("name", "Unknown Medicine"),
                        strength = obj.optString("strength", "Not specified"),
                        form = formVal,
                        dosageInstructions = dosage,
                        originalFrequency = originalFreq,
                        timingInstructions = timing,
                        additionalInstructions = obj.optString("additionalInstructions", ""),
                        morningSlot = obj.optBoolean("morningSlot", true),
                        afternoonSlot = obj.optBoolean("afternoonSlot", false),
                        nightSlot = obj.optBoolean("nightSlot", false),
                        durationDays = obj.optInt("durationDays", 14),
                        isUncertain = obj.optBoolean("isUncertain", false),
                        uncertainFields = uncertainFieldsList,
                        uncertaintyReason = obj.optString("uncertaintyReason", ""),
                        mealInstruction = mealInst,
                        mealInstructionRaw = mealRaw,
                        mealInstructionFlagged = mealFlagged,
                        currentQuantity = 30,
                        quantityUnit = unitVal,
                        doseQuantity = doseQty,
                        lowStockThreshold = 5,
                        refillAlertEnabled = true,
                        reminderTimes = parsedReminderTimes,
                        specifiedTimeInPrescription = specifiedTime
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON array from raw Gemini response: ${e.message}")
        }
        return list
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    data class OrientedBitmapResult(
        val bitmap: Bitmap,
        val wasRotated: Boolean
    )
}
