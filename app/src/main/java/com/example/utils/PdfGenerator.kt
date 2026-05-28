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
}
