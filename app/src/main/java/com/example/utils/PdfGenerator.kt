package com.example.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.EmployeeProfile
import com.example.data.TourEntry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    /**
     * Generates a monthly tour diary PDF and returns the file path.
     * Dimensions are set according to pageSize: Letter (612 x 792) or Legal (612 x 1008).
     */
    fun generateMonthlyDiary(
        context: Context,
        profile: EmployeeProfile,
        entries: List<TourEntry>,
        monthYear: String,
        isLegalSize: Boolean
    ): File? {
        val pdfDocument = PdfDocument()

        // Page sizes in PostScript points (1/72 inch)
        val pageWidth = 612
        val pageHeight = if (isLegalSize) 1008 else 792

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Paints
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.BLACK
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }

        val secondaryLinePaint = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }

        val footerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // --- Margins and Table Dimensions ---
        val leftMargin = 20f
        val rightMargin = pageWidth - leftMargin // 592f
        val topMargin = 25f

        // Draw Title
        var yPos = topMargin + 15
        val displayTitleEn = "DETAILS OF TRAVELS MADE FOR INSPECTION OF CRIME SCENES"
        canvas.drawText(displayTitleEn, pageWidth / 2f, yPos, titlePaint)

        // Draw Meta Info
        yPos += 22
        val nameLabel = "Name: "
        val nameVal = profile.name.ifBlank { "____________" }
        canvas.drawText(nameLabel + nameVal, leftMargin, yPos, metaPaint)

        val desLabel = "Designation: "
        val desVal = profile.designation.ifBlank { "____________" }
        val desWidth = metaPaint.measureText(nameLabel + nameVal) + 50f
        canvas.drawText(desLabel + desVal, leftMargin + desWidth, yPos, metaPaint)

        val postingLabel = "Mobile Forensic Unit: "
        val postingVal = profile.posting.ifBlank { "____________" }
        val postingWidth = desWidth + metaPaint.measureText(desLabel + desVal) + 50f
        canvas.drawText(postingLabel + postingVal, leftMargin + postingWidth, yPos, metaPaint)

        // Month Indicator
        yPos += 15
        val formattedMonth = try {
            val parser = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val formatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val date = parser.parse(monthYear)
            if (date != null) formatter.format(date) else monthYear
        } catch (e: Exception) {
            monthYear
        }
        canvas.drawText("Month: $formattedMonth", leftMargin, yPos, metaPaint)

        // --- Draw Watermark Background (Behind grid) ---
        val xWatermark = pageWidth / 2f
        val yWatermark = (topMargin + pageHeight - 50f) / 2f + 10f
        drawFslWatermark(canvas, xWatermark, yWatermark)

        // --- Draw Table Grid ---
        yPos += 15
        val tableTop = yPos
        val colWidths = floatArrayOf(24f, 45f, 38f, 38f, 50f, 45f, 60f, 60f, 92f, 70f, 50f)
        // Cumulative coordinates
        val xCoords = FloatArray(12)
        xCoords[0] = leftMargin
        for (i in 1..11) {
            xCoords[i] = xCoords[i - 1] + colWidths[i - 1]
        }

        // Header Height: 2 levels
        val headerRowHeight = 15f
        val tableHeaderBottom = tableTop + (headerRowHeight * 2)

        // Background / borders of table headers
        canvas.drawRect(leftMargin, tableTop, rightMargin, tableHeaderBottom, linePaint)

        // Horizontal line separating top & bottom headers
        canvas.drawLine(xCoords[2], tableTop + headerRowHeight, xCoords[4], tableTop + headerRowHeight, linePaint) // for travel details
        canvas.drawLine(xCoords[7], tableTop + headerRowHeight, xCoords[10], tableTop + headerRowHeight, linePaint) // for crime details

        // Draw Header Vertical Lines & Text
        fun drawHeaderCell(xStart: Float, xEnd: Float, text: String, yCenter: Float) {
            val xCenter = (xStart + xEnd) / 2f
            canvas.drawText(text, xCenter, yCenter + 2.5f, headerPaint)
        }

        // Draw standard vertical columns
        // S.No (Col 0)
        canvas.drawLine(xCoords[1], tableTop, xCoords[1], tableHeaderBottom, linePaint)
        drawHeaderCell(xCoords[0], xCoords[1], "S.No", tableTop + headerRowHeight)

        // Date (Col 1)
        canvas.drawLine(xCoords[2], tableTop, xCoords[2], tableHeaderBottom, linePaint)
        drawHeaderCell(xCoords[1], xCoords[2], "Date", tableTop + headerRowHeight)

        // Travel Details (Spans Col 2 & 3)
        canvas.drawLine(xCoords[4], tableTop, xCoords[4], tableHeaderBottom, linePaint)
        drawHeaderCell(xCoords[2], xCoords[4], "Travel Details", tableTop + headerRowHeight / 2)

        // Split Sub-headers Travel Details
        canvas.drawLine(xCoords[3], tableTop + headerRowHeight, xCoords[3], tableHeaderBottom, linePaint)
        drawHeaderCell(xCoords[2], xCoords[3], "Departure", tableTop + headerRowHeight * 1.5f)
        drawHeaderCell(xCoords[3], xCoords[4], "Arrival", tableTop + headerRowHeight * 1.5f)

        // Travel Mode (Col 4)
        canvas.drawLine(xCoords[5], tableTop, xCoords[5], tableHeaderBottom, linePaint)
        drawHeaderCell(xCoords[4], xCoords[5], "Mode", tableTop + headerRowHeight)

        // Distance (Col 5)
        canvas.drawLine(xCoords[6], tableTop, xCoords[6], tableHeaderBottom, linePaint)
        drawHeaderCell(xCoords[5], xCoords[6], "Dist (km)", tableTop + headerRowHeight)

        // CS No (Col 6)
        canvas.drawLine(xCoords[7], tableTop, xCoords[7], tableHeaderBottom, linePaint)
        drawHeaderCell(xCoords[6], xCoords[7], "C.S. No.", tableTop + headerRowHeight)

        // Case Details / Crime details (Spans Col 7, 8, 9)
        canvas.drawLine(xCoords[10], tableTop, xCoords[10], tableHeaderBottom, linePaint)
        drawHeaderCell(xCoords[7], xCoords[10], "Details of Crime / Purpose of Travel", tableTop + headerRowHeight / 2)

        // Split Sub-headers Crime Details
        canvas.drawLine(xCoords[8], tableTop + headerRowHeight, xCoords[8], tableHeaderBottom, linePaint)
        canvas.drawLine(xCoords[9], tableTop + headerRowHeight, xCoords[9], tableHeaderBottom, linePaint)
        drawHeaderCell(xCoords[7], xCoords[8], "FIR No.", tableTop + headerRowHeight * 1.5f)
        drawHeaderCell(xCoords[8], xCoords[9], "Police Station", tableTop + headerRowHeight * 1.5f)
        drawHeaderCell(xCoords[9], xCoords[10], "District", tableTop + headerRowHeight * 1.5f)

        // Report Date (Col 10)
        drawHeaderCell(xCoords[10], xCoords[11], "Rep. Date", tableTop + headerRowHeight)

        // --- Draw Table rows dynamically ---
        var currentY = tableHeaderBottom
        val maxAvailableHeight = pageHeight - 90f // Reserve 90 points for footer & margins

        // Let's check max case lines across all entries to expand row height if needed
        var maxLinesInAnyEntry = 1
        entries.forEach { entry ->
            val csL = entry.csNumber.split("\n").filter { it.isNotBlank() }.size
            val firL = entry.firNumber.split("\n").filter { it.isNotBlank() }.size
            val psL = entry.policeStation.split("\n").filter { it.isNotBlank() }.size
            val distL = entry.district.split("\n").filter { it.isNotBlank() }.size
            val entryMax = maxOf(csL, firL, psL, distL, 1)
            if (entryMax > maxLinesInAnyEntry) {
                maxLinesInAnyEntry = entryMax
            }
        }

        // Determine row height dynamically based on data size to guarantee single page support
        val targetRowHeight = if (entries.isNotEmpty()) {
            val remainingHeight = maxAvailableHeight - tableHeaderBottom
            val calculated = remainingHeight / entries.size
            val minRow = if (maxLinesInAnyEntry > 1) 22f else 15f
            val maxRow = if (maxLinesInAnyEntry > 1) 32f else 22f
            calculated.coerceIn(minRow, maxRow)
        } else {
            20f
        }

        fun drawCellText(text: String, xStart: Float, xEnd: Float, yRowCenter: Float, align: Paint.Align = Paint.Align.CENTER) {
            val paddedPadding = 2f
            val availableWidth = (xEnd - xStart) - (paddedPadding * 2)
            
            val lines = text.split("\n")
            if (lines.all { it.isBlank() }) {
                textPaint.textAlign = align
                if (align == Paint.Align.CENTER) {
                    canvas.drawText("-", (xStart + xEnd) / 2f, yRowCenter + 2.5f, textPaint)
                } else {
                    canvas.drawText("-", xStart + paddedPadding, yRowCenter + 2.5f, textPaint)
                }
                return
            }

            val lineSpacing = 6.2f
            val totalHeight = (lines.size - 1) * lineSpacing
            val startYOffset = -totalHeight / 2f

            lines.forEachIndexed { i, originalLine ->
                var cleanText = originalLine.trim()
                if (cleanText.isBlank()) {
                    return@forEachIndexed
                }
                var textWidth = textPaint.measureText(cleanText)
                if (textWidth > availableWidth) {
                    while (cleanText.length > 3 && textWidth > availableWidth) {
                        cleanText = cleanText.substring(0, cleanText.length - 2)
                        textWidth = textPaint.measureText(cleanText + "..")
                    }
                    cleanText += ".."
                }

                val currentLineY = yRowCenter + startYOffset + (i * lineSpacing)

                textPaint.textAlign = align
                if (align == Paint.Align.CENTER) {
                    canvas.drawText(cleanText, (xStart + xEnd) / 2f, currentLineY + 2.5f, textPaint)
                } else {
                    canvas.drawText(cleanText, xStart + paddedPadding, currentLineY + 2.5f, textPaint)
                }
            }
        }

        // Draw data rows
        val sortedEntries = entries.sortedWith(compareBy<TourEntry> { it.date }.thenBy { it.depTime })
        sortedEntries.forEachIndexed { index, entry ->
            val rowBottom = currentY + targetRowHeight

            // Draw horizontal partition line
            canvas.drawLine(leftMargin, rowBottom, rightMargin, rowBottom, linePaint)

            // Vertical boundary lines
            for (i in 0..11) {
                canvas.drawLine(xCoords[i], currentY, xCoords[i], rowBottom, linePaint)
            }

            val yCenter = (currentY + rowBottom) / 2f

            // Populate cells
            // S.No
            drawCellText((index + 1).toString(), xCoords[0], xCoords[1], yCenter)
            // Date (formatted cleanly as DD/MM)
            val compactDate = formatDate(entry.date)
            drawCellText(compactDate, xCoords[1], xCoords[2], yCenter)
            // Departure Time
            drawCellText(entry.depTime, xCoords[2], xCoords[3], yCenter)
            // Arrival Time / Arrival Date combination
            val arrivalDisplay = if (entry.arrDate.isNotBlank() && entry.arrDate != entry.date) {
                try {
                    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val formatter = SimpleDateFormat("dd/MM", Locale.getDefault())
                    val parsed = parser.parse(entry.arrDate)
                    val compactArrDate = if (parsed != null) formatter.format(parsed) else entry.arrDate
                    "$compactArrDate ${entry.arrTime}"
                } catch (e: Exception) {
                    "${entry.arrDate} ${entry.arrTime}"
                }
            } else {
                entry.arrTime
            }
            drawCellText(arrivalDisplay, xCoords[3], xCoords[4], yCenter)
            // Mode
            drawCellText(entry.travelMode, xCoords[4], xCoords[5], yCenter)
            // Distance
            drawCellText(String.format(Locale.getDefault(), "%.1f", entry.distance), xCoords[5], xCoords[6], yCenter)
            // C.S. No
            drawCellText(entry.csNumber, xCoords[6], xCoords[7], yCenter)
            // FIR No
            drawCellText(entry.firNumber, xCoords[7], xCoords[8], yCenter)
            // Police Station
            drawCellText(entry.policeStation, xCoords[8], xCoords[9], yCenter, Paint.Align.LEFT)
            // District
            drawCellText(entry.district, xCoords[9], xCoords[10], yCenter, Paint.Align.LEFT)
            // Report Date
            val compactReportDate = formatDate(entry.reportDate)
            drawCellText(compactReportDate, xCoords[10], xCoords[11], yCenter)

            currentY = rowBottom
        }

        // Draw empty rows if entries are empty to give a neat standard log book print template
        if (sortedEntries.isEmpty()) {
            val emptyRowCount = if (isLegalSize) 25 else 18
            for (index in 0 until emptyRowCount) {
                val rowBottom = currentY + targetRowHeight
                canvas.drawLine(leftMargin, rowBottom, rightMargin, rowBottom, linePaint)
                for (i in 0..11) {
                    canvas.drawLine(xCoords[i], currentY, xCoords[i], rowBottom, linePaint)
                }
                currentY = rowBottom
            }
        }

        // --- Draw Certification / Declaration Line ---
        val certY = currentY + 16f
        val certPaint = Paint().apply {
            color = Color.BLACK
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("This is to certify that all the details mentioned are true and correct to the best of my knowledge and belief.", leftMargin, certY, certPaint)

        // --- Draw Footer Block ---
        // Signature line and mobile forensic unit
        val preferredY = certY + 26f
        val maxY = pageHeight - 50f
        yPos = minOf(preferredY, maxY)
        val sigLineLength = 140f
        val sigX = rightMargin - sigLineLength

        canvas.drawLine(sigX, yPos, rightMargin, yPos, secondaryLinePaint)
        yPos += 12f
        canvas.drawText("Signature", sigX + 25f, yPos, footerPaint)

        yPos += 12f
        canvas.drawText("Designation: ${profile.designation}", sigX, yPos, metaPaint)

        yPos += 12f
        canvas.drawText("Mobile Forensic Unit", sigX, yPos, metaPaint)

        // Complete & Save
        pdfDocument.finishPage(page)

        val outputDir = context.cacheDir
        val file = File(outputDir, "Tour_Diary_${monthYear}.pdf")
        return try {
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formatter = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
            val parsed = parser.parse(dateStr)
            if (parsed != null) formatter.format(parsed) else dateStr
        } catch (e: Exception) {
            // Fallback for short dates (already formatted)
            dateStr
        }
    }

    private fun drawFslWatermark(canvas: Canvas, cx: Float, cy: Float) {
        val circlePaint = Paint().apply {
            color = Color.argb(12, 30, 61, 89)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            isAntiAlias = true
        }
        val thinCirclePaint = Paint().apply {
            color = Color.argb(8, 30, 61, 89)
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            isAntiAlias = true
        }
        val goldPaint = Paint().apply {
            color = Color.argb(10, 229, 169, 59)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val bluePaint = Paint().apply {
            color = Color.argb(12, 74, 111, 165)
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
            isAntiAlias = true
        }
        val redPaint = Paint().apply {
            color = Color.argb(12, 188, 36, 60)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val fslTextPaint = Paint().apply {
            color = Color.argb(14, 30, 61, 89)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // Draw concentric circles
        canvas.drawCircle(cx, cy, 140f, circlePaint)
        canvas.drawCircle(cx, cy, 130f, thinCirclePaint)

        // Draw Lion Capital (At the top of watermark circle)
        val lY = cy - 90f
        canvas.drawRect(cx - 15f, lY + 30f, cx + 15f, lY + 33f, goldPaint) // pedestal
        canvas.drawCircle(cx, lY + 15f, 15f, goldPaint) // head
        canvas.drawCircle(cx - 10f, lY + 22f, 10f, goldPaint) // left mane
        canvas.drawCircle(cx + 10f, lY + 22f, 10f, goldPaint) // right mane

        // Draw Scales of Justice in center
        val sY = cy
        canvas.drawLine(cx - 50f, sY - 15f, cx + 50f, sY - 15f, circlePaint) // beam
        canvas.drawLine(cx, sY - 30f, cx, sY + 30f, circlePaint) // pillar
        canvas.drawRect(cx - 10f, sY + 30f, cx + 10f, sY + 33f, circlePaint) // base

        // Left Pan
        val leftPanPath = android.graphics.Path().apply {
            moveTo(cx - 50f, sY - 15f)
            lineTo(cx - 60f, sY + 10f)
            lineTo(cx - 40f, sY + 10f)
            close()
        }
        canvas.drawPath(leftPanPath, thinCirclePaint)
        canvas.drawLine(cx - 64f, sY + 10f, cx - 36f, sY + 10f, circlePaint)

        // Right Pan
        val rightPanPath = android.graphics.Path().apply {
            moveTo(cx + 50f, sY - 15f)
            lineTo(cx + 40f, sY + 10f)
            lineTo(cx + 60f, sY + 10f)
            close()
        }
        canvas.drawPath(rightPanPath, thinCirclePaint)
        canvas.drawLine(cx + 36f, sY + 10f, cx + 64f, sY + 10f, circlePaint)

        // Draw Microscope silhouette in middle
        val microPath = android.graphics.Path().apply {
            moveTo(cx - 3f, sY - 10f)
            lineTo(cx + 3f, sY - 10f)
            lineTo(cx + 3f, sY - 3f)
            lineTo(cx - 3f, sY - 3f)
            close()
        }
        canvas.drawPath(microPath, redPaint)

        val microscopeArm = android.graphics.Path().apply {
            moveTo(cx - 5f, sY - 3f)
            quadTo(cx - 12f, sY + 2f, cx - 8f, sY + 15f)
            lineTo(cx - 2f, sY + 15f)
            quadTo(cx - 6f, sY + 5f, cx - 2f, sY - 3f)
            close()
        }
        canvas.drawPath(microscopeArm, redPaint)
        canvas.drawCircle(cx, cy + 20f, 6f, redPaint) // base/knob

        // Left/Right DNA Spiral Waves (using Sine/Cosine curves!)
        val leftDna = android.graphics.Path()
        val rightDna = android.graphics.Path()
        for (y in -80..80 step 4) {
            val radians = (y / 80f) * (2f * Math.PI.toFloat())
            val dx1 = (Math.sin(radians.toDouble()) * 10f).toFloat()
            val dx2 = (-Math.sin(radians.toDouble()) * 10f).toFloat()
            
            // Left DNA strand centers around cx - 95
            if (y == -80) {
                leftDna.moveTo(cx - 95f + dx1, cy + y)
            } else {
                leftDna.lineTo(cx - 95f + dx1, cy + y)
            }
            
            // Right DNA strand centers around cx + 95
            if (y == -80) {
                rightDna.moveTo(cx + 95f + dx2, cy + y)
            } else {
                rightDna.lineTo(cx + 95f + dx2, cy + y)
            }

            // Connector link bars
            if (y % 16 == 0) {
                canvas.drawLine(cx - 95f + dx1, cy + y, cx - 95f + dx2, cy + y, thinCirclePaint)
                canvas.drawLine(cx + 95f + dx1, cy + y, cx + 95f + dx2, cy + y, thinCirclePaint)
            }
        }
        canvas.drawPath(leftDna, bluePaint)
        canvas.drawPath(rightDna, bluePaint)

        // Curved Crimson Ribbon Banner at the bottom of watermark Circle
        val ribbonPath = android.graphics.Path().apply {
            moveTo(cx - 80f, cy + 85f)
            quadTo(cx, cy + 100f, cx + 80f, cy + 85f)
            lineTo(cx + 84f, cy + 96f)
            quadTo(cx, cy + 112f, cx - 84f, cy + 96f)
            close()
        }
        canvas.drawPath(ribbonPath, redPaint)

        // Watermark Texts
        canvas.drawText("DIRECTORATE OF FORENSIC SCIENCE", cx, cy - 118f, fslTextPaint)
        canvas.drawText("RAJASTHAN", cx, cy - 108f, fslTextPaint)
        canvas.drawText("न्यायार्थे विज्ञानम्", cx, cy + 96f, fslTextPaint)
        
        val fslSubPaint = Paint().apply {
            color = Color.argb(16, 30, 61, 89)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("FSL", cx, cy + 124f, fslSubPaint)
    }

    private fun splitJourneyTimes(depTime: String, arrTime: String, distance: Double): Pair<String, String> {
        return try {
            val depParts = depTime.split(":")
            val arrParts = arrTime.split(":")
            val depMins = depParts[0].trim().toInt() * 60 + depParts[1].trim().toInt()
            val arrMins = arrParts[0].trim().toInt() * 60 + arrParts[1].trim().toInt()
            
            val totalMins = if (arrMins >= depMins) arrMins - depMins else (1440 - depMins) + arrMins
            
            // Assume 40 km/h average speed. Outward trip is half distance.
            val halfDist = distance / 2.0
            var travelMins = ((halfDist / 40.0) * 60.0).toInt()
            
            // Keep travelMins reasonable, say between 15 and 40% of total trip time.
            if (totalMins > 40) {
                travelMins = travelMins.coerceIn(15, (totalMins * 0.4).toInt())
            } else {
                travelMins = (totalMins * 0.3).toInt().coerceAtLeast(10)
            }
            
            val outwardArrMins = (depMins + travelMins) % 1440
            val returnDepMins = (arrMins - travelMins + 1440) % 1440
            
            val outH = outwardArrMins / 60
            val outM = outwardArrMins % 60
            val retH = returnDepMins / 60
            val retM = returnDepMins % 60
            
            val outStr = String.format(Locale.getDefault(), "%02d:%02d", outH, outM)
            val retStr = String.format(Locale.getDefault(), "%02d:%02d", retH, retM)
            Pair(outStr, retStr)
        } catch (e: Exception) {
            Pair("12:00", "15:00")
        }
    }

    /**
     * Generates a Rajasthan Civil Service road travel & daily allowance claim ledger PDF.
     */
    fun generateTaBillPdf(
        context: Context,
        profile: EmployeeProfile,
        entries: List<TourEntry>,
        monthYear: String,
        isLegalSize: Boolean
    ): File? {
        val pdfDocument = PdfDocument()

        val pageWidth = 612
        val pageHeight = if (isLegalSize) 1008 else 792

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Paints
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.BLACK
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }

        val secondaryLinePaint = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }

        val footerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val leftMargin = 20f
        val rightMargin = pageWidth - leftMargin
        val topMargin = 25f

        // Draw Title
        var yPos = topMargin + 15
        val displayTitleEn = "RAJASTHAN SERVICE RULES - TRAVEL & DAILY ALLOWANCE CLAIM LEDGER"
        canvas.drawText(displayTitleEn, pageWidth / 2f, yPos, titlePaint)

        // Draw Meta Info
        yPos += 22
        val nameLabel = "Name: "
        val nameVal = profile.name.ifBlank { "____________" }
        canvas.drawText(nameLabel + nameVal, leftMargin, yPos, metaPaint)

        val desLabel = "Designation: "
        val desVal = profile.designation.ifBlank { "____________" }
        val desWidth = metaPaint.measureText(nameLabel + nameVal) + 40f
        canvas.drawText(desLabel + desVal, leftMargin + desWidth, yPos, metaPaint)

        val postingLabel = "HQ/Unit: "
        val postingVal = profile.posting.ifBlank { "____________" }
        val postingWidth = desWidth + metaPaint.measureText(desLabel + desVal) + 40f
        canvas.drawText(postingLabel + postingVal, leftMargin + postingWidth, yPos, metaPaint)

        // Basic Salary and TA Category Info under Rajasthan Rules
        yPos += 15
        val payLabel = "Basic Pay: ₹${profile.basicSalary} "
        val catLabel = "TA Category: ${profile.taCategory}"
        canvas.drawText(payLabel + " | " + catLabel, leftMargin, yPos, metaPaint)

        // Month Indicator
        val formattedMonth = try {
            val parser = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val formatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val date = parser.parse(monthYear)
            if (date != null) formatter.format(date) else monthYear
        } catch (e: Exception) {
            monthYear
        }
        val monthWidth = rightMargin - metaPaint.measureText("Month of Claim: $formattedMonth")
        canvas.drawText("Month of Claim: $formattedMonth", monthWidth, yPos, metaPaint)

        // Draw Watermark
        val xWatermark = pageWidth / 2f
        val yWatermark = (topMargin + pageHeight - 50f) / 2f + 10f
        drawFslWatermark(canvas, xWatermark, yWatermark)

        // --- Draw Table Grid ---
        yPos += 15
        val tableTop = yPos
        val colWidths = floatArrayOf(20f, 40f, 85f, 75f, 50f, 50f, 35f, 60f, 87f, 70f)
        
        // Cumulative coordinates
        val xCoords = FloatArray(11)
        xCoords[0] = leftMargin
        for (i in 1..10) {
            xCoords[i] = xCoords[i - 1] + colWidths[i - 1]
        }

        val headerRowHeight = 22f
        val tableHeaderBottom = tableTop + headerRowHeight

        // Background / borders of table headers
        canvas.drawRect(leftMargin, tableTop, rightMargin, tableHeaderBottom, linePaint)

        // Draw Header Vertical Lines & Text
        fun drawHeaderCell(xStart: Float, xEnd: Float, text: String, yCenter: Float) {
            val xCenter = (xStart + xEnd) / 2f
            canvas.drawText(text, xCenter, yCenter + 2.5f, headerPaint)
        }

        drawHeaderCell(xCoords[0], xCoords[1], "S.No", tableTop + headerRowHeight / 2)
        canvas.drawLine(xCoords[1], tableTop, xCoords[1], tableHeaderBottom, linePaint)

        drawHeaderCell(xCoords[1], xCoords[2], "Date", tableTop + headerRowHeight / 2)
        canvas.drawLine(xCoords[2], tableTop, xCoords[2], tableHeaderBottom, linePaint)

        drawHeaderCell(xCoords[2], xCoords[3], "Journey Route/Details", tableTop + headerRowHeight / 2)
        canvas.drawLine(xCoords[3], tableTop, xCoords[3], tableHeaderBottom, linePaint)

        drawHeaderCell(xCoords[3], xCoords[4], "Reason of Visit", tableTop + headerRowHeight / 2)
        canvas.drawLine(xCoords[4], tableTop, xCoords[4], tableHeaderBottom, linePaint)

        drawHeaderCell(xCoords[4], xCoords[5], "Dep-Arr Time", tableTop + headerRowHeight / 2)
        canvas.drawLine(xCoords[5], tableTop, xCoords[5], tableHeaderBottom, linePaint)

        drawHeaderCell(xCoords[5], xCoords[6], "Mode", tableTop + headerRowHeight / 2)
        canvas.drawLine(xCoords[6], tableTop, xCoords[6], tableHeaderBottom, linePaint)

        drawHeaderCell(xCoords[6], xCoords[7], "Dist (km)", tableTop + headerRowHeight / 1.5f)
        canvas.drawLine(xCoords[7], tableTop, xCoords[7], tableHeaderBottom, linePaint)

        drawHeaderCell(xCoords[7], xCoords[8], "Fare & TA (₹)", tableTop + headerRowHeight / 2)
        canvas.drawLine(xCoords[8], tableTop, xCoords[8], tableHeaderBottom, linePaint)

        drawHeaderCell(xCoords[8], xCoords[9], "DA Claim Details", tableTop + headerRowHeight / 2)
        canvas.drawLine(xCoords[9], tableTop, xCoords[9], tableHeaderBottom, linePaint)

        drawHeaderCell(xCoords[9], xCoords[10], "Total Claim (₹)", tableTop + headerRowHeight / 2)

        // --- Draw Table rows dynamically ---
        var currentY = tableHeaderBottom
        val maxAvailableHeight = pageHeight - 95f // Reserve points for certificate & signatures
        
        val totalLegsCount = entries.size * 2
        val targetRowHeight = if (entries.isNotEmpty()) {
            val remainingHeight = maxAvailableHeight - tableHeaderBottom
            val calculated = remainingHeight / (totalLegsCount + 1) // +1 for the total row
            calculated.coerceIn(16f, 28f)
        } else {
            20f
        }

        var totalTaClaim = 0.0
        var totalDaClaim = 0.0

        val sortedEntriesForTa = entries.sortedWith(compareBy<TourEntry> { it.date }.thenBy { it.depTime })

        sortedEntriesForTa.forEachIndexed { sNoIdx, entry ->
            val hqName = profile.posting.ifBlank { "HQ" }
            val rawPsList = entry.policeStation.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            val psName = if (rawPsList.size > 1) {
                rawPsList.joinToString(" & ")
            } else {
                entry.policeStation.trim()
            }
            
            // Calculate intermediate arrival at destination and return departure
            val (outwardArrTime, returnDepTime) = splitJourneyTimes(entry.depTime, entry.arrTime, entry.distance)
            
            val (taRow, daRow) = RajasthanTaRules.calculateTripAllowance(profile, entry)
            totalTaClaim += taRow
            totalDaClaim += daRow
            
            val halfDist = entry.distance / 2.0
            val halfTa = taRow / 2.0
            val isGovt = entry.travelMode.lowercase(Locale.getDefault()).contains("govt")
            val modelRate = RajasthanTaRules.getMileageRate(entry.travelMode)
            
            // Setup local data class and two legs: Outward and Return
            data class LegData(val route: String, val reason: String, val time: String, val ta: Double)
            val legs = listOf(
                // Outward Leg
                LegData(
                    route = "$hqName to $psName",
                    reason = "Crime Scene Visit\nPS $psName",
                    time = "${entry.depTime}-$outwardArrTime",
                    ta = halfTa
                ),
                // Return Leg
                LegData(
                    route = "$psName to $hqName",
                    reason = "Return back to\n$hqName",
                    time = "$returnDepTime-${entry.arrTime}",
                    ta = halfTa
                )
            )
            
            legs.forEachIndexed { legIdx, leg ->
                val nextY = currentY + targetRowHeight
                // Draw horizontal border
                canvas.drawLine(leftMargin, nextY, rightMargin, nextY, secondaryLinePaint)

                val yRowCenter = currentY + (targetRowHeight / 2f)

                // Draw Cells
                fun drawCellText(text: String, xStart: Float, xEnd: Float, align: Paint.Align = Paint.Align.CENTER) {
                    val textPaintToUse = Paint(textPaint).apply {
                        textAlign = align
                    }
                    val xPosCell = when (align) {
                        Paint.Align.CENTER -> (xStart + xEnd) / 2f
                        Paint.Align.LEFT -> xStart + 3f
                        Paint.Align.RIGHT -> xEnd - 3f
                    }
                    val lines = text.split("\n")
                    if (lines.size > 1) {
                        val lineSpacing = 6.2f
                        val totalHeight = (lines.size - 1) * lineSpacing
                        val startYOffset = -totalHeight / 2f
                        lines.forEachIndexed { idx, line ->
                            val lineY = yRowCenter + startYOffset + (idx * lineSpacing) + 2f
                            var displayLine = line
                            val availableWidth = (xEnd - xStart) - 4f
                            if (textPaintToUse.measureText(line) > availableWidth) {
                                var length = line.length
                                while (length > 0 && textPaintToUse.measureText(line.substring(0, length) + "...") > availableWidth) {
                                    length--
                                }
                                displayLine = if (length > 0) line.substring(0, length) + "..." else "..."
                            }
                            canvas.drawText(displayLine, xPosCell, lineY, textPaintToUse)
                        }
                    } else {
                        // Clip if text is too wide
                        var displayWord = text
                        val availableWidth = (xEnd - xStart) - 4f
                        if (textPaintToUse.measureText(text) > availableWidth) {
                            var length = text.length
                            while (length > 0 && textPaintToUse.measureText(text.substring(0, length) + "...") > availableWidth) {
                                length--
                            }
                            displayWord = if (length > 0) text.substring(0, length) + "..." else "..."
                        }
                        canvas.drawText(displayWord, xPosCell, yRowCenter + 2f, textPaintToUse)
                    }
                }

                // 1. S.No
                val sNoStr = if (legIdx == 0) "${sNoIdx + 1}(a)" else "${sNoIdx + 1}(b)"
                drawCellText(sNoStr, xCoords[0], xCoords[1])
                canvas.drawLine(xCoords[1], currentY, xCoords[1], nextY, linePaint)

                // 2. Date
                val legDate = if (legIdx == 0) entry.date else entry.arrDate.ifBlank { entry.date }
                drawCellText(formatDate(legDate), xCoords[1], xCoords[2])
                canvas.drawLine(xCoords[2], currentY, xCoords[2], nextY, linePaint)

                // 3. Journey Route / Details
                drawCellText(leg.route, xCoords[2], xCoords[3], Paint.Align.LEFT)
                canvas.drawLine(xCoords[3], currentY, xCoords[3], nextY, linePaint)

                // 4. Reason of Visit
                drawCellText(leg.reason, xCoords[3], xCoords[4], Paint.Align.LEFT)
                canvas.drawLine(xCoords[4], currentY, xCoords[4], nextY, linePaint)

                // 5. Dep-Arr Time
                drawCellText(leg.time, xCoords[4], xCoords[5])
                canvas.drawLine(xCoords[5], currentY, xCoords[5], nextY, linePaint)

                // 6. Travel Mode
                drawCellText(entry.travelMode, xCoords[5], xCoords[6])
                canvas.drawLine(xCoords[6], currentY, xCoords[6], nextY, linePaint)

                // 7. Distance
                val distText = if (halfDist > 0.0) "${String.format(Locale.US, "%.1f", halfDist)} km" else "0 km"
                drawCellText(distText, xCoords[6], xCoords[7])
                canvas.drawLine(xCoords[7], currentY, xCoords[7], nextY, linePaint)

                // 8. Fare & TA (Rate section removed as requested)
                val taText = if (isGovt) "Govt. (₹0)" else "₹${leg.ta.toInt()}"
                drawCellText(taText, xCoords[7], xCoords[8])
                canvas.drawLine(xCoords[8], currentY, xCoords[8], nextY, linePaint)

                // 9. DA Claim
                val daText = if (legIdx == 1) {
                    val daPct = (RajasthanTaRules.getDaPercentage(entry.depTime, entry.arrTime) * 100).toInt()
                    "₹${daRow.toInt()} ($daPct% DA)"
                } else {
                    "₹0 (Outward)"
                }
                drawCellText(daText, xCoords[8], xCoords[9])
                canvas.drawLine(xCoords[9], currentY, xCoords[9], nextY, linePaint)

                // 10. Row Total
                val legTotal = leg.ta + (if (legIdx == 1) daRow else 0.0)
                drawCellText("₹${legTotal.toInt()}", xCoords[9], xCoords[10], Paint.Align.RIGHT)

                currentY = nextY
            }
        }

        // Draw Total Summary Row
        val totalRowY = currentY + targetRowHeight
        canvas.drawRect(leftMargin, currentY, rightMargin, totalRowY, linePaint)
        
        // Horizontal line under totals
        canvas.drawLine(leftMargin, totalRowY, rightMargin, totalRowY, linePaint)

        // S.No vertical boundary
        canvas.drawLine(xCoords[1], currentY, xCoords[1], totalRowY, linePaint)
        canvas.drawLine(xCoords[7], currentY, xCoords[7], totalRowY, linePaint)
        canvas.drawLine(xCoords[8], currentY, xCoords[8], totalRowY, linePaint)
        canvas.drawLine(xCoords[9], currentY, xCoords[9], totalRowY, linePaint)

        val totalYCenter = currentY + (targetRowHeight / 2f)
        val summaryTitlePaint = Paint(headerPaint).apply {
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("TOTAL CLAIM:", xCoords[7] - 5f, totalYCenter + 2.5f, summaryTitlePaint)

        // Draw total distance / TA and DA and grand total
        val grandTotal = totalTaClaim + totalDaClaim
        canvas.drawText("₹${totalTaClaim.toInt()}", (xCoords[7] + xCoords[8]) / 2f, totalYCenter + 2f, headerPaint)
        canvas.drawText("₹${totalDaClaim.toInt()}", (xCoords[8] + xCoords[9]) / 2f, totalYCenter + 2f, headerPaint)
        
        val grandTotalTextPaint = Paint(headerPaint).apply {
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("₹${grandTotal.toInt()}", xCoords[10] - 3f, totalYCenter + 2f, grandTotalTextPaint)

        // Add Certificate and Signature space
        val certY = totalRowY + 16f
        val certPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("CERTIFICATE: Certified that all the road travels and claims are statutory and compliant with Rajasthan Travelling Allowance Rules.", leftMargin, certY, certPaint)

        val sigY = certY + 18f
        val finalY = minOf(sigY, pageHeight - 50f)
        canvas.drawLine(rightMargin - 140f, finalY, rightMargin, finalY, secondaryLinePaint)
        canvas.drawText("Claimant Signature", rightMargin - 120f, finalY + 10f, footerPaint)
        canvas.drawText("${profile.name} (${profile.designation})", rightMargin - 130f, finalY + 20f, metaPaint)

        pdfDocument.finishPage(page)

        val outputDir = context.cacheDir
        val file = File(outputDir, "Rajasthan_TA_Bill_${monthYear}.pdf")
        return try {
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}
