package com.karnataka.senseway

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class ObjectRecognizer(private val context: Context) {

    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification() // To get "Table", "Chair" etc.
            .build()
    )

    fun processImage(bitmap: Bitmap, onResult: (String) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        detector.process(image)
            .addOnSuccessListener { objects ->
                if (objects.isEmpty()) {
                    onResult("I cannot see anything clearly.")
                    return@addOnSuccessListener
                }

                val descriptions = objects.mapNotNull { obj ->
                    val label = obj.labels.firstOrNull()?.text
                    label
                }.joinToString(", ")

                if (descriptions.isNotEmpty()) {
                    onResult("I see: $descriptions")
                } else {
                    onResult("I see some objects but cannot name them.")
                }
            }
            .addOnFailureListener {
                onResult("Vision processing failed.")
            }
    }
}
