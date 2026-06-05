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
import retrofit2.http.Path
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

@JsonClass(generateAdapter = true)
data class MultipleTourEntriesOcrResult(
    val entries: List<TourEntryOcrResult> = emptyList()
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
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
     * Parse the document image into structured TourEntryOcrResults using the Gemini 3.5 Flash API.
     */
    suspend fun parseDocument(base64Image: String, mimeType: String, customApiKey: String? = null): MultipleTourEntriesOcrResult? {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API Key is not configured. Please configure it in your AI Studio Secrets panel or enter your own in the Profile tab.")
        }

        val prompt = """
            You are an expert OCR and data extraction system for forensic travel logs, cases, and vehicle log records.
            Analyze the attached document (which can be a case requisition letter, a case register table, a vehicle LOG BOOK table, or a mobile FSL unit report letter).
            
            Instead of selecting just one entry, EXTRACT ALL VALID COMPLETED HOURLY/DAILY JOURNEY ENTRIES detected on the page or document. Scan every row of the table, list of cases, or log book records sequentially and extract them all.
            
            SPECIFICS FOR EACH TYPE OF DOCUMENT DETECTED:
            
            1. MOBILE FORENSIC UNIT / CASE REQUISITION LETTERS SPECIFICS:
               - Look for CS Number / Crime Scene investigation number in the reference number (क्रमांक), which usually starts with "FSL/.../CS .../2025/..." or "CS .../2025". Convert this reference into a clean representation like 'Sikar CS 129/25' or 'Sikar CS 129/2025' depending on the location and year. For example, 'FSL/DMFU/SIKAR/CS 129/2025/ spl-1' MUST be mapped to "Sikar CS 129/25" under 'csNumber'.
               - Look for the Police Station (पुलिस थाना) and district mentioned. For example, "पुलिस थाना धोद" means the 'policeStation' is "Dhod" or "PS Dhod", and "जिला सीकर" means the 'district' is "Sikar".
               - Look for the F.I.R. / Case number (विषय : अ.स. 293/25) or (अ.स. 293/25). This is the FIR number. Map this to 'firNumber' as "293/25" or "293/2025" under 'firNumber'.
               - Look for the report/inspection date (दिनांक 12-12-2025) or (12-12-25). This must be converted to YYYY-MM-DD, e.g., "2025-12-12", and mapped to both 'date' and 'reportDate'.
               - If departure/arrival times are mentioned or can be estimated: Look for phrases like "11.56 एएम बजे" or "09:10 एएम बजे". For departure 'depTime', map "09:10" or "11:56" (rounded appropriately to 24-hour HH:MM format). If not listed, estimate e.g. "11:56" for departure and e.g., "14:30" or similar for return/arrival to make the tour diary complete.
               - If distance is not explicitly written (e.g., 0.0), estimate a realistic round-trip travel distance in kilometers (e.g., 30.0 km) for traveling from Sikar to Dhod/Gunathu and back.

            2. VEHICLE LOG BOOK SPECIFICS:
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

            3. HANDWRITTEN CRIME SCENE REGISTER / TOUR DIARY SPECIFICS:
               If the uploaded document is a handwritten table on lined or checkered paper representing crime scene investigations, it typically has the following columns:
               - S.N.: Serial number (e.g., 01, 02, 03, 04, 05, 06, 07, 08)
               - C.S.: Crime Scene Investigation number (e.g., 64, 65, 66, 67, 68, 69, 70, 71)
               - Informer D & T: Hand-written dates and times (e.g., '01/09/2025 07:28 Am' or '15/09/25 10:00 pm'). Convert the date portion to YYYY-MM-DD. E.g. '15/09/2025' or '15/09/25' becomes '2025-09-15'.
               - PS/CS: The location or police station. Extract the name of the Police Station (which is usually the main text preceding any Hindi keywords in parantheses, e.g. 'Udaipurwati' from 'Udaipurwati (बीरूवाला कुआ)', 'Bagar' from 'Bagar (कस्बा बगड)', 'Pilani' from 'Pilani', 'Singhana' from 'Singhana', 'Gudhagaudji' from 'Gudhagaudji', 'Pacheri Kalan' from 'Pacheri Kalan', 'Khetri nagar' from 'Khetri nagar').
               - TOD: Time of Departure. (e.g., '16/09/25 09:00 Am' or '04:25 pm' or '09:30 Am'). Extract the time and convert to 24-hour HH:MM format (e.g., '09:00 Am' -> '09:00', '04:25 pm' -> '16:25', '12:30 Pm' -> '12:30', '10:00 pm' -> '22:00').
               - TOR: Time of Return/Arrival. Extract the time and convert to 24-hour HH:MM format (e.g., '09:30 pm' -> '21:30', '08:25 Am' -> '08:25', '11:15 pm' -> '23:15').
               - FIR: Case / FIR references written on the right margin (e.g. 'अ.स. 195/25' means FIR is '195/2025' or '195/25', 'अ.सं. 231/25' means '231/2025' or '231/25').

            EXTRACT ALL JOURNEY ROWS WISE AND FORMAT AS JSON:
            Return a valid JSON object matching this structure containing exactly the extracted entries array:
            
            {
              "entries": [
                {
                  "date": "YYYY-MM-DD",     // Date of scene visit/travel.
                  "depTime": "HH:MM",        // Departure time in 24-hour format.
                  "arrTime": "HH:MM",        // Arrival time in 24-hour format.
                  "travelMode": "string",    // Mode of travel. If not specified, default to "MT Vehicle".
                  "distance": 0.0,          // Distance in kilometers.
                  "csNumber": "string",      // Crime Scene Number or reference.
                  "firNumber": "string",     // FIR Number or reference.
                  "policeStation": "string", // Police Station name.
                  "district": "string",      // District name.
                  "reportDate": "YYYY-MM-DD" // Date of report/inspection (defaults to date if not found).
                }
              ]
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

        val models = listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-1.5-flash")
        var lastException: Exception? = null
        for (modelName in models) {
            try {
                val response = apiService.generateContent(modelName, apiKey, request)
                val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (jsonText != null) {
                    val adapter = moshi.adapter(MultipleTourEntriesOcrResult::class.java)
                    val cleanedJson = jsonText.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    val result = adapter.fromJson(cleanedJson)
                    if (result != null) {
                        return result
                    }
                }
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }
        if (lastException != null) {
            throw lastException
        }
        return null
    }

    /**
     * Parse the crime scene register document into a structured CaseEntryOcrResult.
     */
    suspend fun parseCrimeSceneRegister(base64Image: String, mimeType: String, customApiKey: String? = null): CaseEntryOcrResult? {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API Key is not configured. Please configure it in your Secrets panel or enter your own in the Profile tab.")
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

        val models = listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-1.5-flash")
        var lastException: Exception? = null
        for (modelName in models) {
            try {
                val response = apiService.generateContent(modelName, apiKey, request)
                val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (jsonText != null) {
                    val adapter = moshi.adapter(CaseEntryOcrResult::class.java)
                    val cleanedJson = jsonText.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    val result = adapter.fromJson(cleanedJson)
                    if (result != null) {
                        return result
                    }
                }
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }
        if (lastException != null) {
            throw lastException
        }
        return null
    }

    /**
     * Parse the handwritten note / scribble drawing directly from screen and convert into structured notes text.
     */
    suspend fun transcribeScribbleDrawing(base64Image: String, customApiKey: String? = null): String? {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return "Note: Gemini API key is not configured. Enter a valid key in settings or AI Studio Secrets to transcribe doodles."
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

        val models = listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-1.5-flash")
        var lastException: Exception? = null
        for (modelName in models) {
            try {
                val response = apiService.generateContent(modelName, apiKey, request)
                val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (text != null) {
                    return text
                }
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }
        if (lastException != null) {
            throw lastException
        }
        return null
    }

    /**
     * Parse the tour diary text (from .docx or .txt document) into structured TourEntryOcrResults using the Gemini 3.5 Flash API.
     */
    suspend fun parseTextDocument(documentText: String, customApiKey: String? = null): MultipleTourEntriesOcrResult? {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API Key is not configured. Please configure it in your AI Studio Secrets panel or enter your own in the Profile tab.")
        }

        val prompt = """
            You are an expert data extraction system for forensic travel logs, cases, and vehicle log records.
            Analyze the following text which has been extracted from a tour diary document:
            
            ""${'"'}
            $documentText
            ""${'"'}
            
            Extract ALL valid journey entries/days detected in this text. Scan and reconstruct the timeline day by day.
            
            Extract the following details for each journey:
            - date: Convert any date (like DD/MM/YYYY or similar) to YYYY-MM-DD.
            - depTime: Departure time in 24-hour HH:MM format.
            - arrTime: Arrival/Return time in 24-hour HH:MM format.
            - travelMode: Travel mode (e.g. "MT Vehicle", "Bus", "Train").
            - distance: Double (kilometers run).
            - csNumber: Crime Scene serial number.
            - firNumber: FIR number.
            - policeStation: Name of the Police Station.
            - district: Name of District.
            - reportDate: Date of filing report (defaults to date).

            Return a valid JSON object matching this structure containing exactly the extracted entries array:
            
            {
              "entries": [
                {
                  "date": "YYYY-MM-DD",
                  "depTime": "HH:MM",
                  "arrTime": "HH:MM",
                  "travelMode": "string",
                  "distance": 0.0,
                  "csNumber": "string",
                  "firNumber": "string",
                  "policeStation": "string",
                  "district": "string",
                  "reportDate": "YYYY-MM-DD"
                }
              ]
            }
            
            Strictly return ONLY the raw JSON block without markdown formatting or code blocks. Do not include any trailing commas or invalid JSON tokens.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt)
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        val models = listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-1.5-flash")
        var lastException: Exception? = null
        for (modelName in models) {
            try {
                val response = apiService.generateContent(modelName, apiKey, request)
                val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (jsonText != null) {
                    val adapter = moshi.adapter(MultipleTourEntriesOcrResult::class.java)
                    val cleanedJson = jsonText.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    val result = adapter.fromJson(cleanedJson)
                    if (result != null) {
                        return result
                    }
                }
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }
        if (lastException != null) {
            throw lastException
        }
        return null
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
