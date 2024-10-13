package com.raihan.diseasedetection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class ObjectDetectorHelper(
    var thershold: Float = 0.5f,
    var numThreads: Int = 2,
    var maxResult: Int = 3,
    var currentDelegate: Int = 0,
    var currentModel: Int = 0,
    val context: Context,
    val objectDetectorListener: DetectorListener?
) {

    private var objectDetector: ObjectDetector? = null

    init {
        setupObjectDetector()
    }

    fun clearObjectDetector(){
        objectDetector = null
    }

    // Initialize the object detector using current settings on the
    // thread that is using it. CPU and NNAPI delegates can be used with detectors
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the detector
    fun setupObjectDetector() {
        // Create the base options for the detector using specifies max results and score threshold
        val optionBuilder = ObjectDetector.ObjectDetectorOptions.builder()
            .setScoreThreshold(thershold)
            .setMaxResults(maxResult)

        // Set general detections, including number of used threads
        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)

        // Use the specific hardware for running the model. Default to CPU
        when(currentDelegate){
            DELEGATE_CPU -> {
                // Default
            }
            DELEGATE_GPU -> {
                if (CompatibilityList().isDelegateSupportedOnThisDevice){
                    baseOptionsBuilder.useGpu()
                } else {
                    objectDetectorListener?.onError("GPU tidak mendukung pada perangkat ini.")
                }
            }
            DELEGATE_NNAPI -> {
                baseOptionsBuilder.useNnapi()
            }
        }

        optionBuilder.setBaseOptions(baseOptionsBuilder.build())

        // Model Deteksi
        val model_name =
            when(currentModel) {
                MODEL_MOBILENETV1 -> "mobilenetv1.tflite"
                MODEL_EFFICIENTDETV0 -> "efficientdet-lite0.tflite"
                else -> "mobilenetv1.tflite"
            }

        try{
            objectDetector =
                ObjectDetector.createFromFileAndOptions(context, model_name, optionBuilder.build())
        } catch (e: IllegalStateException){
            objectDetectorListener?.onError(
                "Object Detector gagal diinisialisasi. Lihat error logs untuk detail"
            )
            Log.e("Test", "TFLite gagal memuat model dengan error: " + e.message)
        }
    }

    fun detect(image: Bitmap, imageRotation: Int){
        if(objectDetector == null){
            setupObjectDetector()
        }

        // Inference time adalah perbedaan antara waktu sistem saat proses mulai dan selesai

        var inferenceTime = SystemClock.uptimeMillis()

        // Create preprocessor for the image.
        // See https://www.tensorflow.org/lite/inference_with_metadata/
        //            lite_support#imageprocessor_architecture
        val imageProcessor =
            ImageProcessor.Builder()
                .add(Rot90Op(-imageRotation / 90))
                .build()

        // Preprocess the image and convert it into a TensorImage for detection.
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        val results = objectDetector?.detect(tensorImage)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        objectDetectorListener?.onResults(
            results,
            inferenceTime,
            tensorImage.height,
            tensorImage.width
        )
    }

    fun shotDetect(){

    }

    interface DetectorListener{
        fun onError(error: String)

        fun onResults(
            results: MutableList<Detection>?,
            inferenceTime: Long,
            imageHeight: Int,
            imageWidth: Int
        )
    }

    companion object{
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DELEGATE_NNAPI = 2
        const val MODEL_MOBILENETV1 = 0
        const val MODEL_EFFICIENTDETV0 = 1
    }
}