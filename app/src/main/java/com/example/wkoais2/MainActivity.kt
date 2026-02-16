package com.example.wkoais2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var galleryButton: ImageButton
    private lateinit var cameraButton: ImageButton

    private var imageCapture: ImageCapture? = null

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
        private const val LENS_PACKAGE = "com.google.ar.lens"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        previewView = findViewById(R.id.previewView)
        galleryButton = findViewById(R.id.galleryButton)
        cameraButton = findViewById(R.id.imageButton2)

        galleryButton.setOnClickListener {
            openGalleryApp()
        }

        cameraButton.setOnClickListener {
            playClickSound()
            takePhotoAndSendToLens()
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    // check 4 permissions

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    // start the cam widget in the background

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    // take photo

    private fun takePhotoAndSendToLens() {
        val capture = imageCapture ?: return

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())

        val photoFile = File(cacheDir, "IMG_$timestamp.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@MainActivity,
                        "Capture failed: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                    val photoUri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        photoFile
                    )

                    galleryButton.setImageURI(photoUri)

                    sendToGoogleLens(photoUri)

                    Toast.makeText(
                        this@MainActivity,
                        "Sent to Google Lens!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // lens stuff

    private fun sendToGoogleLens(photoUri: Uri) {
        try {
            val possiblePackages = listOf(
                "com.google.ar.lens",                       // Lens standalone (rare)
                "com.google.android.googlequicksearchbox",  // Google App (most common)
                "com.google.android.apps.searchlite"        // Google Go (some devices)
            )

            val baseIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, photoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Try to force open one of the known Google Lens handlers
            for (pkg in possiblePackages) {
                val testIntent = Intent(baseIntent).apply {
                    setPackage(pkg)
                }

                if (testIntent.resolveActivity(packageManager) != null) {
                    grantUriPermission(
                        pkg,
                        photoUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    startActivity(testIntent)
                    return
                }
            }

            startActivity(Intent.createChooser(baseIntent, "Search with"))

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open Lens", Toast.LENGTH_SHORT).show()
        }
    }
    // open gallery on event

    private fun openGalleryApp() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show()
        }
    }

    // what kind of awesome is this

    private fun playClickSound() {
        val mediaPlayer = MediaPlayer.create(this, R.raw.click3)
        mediaPlayer.start()
        mediaPlayer.setOnCompletionListener { mp ->
            mp.release()
        }
    }
}