package com.example.smartlearningassistant

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileDescriptor

class MainActivity : AppCompatActivity() {

    private lateinit var btnCapture: Button
    private lateinit var btnImport: Button
    private lateinit var tvStatus: TextView

    private val REQUEST_IMAGE_CAPTURE = 1001
    private val REQUEST_CAMERA_PERMISSION = 1002
    private val REQUEST_IMPORT = 1003

    private lateinit var photoFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCapture = findViewById(R.id.btnCapture)
        btnImport = findViewById(R.id.btnImport)
        tvStatus = findViewById(R.id.tvStatus)

        btnCapture.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION
                )
            } else {
                openCamera()
            }
        }

        btnImport.setOnClickListener {
            openPicker()
        }
    }

    // -------- Camera (high-res file output) --------
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        photoFile = File.createTempFile("captured_image", ".jpg", cacheDir)
        val photoURI: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            photoFile
        )
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    // -------- Gallery/PDF picker (no extra permissions needed with SAF) --------
    private fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Allow images and PDFs
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }

        when (requestCode) {
            REQUEST_IMAGE_CAPTURE -> {
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                processBitmap(bitmap)
            }
            REQUEST_IMPORT -> {
                val uri = data?.data
                if (uri == null) {
                    Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
                } else {
                    handleImportedUri(uri)
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleImportedUri(uri: Uri) {
        val cr: ContentResolver = contentResolver
        val type = cr.getType(uri) ?: ""
        if (type.startsWith("image/")) {
            val bitmap = decodeBitmapFromUri(uri)
            if (bitmap != null) processBitmap(bitmap)
            else Toast.makeText(this, "Unable to load image", Toast.LENGTH_SHORT).show()
        } else if (type == "application/pdf") {
            val bmp = renderFirstPageOfPdf(uri)
            if (bmp != null) processBitmap(bmp)
            else Toast.makeText(this, "Unable to render PDF", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Unsupported file type", Toast.LENGTH_SHORT).show()
        }
    }

    // Decode large images safely
    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri).use { input ->
                if (input == null) return null
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                BitmapFactory.decodeStream(input, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    // Render first page of a PDF to Bitmap (API 21+)
    private fun renderFirstPageOfPdf(uri: Uri): Bitmap? {
        return try {
            val pfd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r")
            if (pfd == null) return null
            val fd: FileDescriptor = pfd.fileDescriptor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                renderer.openPage(0).use { page ->
                    val w = (page.width * 2) // render at 2x for clarity
                    val h = (page.height * 2)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    renderer.close()
                    return bmp
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // -------- Preprocess → OCR → ResultActivity --------
    private fun processBitmap(original: Bitmap) {
        tvStatus.text = "Preprocessing image…"
        val pre = preprocessForOcr(original)
        tvStatus.text = "Recognizing text…"

        val image = InputImage.fromBitmap(pre, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val detected = visionText.text.trim()
                val intent = Intent(this, ResultActivity::class.java)
                intent.putExtra("recognizedText", detected)
                startActivity(intent)
                tvStatus.text = ""
            }
            .addOnFailureListener { e ->
                tvStatus.text = "OCR error: ${e.message}"
                Toast.makeText(this, "OCR error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Preprocess for OCR without external libs:
     * - Downscale very large images (max side 2000px) to reduce blur from resampling later
     * - Convert to grayscale
     * - Boost contrast & slight sharpening (unsharp mask style)
     */
    private fun preprocessForOcr(src: Bitmap): Bitmap {
        // 1) Downscale if extremely large (faster & crisper OCR)
        val maxSide = 2000
        val scale = maxOf(src.width, src.height).toFloat() / maxSide
        val base = if (scale > 1f) {
            val w = (src.width / scale).toInt()
            val h = (src.height / scale).toInt()
            Bitmap.createScaledBitmap(src, w, h, true)
        } else {
            if (src.config != Bitmap.Config.ARGB_8888)
                src.copy(Bitmap.Config.ARGB_8888, true) else src
        }

        // 2) Grayscale + contrast
        val gray = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gray)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Contrast/brightness matrix (contrast 1.4, brightness 0)
        val contrast = 1.4f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val cm = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        // Then desaturate to grayscale
        val desat = ColorMatrix()
        desat.setSaturation(0f)
        cm.postConcat(desat)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(base, 0f, 0f, paint)

        // 3) Mild sharpen (unsharp mask approximation)
        val sharpened = try {
            val blur = gray.copy(gray.config, true)
            val rs = Canvas(blur)
            val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            blurPaint.maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL)
            rs.drawBitmap(blur, 0f, 0f, blurPaint)

            val finalBmp = Bitmap.createBitmap(gray.width, gray.height, gray.config)
            val c2 = Canvas(finalBmp)
            val p2 = Paint(Paint.ANTI_ALIAS_FLAG)
            // final = 1.5 * gray - 0.5 * blur
            val cm2 = ColorMatrix(floatArrayOf(
                1.5f, 0f, 0f, 0f, 0f,
                0f, 1.5f, 0f, 0f, 0f,
                0f, 0f, 1.5f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            p2.colorFilter = ColorMatrixColorFilter(cm2)
            c2.drawBitmap(gray, 0f, 0f, p2)
            p2.xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
            c2.drawBitmap(blur, 0f, 0f, p2)
            finalBmp
        } catch (_: Exception) {
            gray // if anything fails, use grayscale
        }

        return sharpened
    }
}
