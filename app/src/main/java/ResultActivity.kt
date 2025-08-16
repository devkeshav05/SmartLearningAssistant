package com.example.smartlearningassistant

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Html
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.*
import kotlin.math.ln

class ResultActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvRecognizedText: TextView
    private lateinit var tvAnswer: TextView
    private lateinit var etQuestion: EditText
    private lateinit var btnRead: Button
    private lateinit var btnStop: Button
    private lateinit var btnSummarize: Button
    private lateinit var btnAsk: Button

    private lateinit var tts: TextToSpeech
    private var rawText: String = ""
    private var cleanTextCache: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        tvRecognizedText = findViewById(R.id.tvRecognizedText)
        tvAnswer = findViewById(R.id.tvAnswer)
        etQuestion = findViewById(R.id.etQuestion)
        btnRead = findViewById(R.id.btnRead)
        btnStop = findViewById(R.id.btnStop)
        btnSummarize = findViewById(R.id.btnSummarize)
        btnAsk = findViewById(R.id.btnAsk)

        rawText = intent.getStringExtra("recognizedText") ?: ""
        cleanTextCache = cleanOcrText(rawText)
        tvRecognizedText.text = cleanTextCache

        tts = TextToSpeech(this, this)

        btnRead.setOnClickListener {
            speak(cleanTextCache)
        }
        btnStop.setOnClickListener {
            tts.stop()
        }
        btnSummarize.setOnClickListener {
            val summary = summarize(cleanTextCache, maxSentences = 3)
            tvRecognizedText.text = summary
            speak(summary)
        }
        btnAsk.setOnClickListener {
            val q = etQuestion.text.toString()
            val ans = answerFromContext(cleanTextCache, q)
            tvAnswer.text = ans
            if (ans.isNotBlank()) speak(ans)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Use device default language (works offline if pack installed)
            val res = tts.setLanguage(Locale.getDefault())
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to US English
                tts.language = Locale.US
            }
            // Auto-read once when screen opens
            if (cleanTextCache.isNotBlank()) speak(cleanTextCache)
        }
    }

    private fun speak(text: String) {
        // Clear any previous utterance
        tts.stop()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
    }

    override fun onDestroy() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (_: Exception) { }
        super.onDestroy()
    }

    // ---------- OCR post-processing & NLP helpers (offline) ----------

    // 1) Clean OCR text: remove hyphen breaks, fix spacing, strip junk
    private fun cleanOcrText(input: String): String {
        var s = input

        // Merge hyphenated line breaks: "exam-\nple" -> "example"
        s = s.replace(Regex("-\\s*\\n"), "")
        // Replace newlines with spaces
        s = s.replace(Regex("[\\r\\n]+"), " ")
        // Collapse multiple spaces
        s = s.replace(Regex("\\s{2,}"), " ").trim()

        // Remove common OCR artifacts (non-printable)
        s = s.replace(Regex("[^\\p{Print}\\t\\x0A\\x0D]"), "")

        // Fix spaced letters like "L E A R N" -> "LEARN" (heuristic)
        s = s.replace(Regex("\\b([A-Za-z])(?:\\s+[A-Za-z]){2,}\\b")) {
            it.value.replace(" ", "")
        }

        return s
    }

    // 2) Split into sentences
    private fun splitSentences(text: String): List<String> {
        return text.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    // 3) Very small stopword set
    private val stop = setOf(
        "the","is","in","at","of","on","and","a","an","to","for","it","this","that","with",
        "as","by","be","are","or","from","was","were","but","if","then","so","we","you","your"
    )

    // 4) Simple word frequency map
    private fun wordFreq(text: String): Map<String, Int> {
        val freq = HashMap<String, Int>()
        val tokens = text.lowercase(Locale.getDefault()).split(Regex("[^a-z0-9]+"))
        for (t in tokens) {
            if (t.isBlank() || t in stop) continue
            freq[t] = (freq[t] ?: 0) + 1
        }
        return freq
    }

    // 5) Extractive summarization: score sentences by word frequencies (tf * log damp)
    private fun summarize(text: String, maxSentences: Int = 3): String {
        val sentences = splitSentences(text)
        if (sentences.size <= maxSentences) return text

        val freq = wordFreq(text)
        val scores = sentences.map { s ->
            val tokens = s.lowercase(Locale.getDefault()).split(Regex("[^a-z0-9]+"))
            val sum = tokens.filter { it.isNotBlank() && it !in stop }.sumOf { w ->
                // weight long/rare words slightly more
                val f = (freq[w] ?: 0).toDouble()
                if (f == 0.0) 0.0 else 1.0 + ln(1.0 + f)
            }
            sum
        }

        // Pick top N sentences, but keep original order
        val topIdx = scores.withIndex().sortedByDescending { it.value }.take(maxSentences).map { it.index }.toSet()
        val result = sentences.filterIndexed { i, _ -> i in topIdx }
        return result.joinToString(" ")
    }

    // 6) Super-light Q&A: return the sentence(s) that best match the question keywords
    private fun answerFromContext(context: String, question: String): String {
        if (question.isBlank() || context.isBlank()) return "Please type a question."
        val sentences = splitSentences(context)
        val qTokens = question.lowercase(Locale.getDefault()).split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() && it !in stop }.toSet()
        if (qTokens.isEmpty()) return "Please include some keywords in your question."

        var bestScore = 0
        val best = mutableListOf<String>()
        for (s in sentences) {
            val sTokens = s.lowercase(Locale.getDefault()).split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() && it !in stop }.toSet()
            val overlap = qTokens.intersect(sTokens).size
            if (overlap > 0) {
                when {
                    overlap > bestScore -> { bestScore = overlap; best.clear(); best.add(s) }
                    overlap == bestScore -> best.add(s)
                }
            }
        }
        return if (best.isEmpty()) "I couldn't find an exact answer in the text."
        else best.joinToString(" ")
    }
}
