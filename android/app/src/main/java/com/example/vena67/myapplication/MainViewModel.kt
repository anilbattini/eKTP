package com.example.vena67.myapplication

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.graphics.Bitmap
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionCloudTextRecognizerOptions
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    lateinit var mSelectedImage: Bitmap

    fun runTextRecognition() {

        val image = FirebaseVisionImage.fromBitmap(mSelectedImage)
        /**
         * This uses onDevice model. Means, it doesn't send our data to any cloud.
         */
        val onDeviceRecognizer = FirebaseVision.getInstance()
                .onDeviceTextRecognizer
        // Or, to provide language hints to assist with language detection:
// See https://cloud.google.com/vision/docs/languages for supported languages
        val options = FirebaseVisionCloudTextRecognizerOptions.Builder()
                .setLanguageHints(Arrays.asList("en", "id"))
                .setModelType(FirebaseVisionCloudTextRecognizerOptions.DENSE_MODEL)
                .build()
        /**
         * This cloud model. Means, it sends our data to Firebase cloud to get the results with more accuracy.
         *
         */
//        val detector = FirebaseVision.getInstance().cloudTextRecognizer
// Or, to change the default settings:
        val cloudRecognizer = FirebaseVision.getInstance().getCloudTextRecognizer(options)
        scan_text.isEnabled = false
        val selectedRecognizer = if (radioGroup.checkedRadioButtonId == onDeviceRadio.id) onDeviceRecognizer else cloudRecognizer
        selectedRecognizer.processImage(image)
                .addOnSuccessListener { texts ->
                    scan_text.isEnabled = true
                    processTextRecognitionResult(texts)
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    scan_text.isEnabled = true
                    e.printStackTrace()
                }
    }

    fun setSelectedImage(bitmap: Bitmap) {
        mSelectedImage = bitmap
    }

}