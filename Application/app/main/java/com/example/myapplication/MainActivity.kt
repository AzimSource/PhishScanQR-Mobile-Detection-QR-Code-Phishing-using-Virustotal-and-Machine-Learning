package com.example.myapplication

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    private lateinit var cameraExecutor: ExecutorService
    private var alreadyScanned = false

    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    private var camera: Camera? = null
    private lateinit var previewView: PreviewView
    private lateinit var scannerContainer: View
    private lateinit var loadingDialog: AlertDialog

    // VirusTotal
    private val vtApiKey =
        "f51fa69f314026f822f3cc9ad63cbcb049ebd2116ae2290a28aa3620139fafa6"
    private val vtPollDelayMs = 1200L
    private val vtMaxPolls = 8

    // ML Endpoint
    private val mlEndpoint = "https://phishing-detector-gcp8.onrender.com/predict"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { scanImageFromUri(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        previewView = findViewById(R.id.previewView)
        scannerContainer = findViewById(R.id.scannerContainer)

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Animate scanner line
        // Scanner animation (red line moving up and down)
        val scannerLine = findViewById<View>(R.id.scannerLine)
        val scannerContainer = findViewById<View>(R.id.scannerContainer) // <-- IMPORTANT: add this

        scannerContainer.post {
            val frameHeight = scannerContainer.height.toFloat()
            ObjectAnimator.ofFloat(
                scannerLine,
                "translationY",
                0f,
                frameHeight - scannerLine.height
            ).apply {
                duration = 2000
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }


        // Flashlight button
        findViewById<ImageButton>(R.id.btnFlash).setOnClickListener { toggleFlashlight() }

        // Gallery button
        findViewById<ImageButton>(R.id.btnGallery).setOnClickListener { pickImageFromGallery() }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(options)

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                val boundingBox = barcode.boundingBox ?: continue

                                // Center of detected QR (relative to image)
                                val boxCenterX = boundingBox.centerX().toFloat() / image.width.toFloat()
                                val boxCenterY = boundingBox.centerY().toFloat() / image.height.toFloat()

                                // Get scanner frame position on screen
                                val frameLocation = IntArray(2)
                                scannerContainer.getLocationOnScreen(frameLocation)
                                val frameX = frameLocation[0]
                                val frameY = frameLocation[1]
                                val frameWidth = scannerContainer.width
                                val frameHeight = scannerContainer.height

                                // Get previewView position on screen
                                val previewLocation = IntArray(2)
                                previewView.getLocationOnScreen(previewLocation)
                                val previewX = previewLocation[0]
                                val previewY = previewLocation[1]
                                val previewWidth = previewView.width
                                val previewHeight = previewView.height

                                // Convert relative center to absolute screen position
                                val absX = (previewX + (boxCenterX * previewWidth)).toInt()
                                val absY = (previewY + (boxCenterY * previewHeight)).toInt()

                                // Check if QR center is inside the scanning box
                                val insideFrame =
                                    absX in frameX..(frameX + frameWidth) &&
                                            absY in frameY..(frameY + frameHeight)

                                if (insideFrame && !alreadyScanned) {
                                    alreadyScanned = true
                                    barcode.rawValue?.let { value ->
                                        runOnUiThread {
                                            playFeedback()
                                            checkUrlSafety(value)
                                        }
                                    }
                                }
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                } else {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this as LifecycleOwner, cameraSelector, preview, imageAnalysis
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Feedback vibration
    private fun playFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(100)
    }

    // ======================
    // URL & ML / VT Logic
    // ======================

    private fun checkUrlSafety(rawText: String) {
        val normalized = normalizeUrl(rawText) ?: run {
            showQrDialog(rawText, rawText)
            return
        }

        // ✅ Step 1: Check local database before making API calls
        if (dbHelper.isUrlExist(DatabaseHelper.TABLE_BLACKLIST, normalized)) {
            Log.d("DB", "URL found in blacklist: $normalized")
            showWarningDialog(normalized, normalized, "⚠️ This link is unsafe and blacklisted by the system")
            return
        }

        if (dbHelper.isUrlExist(DatabaseHelper.TABLE_WHITELIST, normalized)) {
            Log.d("DB", "URL found in whitelist: $normalized")
            showQrDialog(normalized, normalized)
            return
        }

        // ✅ Step 2: If not found locally, proceed with checking online
        Thread {
            val expandedUrl = resolveFinalUrl(normalized)

            runOnUiThread {
//                Toast.makeText(this, "✅ Resolved URL:\n$expandedUrl", Toast.LENGTH_SHORT).show()
                Log.d("QR", "Expanded final URL: $expandedUrl")
                sendToVirusTotal(expandedUrl, normalized)
            }
        }.start()
    }



    private fun normalizeUrl(raw: String): String? {
        val lower = raw.trim()
        if (!isHttpUrl(lower)) return null
        return when {
            lower.startsWith("http://", true) || lower.startsWith("https://", true) -> lower
            else -> "https://$lower"
        }
    }

    private fun isHttpUrl(text: String): Boolean {
        val lower = text.lowercase().trim()
        return (lower.startsWith("http://") || lower.startsWith("https://") || (lower.contains('.') && !lower.contains(' ')))
    }

    // ============ VirusTotal + ML ============

    private fun sendToVirusTotal(expandedUrl: String, originalUrl: String) {
        showLoadingDialog()
        val submitBody = FormBody.Builder().add("url", expandedUrl).build()
        val submitReq = Request.Builder()
            .url("https://www.virustotal.com/api/v3/urls")
            .header("x-apikey", vtApiKey)
            .post(submitBody)
            .build()

        Log.d("VT", "Submitting to VT: $expandedUrl")
        client.newCall(submitReq).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("VT", "Submit failed: ${e.message}")
                checkWithML(expandedUrl, originalUrl, vtFailed = true)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string()
                    Log.d("VT", "Submit raw response: $body")
                    val json = JSONObject(body ?: "")
                    val analysisId =
                        json.optJSONObject("data")?.optString("id", null) ?: return
                    pollAnalysis(analysisId, expandedUrl, originalUrl, vtMaxPolls)
                }
            }
        })
    }

    private fun pollAnalysis(
        analysisId: String,
        expandedUrl: String,
        originalUrl: String,
        attemptsLeft: Int
    ) {
        val req = Request.Builder()
            .url("https://www.virustotal.com/api/v3/analyses/$analysisId")
            .header("x-apikey", vtApiKey)
            .get()
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (attemptsLeft > 0) Handler(Looper.getMainLooper()).postDelayed({
                    pollAnalysis(analysisId, expandedUrl, originalUrl, attemptsLeft - 1)
                }, vtPollDelayMs)
                else checkWithML(expandedUrl, originalUrl, vtFailed = true)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: ""
                    Log.d("VT", "Poll raw response: $body")
                    val json = JSONObject(body)
                    val attr = json.optJSONObject("data")?.optJSONObject("attributes")
                    val status = attr?.optString("status", "")
                    if (status != "completed") {
                        if (attemptsLeft > 0) Handler(Looper.getMainLooper()).postDelayed({
                            pollAnalysis(analysisId, expandedUrl, originalUrl, attemptsLeft - 1)
                        }, vtPollDelayMs)
                        else checkWithML(expandedUrl, originalUrl, vtFailed = true)
                        return
                    }

                    val stats = attr.optJSONObject("stats")
                    val malicious = stats?.optInt("malicious", 0) ?: 0
                    val suspicious = stats?.optInt("suspicious", 0) ?: 0

                    runOnUiThread {
                        if (malicious > 0 || suspicious > 0) {
                            dismissLoadingDialog()
                            dbHelper.insertUrl(DatabaseHelper.TABLE_BLACKLIST, expandedUrl)
                            showWarningDialog(originalUrl, expandedUrl, "⚠️ This link is unsafe!. Do not open the link!!")
                        } else checkWithML(expandedUrl, originalUrl, vtFailed = false)
                    }
                }
            }
        })
    }

    private fun checkWithML(expandedUrl: String, originalUrl: String, vtFailed: Boolean) {
        val json = JSONObject().put("url", expandedUrl)
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val req = Request.Builder()
            .url(mlEndpoint)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        Log.d("ML", "Calling ML endpoint: $mlEndpoint with URL: $expandedUrl")

        client.newCall(req).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e("ML", "ML request failed: ${e.message}")
                runOnUiThread {
                    dismissLoadingDialog()
                    // If ML failed but VirusTotal already succeeded → trust VT result
                    if (vtFailed) {
                        showWarningDialog(originalUrl, expandedUrl, "⚠️ Both ML and VirusTotal failed. Suspicious link.")
                    } else {
                        Log.w("ML", "ML failed, using VirusTotal result instead.")
                        showQrDialog(originalUrl, expandedUrl)
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = it.body?.string()
                    Log.d("ML", "Raw ML response: $bodyStr")

                    val prediction = try {
                        val jsonResp = JSONObject(bodyStr ?: "{}")
                        jsonResp.optString("final_prediction")
                            .ifEmpty { jsonResp.optString("prediction") }
                            .ifEmpty { jsonResp.optString("label") }
                            .lowercase()
                    } catch (e: Exception) {
                        ""
                    }

                    runOnUiThread {
                        dismissLoadingDialog()
                        if (prediction in listOf("legitimate", "benign", "legit")) {
                            dbHelper.insertUrl(DatabaseHelper.TABLE_WHITELIST, expandedUrl)
                            showQrDialog(originalUrl, expandedUrl)
                        } else if (prediction.isEmpty()) {
                            // If ML returned no label → fallback to VT result if it worked
                            if (vtFailed) {
                                showWarningDialog(originalUrl, expandedUrl, "⚠️ ML returned no result. Suspicious link.")
                            } else {
                                Log.w("ML", "ML empty result, using VirusTotal result instead.")
                                showQrDialog(originalUrl, expandedUrl)
                            }
                        } else {
                            dbHelper.insertUrl(DatabaseHelper.TABLE_BLACKLIST, expandedUrl)
                            showWarningDialog(originalUrl, expandedUrl, "⚠️ This link is unsafe!. Do not open the Link!!")
                        }
                    }
                }
            }
        })
    }


    // ============ Helpers ============

    private fun resolveFinalUrl(initialUrl: String): String {
        var currentUrl = initialUrl
        val maxRedirects = 10
        val seenUrls = mutableSetOf<String>()

        // 1. Setup Client: We handle redirects manually to catch the intermediate steps
        val redirectClient = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        try {
            for (i in 0 until maxRedirects) {
                if (!seenUrls.add(currentUrl)) {
                    Log.w("ResolveURL", "Redirect loop detected.")
                    break
                }

                // 2. Request the URL
                // Note: We use a browser User-Agent.
                // If this still fails for some specific sites, try removing the User-Agent header.
                val request = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android 10; Mobile; rv:88.0) Gecko/88.0 Firefox/88.0")
                    .build()

                val response = redirectClient.newCall(request).execute()

                // 3. Handle Standard HTTP Redirects (301, 302, etc.)
                if (response.isRedirect) {
                    val location = response.header("Location")
                    response.close() // Close immediately
                    if (location != null) {
                        val newUrl = response.request.url.resolve(location)
                        currentUrl = newUrl.toString()
                        continue // Loop again with new URL
                    }
                }

                // 4. Handle HTML Based Redirects (Meta Refresh & JavaScript)
                // If it's 200 OK, we must check the BODY for hidden redirects (like me-qr.com uses)
                val contentType = response.header("Content-Type", "") ?: ""
                if (response.isSuccessful && contentType.contains("text/html", ignoreCase = true)) {
                    val html = response.body?.string() ?: ""
                    response.close() // Close after reading body

                    // A. Check for Meta Refresh (<meta http-equiv="refresh" ...>)
                    val metaRegex = Regex(
                        """<meta\s+http-equiv\s*=\s*["']?refresh["']?\s+content\s*=\s*["']?\d+\s*;\s*url\s*=\s*([^"']+)["']?""",
                        RegexOption.IGNORE_CASE
                    )
                    val metaMatch = metaRegex.find(html)
                    if (metaMatch != null) {
                        val metaUrl = metaMatch.groupValues[1]
                        Log.d("ResolveURL", "Found Meta Refresh to: $metaUrl")
                        currentUrl = metaUrl // Update and loop again
                        continue
                    }

                    // B. Check for JavaScript Redirects (window.location = "...")
                    // This is what fixes 'me-qr.com' and other ad-based shorteners
                    val jsAssignRegex = Regex(
                        """(window\.location|window\.location\.href|location\.href)\s*=\s*["']([^"']+)["']""",
                        RegexOption.IGNORE_CASE
                    )
                    val jsReplaceRegex = Regex(
                        """(window\.location\.replace|location\.replace)\(\s*["']([^"']+)["']\s*\)""",
                        RegexOption.IGNORE_CASE
                    )

                    val jsMatch = jsAssignRegex.find(html) ?: jsReplaceRegex.find(html)

                    if (jsMatch != null) {
                        // The URL is usually in the last group
                        val jsUrl = jsMatch.groupValues.last()
                        Log.d("ResolveURL", "Found JS Redirect to: $jsUrl")

                        // Sometimes the JS URL is relative, resolve it
                        val resolvedJsUrl = try {
                            // Re-create an HttpUrl to resolve relative paths
                            Request.Builder().url(currentUrl).build().url.resolve(jsUrl)?.toString()
                        } catch (e: Exception) { null }

                        if (resolvedJsUrl != null) {
                            currentUrl = resolvedJsUrl
                            continue // Loop again
                        }
                    }

                    // If we reached here, it's a 200 OK page with no obvious redirect.
                    // This is the final destination.
                    break
                } else {
                    response.close()
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("ResolveURL", "Resolution failed: ${e.message}")
        }

        Log.d("ResolveURL", "✅ Final Resolved URL: $currentUrl")
        return currentUrl
    }




    private fun pickImageFromGallery() = galleryLauncher.launch("image/*")

    private fun scanImageFromUri(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
            )
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val value = barcodes.firstOrNull()?.rawValue
                    if (value != null) checkUrlSafety(value)
                    else Toast.makeText(this, "No QR found", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to scan image", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
        }
    }

    // ============ Dialogs ============

    private fun showLoadingDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialoq_loading, null)
        loadingDialog = AlertDialog.Builder(this).setView(view).setCancelable(false).create()
        loadingDialog.show()
    }

    private fun dismissLoadingDialog() {
        if (this::loadingDialog.isInitialized && loadingDialog.isShowing) loadingDialog.dismiss()
    }

    private fun showQrDialog(originalUrl: String, expandedUrl: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialoq_qr_result, null)
        val txtLink = dialogView.findViewById<TextView>(R.id.txtLink)
        val btnOpen = dialogView.findViewById<Button>(R.id.btnOpen)
        val btnCopy = dialogView.findViewById<Button>(R.id.btnCopy)

        txtLink.text = expandedUrl
        val alert = AlertDialog.Builder(this).setView(dialogView).create()

        btnOpen.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(expandedUrl)))
            } catch (_: Exception) {
                Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
            }
        }

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("QR Code", expandedUrl))
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        alert.setOnDismissListener {
            Handler(Looper.getMainLooper()).postDelayed({ alreadyScanned = false }, 300)
        }

        alert.show()
    }

    private fun showWarningDialog(originalUrl: String, expandedUrl: String, message: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialoq_warning, null)
        val txt = view.findViewById<TextView>(R.id.txtWarning)
        val btn = view.findViewById<Button>(R.id.btnOk)
        txt.text = message
        val dialog = AlertDialog.Builder(this).setView(view).create()
        btn.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            Handler(Looper.getMainLooper()).postDelayed({ alreadyScanned = false }, 300)
        }
        dialog.show()
    }

    private fun toggleFlashlight() {
        camera?.let {
            if (it.cameraInfo.hasFlashUnit()) {
                val torchState = it.cameraInfo.torchState.value
                it.cameraControl.enableTorch(torchState != TorchState.ON)
            } else {
                Toast.makeText(this, "No flashlight available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
