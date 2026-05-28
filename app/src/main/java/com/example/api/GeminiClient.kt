package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String // Base64
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content
)

@JsonClass(generateAdapter = true)
data class TourEntryOcrResult(
    val date: String? = "",            // YYYY-MM-DD
    val depTime: String? = "",         // HH:MM
    val arrTime: String? = "",         // HH:MM
    val travelMode: String? = "",      // e.g. "MT Vehicle"
    val distance: Double? = 0.0,       // in km
    val csNumber: String? = "",        // Crime Scene Number
    val firNumber: String? = "",       // F.I.R. Number
    val policeStation: String? = "",   // PS name
    val district: String? = "",        // District
    val reportDate: String? = ""       // YYYY-MM-DD
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    /**
     * Parse the document image into a structured TourEntryOcrResult using the Gemini 3.5 Flash API.
     */
    suspend fun parseDocument(base64Image: String, mimeType: String): TourEntryOcrResult? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API Key is not configured. Please configure it in your AI Studio Secrets panel.")
        }

        val prompt = """
            You are an expert OCR and data extraction system for forensic travel, cases, and vehicle log records.
            Analyze the attached document (which can be a case requisition letter, a case register table, a vehicle LOG BOOK table, or a mobile FSL unit report letter).
            
            MOBILE FORENSIC UNIT / CASE REQUISITION LETTERS SPECIFICS:
            If the uploaded document is an official letter/report header (e.g., containing "मोबाइल फॉरेंसिक यूनिट" or "घटनास्थल निरीक्षण रिपोर्ट"):
            - Look for CS Number / Crime Scene investigation number in the reference number (क्रमांक), which usually starts with "FSL/.../CS .../2025/..." or "CS .../2025". Convert this reference into a clean representation like 'Sikar CS 129/25' or 'Sikar CS 129/2025' depending on the location and year. For example, 'FSL/DMFU/SIKAR/CS 129/2025/ spl-1' MUST be mapped to "Sikar CS 129/25" under 'csNumber'.
            - Look for the Police Station (पुलिस थाना) and district mentioned. For example, "पुलिस थाना धोद" means the 'policeStation' is "Dhod" or "PS Dhod", and "जिला सीकर" means the 'district' is "Sikar".
            - Look for the F.I.R. / Case number (विषय : अ.स. 293/25) or (अ.स. 293/25). This is the FIR number. Map this to 'firNumber' as "293/25" or "293/2025" under 'firNumber'.
            - Look for the report/inspection date (दिनांक 12-12-2025) or (12-12-25). This must be converted to YYYY-MM-DD, e.g., "2025-12-12", and mapped to both 'date' and 'reportDate'.
            - If departure/arrival times are mentioned or can be estimated: Look for phrases like "11.56 एएम बजे" or "09:10 एएम बजे". For departure 'depTime', map "09:10" or "11:56" (rounded appropriately to 24-hour HH:MM format). If not listed, estimate e.g. "11:56" for departure and e.g., "14:30" or similar for return/arrival to make the tour diary complete.
            - If distance is not explicitly written (e.g., 0.0), estimate a realistic round-trip travel distance in kilometers (e.g., 30.0 km) for traveling from Sikar to Dhod/Gunathu and back.

            VEHICLE LOG BOOK SPECIFICS:
            If the uploaded document is a vehicle log book with tabular fields, it typically contains columns similar to:
            - Date (e.g. 08/05/26, 12/08/26, 13/08/26, 15/08/26) in DD/MM/YY format. Convert this to YYYY-MM-DD. For example:
              * '08/05/26' should be parsed as May 8, 2026, so return '2026-05-08'.
              * '12/08/26' should be parsed as August 12, 2026, so return '2026-08-12'.
              * '13/08/26' should be parsed as August 13, 2026, so return '2026-08-13'.
              * '15/08/26' should be parsed as August 15, 2026, so return '2026-08-15'.
            - Time (Out / In): Out time represents departure time (depTime), and In time represents arrival time (arrTime).
              * Convert these to 24-hour HH:MM format (e.g., '8:45 AM', '845 am' or '8.45 am' becomes '08:45'; '10:00 PM', '1000 pm' or '10.00 pm' becomes '22:00').
              * If times are written as e.g. '8:00 AM' / '9:30 PM', they are '08:00' / '21:30' respectively.
            - From / To: The journey routes.
            - Milo Meter (Out / In) and 'Total Run' / 'Run':
              * The 'Total Run' column represents the net distance traveled in kilometers. Extract this number as "distance" (e.g., '154', '250', '103', '101' should be returned as double float e.g., 154.0, 250.0, 103.0, 101.0).
            - Purpose of Journey: Look for mentioned police stations (e.g., 'P.S. रींगस' or 'PS रींगस' means policeStation is 'Reengus / रींगस' or 'Reengus'), districts, or Case/FIR references (e.g., 'अप स 160/26 P.S. रींगस' means firNumber is '160/2026').

            HANDWRITTEN CRIME SCENE REGISTER / TOUR DIARY SPECIFICS:
            If the uploaded document is a handwritten table on lined or checkered paper representing crime scene investigations, it typically has the following columns:
            - S.N.: Serial number (e.g., 01, 02, 03, 04, 05, 06, 07, 08)
            - C.S.: Crime Scene Investigation number (e.g., 64, 65, 66, 67, 68, 69, 70, 71)
            - Informer D & T: Hand-written dates and times (e.g., '01/09/2025 07:28 Am' or '15/09/25 10:00 pm'). Convert the date portion to YYYY-MM-DD. E.g. '15/09/2025' or '15/09/25' becomes '2025-09-15'.
            - PS/CS: The location or police station. Extract the name of the Police Station (which is usually the main text preceding any Hindi keywords in parantheses, e.g. 'Udaipurwati' from 'Udaipurwati (बीरूवाला कुआ)', 'Bagar' from 'Bagar (कस्बा बगड)', 'Pilani' from 'Pilani', 'Singhana' from 'Singhana', 'Gudhagaudji' from 'Gudhagaudji', 'Pacheri Kalan' from 'Pacheri Kalan', 'Khetri nagar' from 'Khetri nagar').
            - TOD: Time of Departure. (e.g., '16/09/25 09:00 Am' or '04:25 pm' or '09:30 Am'). Extract the time and convert to 24-hour HH:MM format (e.g., '09:00 Am' -> '09:00', '04:25 pm' -> '16:25', '12:30 Pm' -> '12:30', '10:00 pm' -> '22:00').
            - TOR: Time of Return/Arrival. Extract the time and convert to 24-hour HH:MM format (e.g., '09:30 pm' -> '21:30', '08:25 Am' -> '08:25', '11:15 pm' -> '23:15').
            - FIR: Case / FIR references written on the right margin (e.g. 'अ.स. 195/25' means FIR is '195/2025' or '195/25', 'अ.सं. 231/25' means '231/2025' or '231/25').

            ROW SELECTION RULE:
            If there are multiple rows/entries recorded on a single log page, analyze the entire table but extract and populate the JSON with fields from the LAST valid completed horizontal row of the table (as it represents the latest/most recent trip or crime scene investigation record the officer is adding to their tour diary).
            For example, in a table with 8 rows where row 8 is '08. | 71. | PCR JJN 15/09/2025 10:00 pm | Khetri nagar (बागास) | 16/09/25 09:00 Am | 16/09/25 10:00 pm', return:
            * date: '2025-09-15' or '2025-09-16' (use date from the last row)
            * depTime: '09:00'
            * arrTime: '22:00'
            * travelMode: 'MT Vehicle (Govt.)' (default or inferred)
            * distance: 0.0 (or estimate map distance if mentioned, else default 0.0)
            * csNumber: '71/2025'
            * policeStation: 'Khetri nagar'
            * district: 'Jhunjhunu' (inferred from PCR JJN)
            * reportDate: '2025-09-15'

            Extract and return a single valid JSON object representing the travel/case entry with exactly the following keys (all keys are required, even if there is an empty string):
            
            {
              "date": "YYYY-MM-DD",  // Date of scene visit or travel. Look for dates like 'दिनांक' or the log book Date column or last row's date. Always convert to YYYY-MM-DD format!
              "depTime": "HH:MM",     // Departure time (Time Out) in 24-hour format (e.g. '10:30', '09:00', '15:15'). If not found, use "".
              "arrTime": "HH:MM",     // Arrival time (Time In) in 24-hour format. if not found, use "".
              "travelMode": "string", // Mode of travel, e.g., 'MT Vehicle' (एम.टी. वाहन), 'Govt. Vehicle' (राजकीय वाहन), 'Official Vehicle', 'Bus'. If not found, default to 'MT Vehicle'.
              "distance": 0.0,       // Total round-trip distance traveled in kilometers as a float/double (e.g. 50.0). Look for 'Total Run' or distance. If not found, use 0.0.
              "csNumber": "string",   // Crime scene inspection serial number or C.S. No., CS Number (e.g. '75/2026' or '71/2025').
              "firNumber": "string",  // FIR number / Cases details (e.g. '123/2026', '54/2026').
              "policeStation": "string", // Name of the Police Station / थाना / P.S. (e.g. 'Shastri Nagar', 'Kotwali').
              "district": "string",   // Name of the related district / जिला (e.g. 'Jodhpur', 'Jaipur').
              "reportDate": "YYYY-MM-DD" // Date the report was filed or dated on the requisition letter. If not found, use the same as "date".
            }
            
            Strictly return ONLY the raw JSON block without markdown formatting or code blocks. Do not include any trailing commas or invalid JSON tokens.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = mimeType, data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        val response = apiService.generateContent(apiKey, request)
        val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: return null

        // Parse JSON response using Moshi
        return try {
            val adapter = moshi.adapter(TourEntryOcrResult::class.java)
            // Trim any markdown block code wrappers if Gemini returned them despite instruction
            val cleanedJson = jsonText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            adapter.fromJson(cleanedJson)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parse the crime scene register document into a structured CaseEntryOcrResult.
     */
    suspend fun parseCrimeSceneRegister(base64Image: String, mimeType: String): CaseEntryOcrResult? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API Key is not configured. Please configure it in your Secrets panel.")
        }

        val prompt = """
            You are an expert OCR and data extraction system for police crime scene register logs, registers, and forensic logs.
            Analyze this uploaded document containing details of a crime scene investigation or register entry.
            Extract and return a single valid JSON object representing the case/crime scene profile with exactly the following keys (all keys are required, even if empty string):
            
            {
              "caseNumber": "string",        // Crime Scene Number (C.S. No) or Case serial number (e.g. 'CS-101/2026').
              "firNumber": "string",         // F.I.R. Number (e.g. '24/2026').
              "policeStation": "string",     // Name of the Police Station / थाना / P.S. (e.g. 'Sajjangarh', 'Sadar').
              "district": "string",          // District name / जिला (e.g. 'Udaipur').
              "date": "YYYY-MM-DD",          // Date recorded or date of crime scene visit.
              "tags": "string",              // Comma-separated forensic and case keywords relevant to this case (e.g. 'Homicide, Forensic, Fingerprints, Theft').
              "notes": "string",             // A concise summary or details/history of the crime scene drawn from the document text.
              "investigatingOfficer": "string", // Investigating Officer (I.O.) name or forensic expert name if shown.
              "status": "string"             // Status e.g. "Under Investigation", "Active", "Pending", or "Completed".
            }
            
            Strictly return ONLY the raw JSON block without markdown formatting or code blocks.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = mimeType, data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        val response = apiService.generateContent(apiKey, request)
        val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: return null

        return try {
            val adapter = moshi.adapter(CaseEntryOcrResult::class.java)
            val cleanedJson = jsonText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            adapter.fromJson(cleanedJson)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parse the handwritten note / scribble drawing directly from screen and convert into structured notes text.
     */
    suspend fun transcribeScribbleDrawing(base64Image: String): String? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return null
        }

        val prompt = "This is a freehand handwritten sketch or note drawn directly on a mobile screen during a crime scene investigation. Analyze the handwriting and drawings, transcribe any visible text, and summarize what is shown or written. Provide a neat, typed summary of these notes."

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.2f
            )
        )

        return try {
            val response = apiService.generateContent(apiKey, request)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@JsonClass(generateAdapter = true)
data class CaseEntryOcrResult(
    val caseNumber: String? = "",
    val firNumber: String? = "",
    val policeStation: String? = "",
    val district: String? = "",
    val date: String? = "",
    val tags: String? = "",
    val notes: String? = "",
    val investigatingOfficer: String? = "",
    val status: String? = "Active"
)
