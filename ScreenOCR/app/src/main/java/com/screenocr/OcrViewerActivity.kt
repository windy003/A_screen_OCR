package com.screenocr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.screenocr.databinding.ActivityOcrViewerBinding

class OcrViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOcrViewerBinding
    private var allText: String = ""
    private var textBlocks: List<Text.TextBlock> = emptyList()

    companion object {
        private const val REQUEST_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOcrViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()

        if (hasStoragePermission()) {
            loadLatestScreenshot()
        } else {
            requestStoragePermission()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_PERMISSION)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadLatestScreenshot()
            } else {
                Toast.makeText(this, "需要存储权限才能读取截图", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun loadLatestScreenshot() {
        val uri = findLatestScreenshot()
        if (uri == null) {
            Toast.makeText(this, "未找到截图，请先用实体按键截屏", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val bitmap = try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }

        if (bitmap == null) {
            Toast.makeText(this, "无法加载截图", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Show image
        binding.zoomableImageView.setImageBitmap(bitmap)
        binding.zoomableImageView.post { binding.zoomableImageView.fitToView() }

        // Sync overlay with zoom/pan
        binding.zoomableImageView.onMatrixChangeListener = { matrix ->
            binding.ocrOverlayView.setImageMatrix(matrix)
        }

        // Tap text on image
        binding.zoomableImageView.onTapListener = { imgX, imgY ->
            onImageTapped(imgX, imgY)
        }

        // Auto run OCR
        runOcr(uri)
    }

    private fun findLatestScreenshot(): Uri? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA
        )

        // Look for screenshots - common paths
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.Images.Media.DATA} LIKE ?"
        }
        val selectionArgs = arrayOf("%creenshot%")

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)
                return ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
            }
        }

        // Fallback: just get the latest image
        contentResolver.query(
            collection, arrayOf(MediaStore.Images.Media._ID),
            null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                return ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
            }
        }

        return null
    }

    private fun runOcr(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.ocr_processing)

        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            binding.tvStatus.text = "加载图片失败"
            return
        }

        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

        recognizer.process(image)
            .addOnSuccessListener { result ->
                binding.progressBar.visibility = View.GONE
                textBlocks = result.textBlocks
                allText = result.text

                if (textBlocks.isEmpty()) {
                    binding.tvStatus.text = getString(R.string.ocr_no_text)
                } else {
                    binding.tvStatus.text = "识别完成，点击文字选取"
                    binding.tvStatus.postDelayed({
                        binding.tvStatus.visibility = View.GONE
                    }, 2000)

                    binding.ocrOverlayView.visibility = View.VISIBLE
                    binding.ocrOverlayView.setTextBlocks(textBlocks)
                    binding.ocrOverlayView.setImageMatrix(
                        binding.zoomableImageView.getImageMatrix2()
                    )
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "识别失败: ${e.message}"
            }
    }

    private fun onImageTapped(imgX: Float, imgY: Float) {
        if (textBlocks.isEmpty()) return
        val element = binding.ocrOverlayView.findElementAt(imgX, imgY)
        if (element != null) {
            val currentText = binding.etOcrResult.text.toString()
            if (currentText.isEmpty()) {
                binding.etOcrResult.setText(element.text)
            } else {
                binding.etOcrResult.setText("$currentText ${element.text}")
            }
            binding.etOcrResult.setSelection(binding.etOcrResult.text.length)
        }
    }

    private fun setupButtons() {
        binding.btnCopy.setOnClickListener {
            val text = binding.etOcrResult.text.toString()
            if (text.isNotEmpty()) {
                copyToClipboard(text)
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopyAll.setOnClickListener {
            if (allText.isNotEmpty()) {
                copyToClipboard(allText)
                binding.etOcrResult.setText(allText)
                Toast.makeText(this, "已复制全部文字", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClear.setOnClickListener {
            binding.etOcrResult.setText("")
            binding.ocrOverlayView.clearSelection()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", text))
    }
}
