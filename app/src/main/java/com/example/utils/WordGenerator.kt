package com.example.utils

import android.content.Context
import com.example.data.EmployeeProfile
import com.example.data.TourEntry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

object WordGenerator {

    /**
     * Generates a monthly tour diary Word document (.doc) in a highly styled
     * MS Word-compatible HTML format and returns the generated File target.
     */
    fun generateMonthlyDiaryDoc(
        context: Context,
        profile: EmployeeProfile,
        entries: List<TourEntry>,
        monthYear: String
    ): File? {
        val fileName = "Tour_Diary_${monthYear}.doc"
        val file = File(context.cacheDir, fileName)

        val formattedMonth = try {
            val parser = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val formatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val date = parser.parse(monthYear)
            if (date != null) formatter.format(date) else monthYear
        } catch (e: Exception) {
            monthYear
        }

        val nameVal = profile.name.ifBlank { "N/A" }
        val desVal = profile.designation.ifBlank { "N/A" }
        val postingVal = profile.posting.ifBlank { "N/A" }

        val sorted = entries.sortedWith(compareBy<TourEntry> { it.date }.thenBy { it.depTime })

        val htmlContent = StringBuilder()
        htmlContent.append("""
            <html xmlns:o='urn:schemas-microsoft-com:office:office' 
                  xmlns:w='urn:schemas-microsoft-com:office:word' 
                  xmlns='http://www.w3.org/TR/REC-html40'>
            <head>
            <meta charset="utf-8">
            <title>Monthly Tour Diary</title>
            <!--[if gte mso 9]>
            <xml>
             <w:WordDocument>
              <w:View>Print</w:View>
              <w:Zoom>100</w:Zoom>
              <w:DoNotOptimizeForBrowser/>
             </w:WordDocument>
            </xml>
            <![endif]-->
            <style>
            @page {
                size: A4 landscape;
                margin: 0.5in 0.5in 0.5in 0.5in;
                mso-header-margin: 0.5in;
                mso-footer-margin: 0.5in;
            }
            body {
                font-family: 'Arial', sans-serif;
                font-size: 10pt;
                line-height: 1.2;
                color: #000000;
            }
            .header-container {
                width: 100%;
                text-align: center;
                margin-bottom: 20px;
            }
            .header-title {
                font-size: 13pt;
                font-weight: bold;
                text-transform: uppercase;
                margin-bottom: 15px;
                color: #000000;
            }
            .meta-table {
                width: 100%;
                border-collapse: collapse;
                margin-bottom: 15px;
            }
            .meta-table td {
                border: none !important;
                padding: 4px 0;
                font-size: 10pt;
                vertical-align: middle;
            }
            .meta-label {
                font-weight: normal;
                color: #333333;
            }
            .meta-value {
                font-weight: bold;
                border-bottom: 1px dotted #000000;
                padding-right: 15px;
            }
            .diary-table {
                width: 100%;
                border-collapse: collapse;
                margin-top: 10px;
            }
            .diary-table th {
                border: 1px solid #000000 !important;
                padding: 6px;
                font-size: 9.5pt;
                font-weight: bold;
                background-color: #F2EEF9;
                text-align: center;
                vertical-align: middle;
            }
            .diary-table td {
                border: 1px solid #000000 !important;
                padding: 6px;
                font-size: 9pt;
                text-align: center;
                vertical-align: middle;
            }
            .footer-section {
                width: 100%;
                margin-top: 40px;
            }
            .signature-cell {
                text-align: right;
                font-weight: bold;
                font-size: 10pt;
                padding-top: 30px;
            }
            </style>
            </head>
            <body>
            
            <div class="header-container">
                <div class="header-title">DETAILS OF TRAVELS MADE FOR INSPECTION OF CRIME SCENES</div>
            </div>
            
            <table class="meta-table">
                <tr>
                    <td width="33%"><span class="meta-label">Name:</span> <span class="meta-value">$nameVal</span></td>
                    <td width="33%"><span class="meta-label">Designation:</span> <span class="meta-value">$desVal</span></td>
                    <td width="34%"><span class="meta-label">Mobile Forensic Unit:</span> <span class="meta-value">$postingVal</span></td>
                </tr>
                <tr>
                    <td colspan="3" style="padding-top: 8px;"><span class="meta-label">Month:</span> <span class="meta-value">$formattedMonth</span></td>
                </tr>
            </table>

            <table class="diary-table">
                <thead>
                    <tr>
                        <th rowspan="2" width="4%">S.No</th>
                        <th rowspan="2" width="8%">Date</th>
                        <th colspan="2" width="16%">Travel Details</th>
                        <th rowspan="2" width="10%">Mode</th>
                        <th rowspan="2" width="7%">Dist<br>(km)</th>
                        <th rowspan="2" width="12%">C.S. No.</th>
                        <th colspan="3" width="35%">Details of Crime / Purpose of Travel</th>
                        <th rowspan="2" width="8%">Rep. Date</th>
                    </tr>
                    <tr>
                        <th>Departure</th>
                        <th>Arrival</th>
                        <th>FIR No.</th>
                        <th>Police Station</th>
                        <th>District</th>
                    </tr>
                </thead>
                <tbody>
        """.trimIndent())

        if (sorted.isEmpty()) {
            htmlContent.append("""
                <tr>
                    <td colspan="11" style="height: 60px; text-align: center; color: #666666;">No tour entries found for this month.</td>
                </tr>
            """.trimIndent())
        } else {
            sorted.forEachIndexed { index, entry ->
                val compactDate = formatDate(entry.date)
                val formatArr = if (entry.arrDate.isNotBlank() && entry.arrDate != entry.date) {
                    formatDate(entry.arrDate) + " " + entry.arrTime
                } else {
                    entry.arrTime
                }
                val rDate = if (entry.reportDate.isNotBlank()) formatDate(entry.reportDate) else "-"

                htmlContent.append("""
                    <tr>
                        <td style="font-weight: bold;">${index + 1}</td>
                        <td>$compactDate</td>
                        <td>${entry.depTime}</td>
                        <td>$formatArr</td>
                        <td>${entry.travelMode}</td>
                        <td>${String.format(Locale.getDefault(), "%.1f", entry.distance)}</td>
                        <td style="text-align: left; white-space: pre-wrap;">${entry.csNumber.replace("\n", "<br>")}</td>
                        <td style="text-align: left; white-space: pre-wrap;">${entry.firNumber.replace("\n", "<br>")}</td>
                        <td style="text-align: left; white-space: pre-wrap;">${entry.policeStation.replace("\n", "<br>")}</td>
                        <td style="text-align: left; white-space: pre-wrap;">${entry.district.replace("\n", "<br>")}</td>
                        <td>$rDate</td>
                    </tr>
                """.trimIndent())
            }
        }

        htmlContent.append("""
                </tbody>
            </table>
            
            <table class="footer-section" border="0" cellspacing="0" cellpadding="0">
                <tr>
                    <td width="50%">&nbsp;</td>
                    <td class="signature-cell" width="50%">
                        Signature of Officer<br>
                        <span style="font-size: 8.5pt; font-weight: normal; color: #555555;">$desVal</span>
                    </td>
                </tr>
            </table>

            </body>
            </html>
        """.trimIndent())

        return try {
            val fos = FileOutputStream(file)
            fos.write(htmlContent.toString().toByteArray(Charsets.UTF_8))
            fos.flush()
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formatter = SimpleDateFormat("dd/MM", Locale.getDefault())
            val parsed = parser.parse(dateStr)
            if (parsed != null) formatter.format(parsed) else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }
}
