package com.example.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

class MedicineChatService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun askMedicineQuestion(query: String, activeMedicineNames: List<String>): String =
        withContext(Dispatchers.IO) {
            val rawKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Throwable) { null }
            val apiKey = rawKey?.trim()?.removeSurrounding("\"")?.removeSurrounding("'").orEmpty()
            val hasCustomApiKey = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"

            if (hasCustomApiKey) {
                try {
                    return@withContext callGeminiChatApi(apiKey, query, activeMedicineNames)
                } catch (e: Exception) {
                    // If network fails, fall back to safe verified clinical knowledge base
                    return@withContext getVerifiedClinicalKnowledge(query)
                }
            } else {
                // If API key is not set, provide verified clinical knowledge base response
                return@withContext getVerifiedClinicalKnowledge(query)
            }
        }

    private fun callGeminiChatApi(apiKey: String, query: String, activeMeds: List<String>): String {
        val systemPrompt = """
            You are a clinical pharmacotherapy information assistant in the MedTracker app.
            Your role is strictly educational and informational.

            MANDATORY CLINICAL GUIDELINES:
            1. Explain:
               - Generic and brand name
               - Common FDA/clinical uses and associated health conditions
               - Distinguish common uses from an individual's actual diagnosis (state clearly that being prescribed a medication does not confirm a specific personal diagnosis)
               - Most common side effects
               - Key clinical precautions, food/drug interactions, and missed-dose instructions
            2. NEVER diagnose patients.
            3. NEVER advise starting, stopping, altering doses, or changing medications.
            4. If reliable medical evidence is unavailable for a substance or question, say clearly: "Reliable clinical information is not available for this query."
            5. Do NOT invent, hallucinate, or extrapolate unverified medical claims.
            6. Include this standard reminder at the end: "Disclaimer: This information is for educational purposes and is not a substitute for clinical advice from your prescribing doctor or pharmacist."
        """.trimIndent()

        val jsonPayload = JSONObject().apply {
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            val activeContext = if (activeMeds.isNotEmpty()) {
                "Active patient schedule medications: ${activeMeds.joinToString(", ")}.\n\n"
            } else ""

            partsArray.put(JSONObject().put("text", "$systemPrompt\n\n${activeContext}User Question: $query"))
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            put("contents", contentsArray)

            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
            })
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return getVerifiedClinicalKnowledge(query)
            }

            val jsonRoot = JSONObject(responseString)
            val candidates = jsonRoot.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val reply = parts?.optJSONObject(0)?.optString("text")

            return if (!reply.isNullOrBlank()) {
                reply.trim()
            } else {
                getVerifiedClinicalKnowledge(query)
            }
        }
    }

    private fun getVerifiedClinicalKnowledge(query: String): String {
        val q = query.lowercase().trim()

        return when {
            q.contains("amoxicillin") -> """
                💊 Amoxicillin (Penicillin-class Antibiotic)
                
                • Generic Name: Amoxicillin
                • Common Uses: Treats bacterial infections such as respiratory tract infections, ear/sinus infections, urinary tract infections, and dental infections.
                • Note on Diagnosis: While commonly prescribed for infections, individual reasons vary; only your physician can confirm your specific diagnosis.
                • Common Side Effects: Mild nausea, diarrhea, upset stomach, or skin rash.
                • Important Precautions: Finish the entire prescribed course even if you feel better. Seek immediate medical attention if you experience signs of an allergic reaction (hives, difficulty breathing, facial swelling).
                
                Disclaimer: This information is educational and not a substitute for clinical consultation with your doctor or pharmacist.
            """.trimIndent()

            q.contains("lisinopril") -> """
                💊 Lisinopril (ACE Inhibitor)
                
                • Generic Name: Lisinopril
                • Common Uses: Management of hypertension (high blood pressure) and heart failure, and improving survival after myocardial infarction (heart attack).
                • Note on Diagnosis: Lowering blood pressure is a preventive therapy; taking Lisinopril does not mean you have experienced heart failure or a heart attack.
                • Common Side Effects: Dry, persistent cough, dizziness/lightheadedness when standing, headache, or fatigue.
                • Important Precautions: Avoid potassium supplements or high-potassium salt substitutes unless advised by your doctor. Discontinue and call your doctor immediately if you develop facial or throat swelling (angioedema).
                
                Disclaimer: This information is educational and not a substitute for clinical consultation with your doctor or pharmacist.
            """.trimIndent()

            q.contains("metformin") -> """
                💊 Metformin (Biguanide Antihyperglycemic)
                
                • Generic Name: Metformin Hydrochloride
                • Common Uses: First-line medication for type 2 diabetes mellitus to improve glycemic control; sometimes used off-label for prediabetes or PCOS.
                • Note on Diagnosis: Being prescribed Metformin does not by itself define your specific diagnostic category. Consult your healthcare team regarding your exact metabolic markers.
                • Common Side Effects: Nausea, loose stools, abdominal gas, or metallic taste, particularly when starting therapy.
                • Important Precautions: Always take with meals to reduce gastrointestinal discomfort. Avoid excessive alcohol consumption. Inform medical staff before contrast dye scans.
                
                Disclaimer: This information is educational and not a substitute for clinical consultation with your doctor or pharmacist.
            """.trimIndent()

            q.contains("atorvastatin") -> """
                💊 Atorvastatin (HMG-CoA Reductase Inhibitor / Statin)
                
                • Generic Name: Atorvastatin Calcium (e.g. Lipitor)
                • Common Uses: Lowers LDL ("bad") cholesterol and triglycerides, and reduces cardiovascular risk (stroke, heart attack).
                • Note on Diagnosis: Prescribed both for existing lipid conditions and for preventive cardiovascular protection.
                • Common Side Effects: Mild muscle ache, joint pain, or mild digestive upset.
                • Important Precautions: Report unusual or severe muscle pain, tenderness, or weakness to your doctor promptly. Limit grapefruit juice consumption.
                
                Disclaimer: This information is educational and not a substitute for clinical consultation with your doctor or pharmacist.
            """.trimIndent()

            q.contains("omeprazole") -> """
                💊 Omeprazole (Proton Pump Inhibitor / PPI)
                
                • Generic Name: Omeprazole (e.g. Prilosec)
                • Common Uses: Treats gastroesophageal reflux disease (GERD), heartburn, stomach ulcers, and protects the stomach lining.
                • Common Side Effects: Headache, mild stomach pain, diarrhea, or gas.
                • Important Precautions: Best taken 30 to 60 minutes before breakfast. Swallow whole; do not chew delayed-release capsules.
                
                Disclaimer: This information is educational and not a substitute for clinical consultation with your doctor or pharmacist.
            """.trimIndent()

            q.contains("metoprolol") -> """
                💊 Metoprolol (Beta-Blocker)
                
                • Generic Name: Metoprolol Tartrate / Succinate ER
                • Common Uses: High blood pressure, angina (chest pain), and managing heart rate / arrhythmia.
                • Common Side Effects: Slow heart rate, fatigue, dizziness, or cold hands/feet.
                • Important Precautions: Do not abruptly stop taking Metoprolol without doctor guidance, as this can cause rebound blood pressure elevation.
                
                Disclaimer: This information is educational and not a substitute for clinical consultation with your doctor or pharmacist.
            """.trimIndent()

            else -> """
                📋 Clinical Information Guidance
                
                Regarding "$query":
                • Please specify the exact medication name (e.g. Amoxicillin, Lisinopril, Metformin, Atorvastatin) or select one of your confirmed medications from the buttons above.
                • Reliable clinical information requires an exact pharmaceutical name to provide accurate generic details, side effects, and precautions.
                • If this is about an unlisted drug or medical symptom, please consult your prescribing physician or licensed pharmacist directly.
                
                Disclaimer: This assistant provides general pharmacological information only and cannot diagnose conditions or recommend changes to medications.
            """.trimIndent()
        }
    }
}
