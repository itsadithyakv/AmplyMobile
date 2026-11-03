package com.amply.mobile.report

import android.content.Context
import android.os.Build
import com.amply.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ReportKind(val label: String, val subject: String) { Bug("Something's wrong", "Bug"), Crash("Crash or freeze", "Crash or freeze"), Idea("An idea", "Idea") }

data class ReportDraft(
    val kind: ReportKind,
    val summary: String,
    val details: String,
    val steps: String,
    val email: String,
    val includeDiagnostics: Boolean,
)

enum class ReportResult { Sent, RateLimited, Offline, Failed, Invalid }

/**
 * "Report a problem", sent through the same EmailJS service and template as the desktop app
 * (src/services/bugReport.ts), so reports from both land in one inbox with the same fields.
 * Nothing is sent until the user presses Send; device details are optional and shown first.
 *
 * EmailJS rejects calls from outside a browser unless "Allow EmailJS API for non-browser
 * applications" is on in the EmailJS account (Account > Security).
 */
object BugReporter {
    private const val ENDPOINT = "https://api.emailjs.com/api/v1.0/email/send"
    private const val SERVICE_ID = "service_wrivwqo"
    private const val TEMPLATE_ID = "template_emt2dhk"
    private const val PUBLIC_KEY = "qFdV7E6GG4xYS1i7f"
    private const val NOT_INCLUDED = "Not included"
    private const val COOLDOWN_MS = 60_000L
    private const val DAILY_MAX = 5

    const val SUMMARY_MIN = 4
    const val DETAILS_MIN = 10

    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    fun deviceDescription(librarySize: Int, plan: String): String =
        "Amply ${BuildConfig.VERSION_NAME} for Android ($plan)\n" +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Language: ${Locale.getDefault().toLanguageTag()}\n" +
            "Songs in library: $librarySize"

    fun isValid(draft: ReportDraft): Boolean =
        draft.summary.trim().length >= SUMMARY_MIN && draft.details.trim().length >= DETAILS_MIN

    suspend fun send(context: Context, draft: ReportDraft, plan: String, librarySize: Int): ReportResult = withContext(Dispatchers.IO) {
        if (!isValid(draft)) return@withContext ReportResult.Invalid
        val prefs = context.getSharedPreferences("amply_reports", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val sent = prefs.getString("sent", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < 86_400_000L }
        // One a minute and five a day keeps a stuck button off the shared EmailJS quota.
        if (sent.any { now - it < COOLDOWN_MS } || sent.size >= DAILY_MAX) return@withContext ReportResult.RateLimited

        val withDetails = draft.includeDiagnostics
        val params = JSONObject()
            .put("report_id", UUID.randomUUID().toString().take(8))
            .put("kind", draft.kind.subject)
            .put("summary", draft.summary.trim())
            .put("details", draft.details.trim())
            .put("steps", draft.steps.trim().ifBlank { "Not given" })
            .put("reply_to", draft.email.trim())
            .put("reply_note", draft.email.trim().ifBlank { "No reply address given" })
            .put("app_version", "${BuildConfig.VERSION_NAME} (Android)")
            .put("plan", plan)
            .put("language", Locale.getDefault().toLanguageTag())
            // The template's "windows" field carries the OS line on both platforms.
            .put("windows", if (withDetails) "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}" else NOT_INCLUDED)
            .put("install", if (withDetails) "Android app" else NOT_INCLUDED)
            .put("library_size", if (withDetails) librarySize.toString() else NOT_INCLUDED)
            .put("recent_errors", if (withDetails) "None recorded" else NOT_INCLUDED)
            .put("app_log", if (withDetails) "Not collected on Android" else NOT_INCLUDED)
            .put("sent_at", Instant.now().toString())
        val body = JSONObject()
            .put("service_id", SERVICE_ID)
            .put("template_id", TEMPLATE_ID)
            .put("user_id", PUBLIC_KEY)
            .put("template_params", params)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(ENDPOINT).post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        prefs.edit().putString("sent", (sent + now).joinToString(",")).apply()
                        ReportResult.Sent
                    }
                    response.code == 429 -> ReportResult.RateLimited
                    else -> ReportResult.Failed
                }
            }
        } catch (_: java.io.IOException) {
            ReportResult.Offline
        }
    }
}
