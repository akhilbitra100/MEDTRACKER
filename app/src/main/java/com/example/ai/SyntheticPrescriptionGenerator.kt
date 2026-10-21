package com.example.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

object SyntheticPrescriptionGenerator {

    data class PrescriptionDoc(
        val id: Int,
        val clinicName: String,
        val doctorName: String,
        val patientName: String,
        val date: String,
        val title: String,
        val rxItems: List<String>
    )

    val PRESET_1 = PrescriptionDoc(
        id = 1,
        clinicName = "METROPOLITAN COMMUNITY HEALTH",
        doctorName = "Dr. Evelyn Reed, MD - Lic #MED94821",
        patientName = "Alex Johnson  |  DOB: 05/14/1954 (Age 72)",
        date = "Date: October 8, 2026",
        title = "General Care & Infection Rx",
        rxItems = listOf(
            "1. Amoxicillin 500mg Oral Capsule\n   Sig: Take 1 capsule twice daily with meals (Morning & Night) for 7 days.\n   Qty: #14  |  Refills: 0",
            "2. Lisinopril 10mg Oral Tablet\n   Sig: Take 1 tablet once daily every morning.\n   Qty: #30  |  Refills: 3",
            "3. Metformin HCl 500mg Oral Tablet\n   Sig: Take 1 tablet daily with lunch (Afternoon) with food.\n   Qty: #30  |  Refills: 2"
        )
    )

    val PRESET_2 = PrescriptionDoc(
        id = 2,
        clinicName = "ST. JUDE CARDIOVASCULAR CENTER",
        doctorName = "Dr. Marcus Vance, MD, FACC - Lic #CAR8820",
        patientName = "Eleanor Davis  |  DOB: 11/20/1948 (Age 77)",
        date = "Date: October 8, 2026",
        title = "Cardiac & Gastric Maintenance Rx",
        rxItems = listOf(
            "1. Atorvastatin Calcium 20mg Oral Tablet\n   Sig: Take 1 tablet daily at bedtime (Night) with water.\n   Qty: #30  |  Refills: 5",
            "2. Omeprazole 20mg Delayed-Release Capsule\n   Sig: Take 1 capsule 30 minutes before morning breakfast.\n   Qty: #14  |  Refills: 1",
            "3. Metoprolol Succinate 25mg ER Tablet\n   Sig: Take 1 tablet once daily in the morning.\n   [Rx Note: Faint print on dosage - verify 25mg]\n   Qty: #30  |  Refills: 2"
        )
    )

    fun createPrescriptionBitmap(doc: PrescriptionDoc): Bitmap {
        val width = 800
        val height = 1050
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clean medical stationery background
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Watermark border / header line
        paint.color = Color.parseColor("#00668B")
        paint.style = Paint.Style.FILL
        canvas.drawRect(30f, 30f, (width - 30).toFloat(), 42f, paint)

        // Clinic Header
        paint.color = Color.parseColor("#004A66")
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        canvas.drawText(doc.clinicName, 50f, 90f, paint)

        paint.textSize = 18f
        paint.color = Color.parseColor("#475569")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Department of Geriatric & Internal Medicine • Tel: (555) 234-8900", 50f, 120f, paint)
        canvas.drawText(doc.doctorName, 50f, 146f, paint)

        // Divider
        paint.color = Color.parseColor("#CBD5E1")
        paint.strokeWidth = 2f
        canvas.drawLine(50f, 165f, (width - 50).toFloat(), 165f, paint)

        // Patient block
        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("PATIENT: ${doc.patientName}", 50f, 205f, paint)

        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.parseColor("#475569")
        canvas.drawText(doc.date, 50f, 235f, paint)

        // Rx Emblem
        paint.color = Color.parseColor("#00668B")
        paint.textSize = 68f
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        canvas.drawText("℞", 50f, 320f, paint)

        paint.textSize = 22f
        paint.color = Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("PRESCRIPTION ORDERS", 140f, 300f, paint)

        // Line below Rx
        paint.color = Color.parseColor("#94A3B8")
        paint.strokeWidth = 1.5f
        canvas.drawLine(50f, 345f, (width - 50).toFloat(), 345f, paint)

        // Medication Items Box
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#F8FAFC")
        val bgRect = RectF(50f, 370f, (width - 50).toFloat(), 840f)
        canvas.drawRoundRect(bgRect, 16f, 16f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#E2E8F0")
        canvas.drawRoundRect(bgRect, 16f, 16f, paint)

        var yPos = 415f
        for ((idx, item) in doc.rxItems.withIndex()) {
            val lines = item.split("\n")
            paint.style = Paint.Style.FILL

            // Item Title
            paint.textSize = 22f
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(lines[0], 75f, yPos, paint)
            yPos += 32f

            // Sig instructions
            if (lines.size > 1) {
                paint.textSize = 19f
                paint.color = Color.parseColor("#1E293B")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(lines[1], 75f, yPos, paint)
                yPos += 28f
            }

            // Qty / Refills
            if (lines.size > 2) {
                paint.textSize = 17f
                paint.color = Color.parseColor("#64748B")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText(lines[2], 75f, yPos, paint)
                yPos += 28f
            }

            // If item 3 in preset 2 has special note, highlight it
            if (doc.id == 2 && idx == 2 && lines.size > 3) {
                paint.textSize = 16f
                paint.color = Color.parseColor("#B45309")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(lines[3], 75f, yPos, paint)
                yPos += 28f
            }

            yPos += 24f
            if (idx < doc.rxItems.size - 1) {
                paint.color = Color.parseColor("#E2E8F0")
                paint.strokeWidth = 1f
                canvas.drawLine(75f, yPos - 12f, (width - 75).toFloat(), yPos - 12f, paint)
            }
        }

        // Doctor Signature area
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#0F172A")
        paint.strokeWidth = 1.5f
        canvas.drawLine((width - 340).toFloat(), 950f, (width - 60).toFloat(), 950f, paint)

        // Script-style signature
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1E3A8A")
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        canvas.drawText("E. Reed, M.D.", (width - 300).toFloat(), 935f, paint)

        paint.color = Color.parseColor("#64748B")
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Physician Signature / DEA Registered", (width - 340).toFloat(), 975f, paint)

        // Bottom Security notice
        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 13f
        canvas.drawText("SECURITY Rx PAPER • MEDTRACKER SYNTHETIC DEMONSTRATION RECORD", 50f, 1010f, paint)

        return bitmap
    }
}
