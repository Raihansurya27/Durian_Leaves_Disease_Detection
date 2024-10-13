package com.raihan.diseasedetection

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.raihan.diseasedetection.databinding.ActivityCameraBinding
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity(), ObjectDetectorHelper.DetectorListener {
    private lateinit var viewBinding: ActivityCameraBinding
    private lateinit var camera: Camera
    //    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    //    private lateinit var interpreter: Interpreter
    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var bitmapBuffer: Bitmap
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var orientationEventListener: OrientationEventListener
    private lateinit var tapGestureListener: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var outputDirectory: File

    // Animasi
    private lateinit var slideUp : Animation
    private lateinit var slideDown : Animation

    // Use Case
    private var imageCapture : ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview : Preview? = null
    private var isAnalysisEnabled = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        supportActionBar?.hide()

        // Izin kamera
        if(allPermissionsGranted()){
            // Tampilkan progress bar
            showProgressBar()

            // direktori foto
            outputDirectory = getOutputDirectory()

            // class ObjectDetectorHelper
            objectDetectorHelper = ObjectDetectorHelper(
                context = this@CameraActivity,
                objectDetectorListener = this
            )

            viewBinding.cameraView.post {
                startCamera()
            }

            cameraExecutor = Executors.newSingleThreadExecutor()

            // fitur Blur
            blurInit()

            // Rotasi Kamera
            rotationInit()
            orientationEventListener.enable()

            // Kamera Fokus
            focusInit()

            // Zoom Kamera
            zoomInit()

            // Kamera saat ditekan
            cameraTouch()

            // Animasi
            initAnimation()

            // Tombol di setting kamera
            initSettingDetection()

            // Tombol di kamera
            initButton()

            // Stop Analyzer
            stopAnalysis()

            // Hilangkan progress bar
            hideProgressBar()
        }else{
            requestPermissions()
        }
    }

    // Tampilkan progress bar
    private fun showProgressBar(){
        viewBinding.overlayProgressBar.visibility = View.VISIBLE
        viewBinding.cameraProgressBar.visibility = View.VISIBLE
    }

    // Hilangkan progress bar
    private fun hideProgressBar(){
        viewBinding.overlayProgressBar.visibility = View.GONE
        viewBinding.cameraProgressBar.visibility = View.GONE
    }

    // Tombol setting karmera
    private fun settingInit(){
        viewBinding.cameraSettingButton.setOnClickListener {

            when (viewBinding.settingNavigation.visibility) {
                View.VISIBLE -> {
                    viewBinding.settingNavigation.startAnimation(slideUp)
                    slideUp.setAnimationListener(object : Animation.AnimationListener{
                        override fun onAnimationStart(animation: Animation?) {
                            viewBinding.cameraSettingButton.setBackgroundResource(R.drawable.setting_off_new)
                        }

                        override fun onAnimationEnd(animation: Animation?) {
                            viewBinding.settingNavigation.visibility = View.GONE
                        }

                        override fun onAnimationRepeat(animation: Animation?) {

                        }

                    })
                }

                View.GONE -> {
                    viewBinding.settingNavigation.startAnimation(slideDown)
                    slideDown.setAnimationListener(object: Animation.AnimationListener{
                        override fun onAnimationStart(animation: Animation?) {
                            viewBinding.cameraSettingButton.setBackgroundResource(R.drawable.setting_on_new)
                        }

                        override fun onAnimationEnd(animation: Animation?) {
                            viewBinding.settingNavigation.visibility = View.VISIBLE
                        }

                        override fun onAnimationRepeat(animation: Animation?) {

                        }

                    })
                }

                View.INVISIBLE -> {
                    // Fungsi lain
                }
            }
        }
    }

    // Animasi
    private fun initAnimation(){
        // Animasi
        slideDown = AnimationUtils.loadAnimation(this, R.anim.slide_down_fade_in)
        slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_out)
    }

    // Tombol di kamera
    private fun initButton(){
        // Ambil Foto
        viewBinding.cameraShotButton.setOnClickListener {
            takePhoto()
            showProgressBar()
        }

        // Flash Kamera
        viewBinding.cameraFlashButton.setOnClickListener {
            flash()
        }

        // Atur Kamera
        settingInit()

        // Atur deteksi objek
        viewBinding.cameraDetectionButton.setOnClickListener {
            isAnalysisEnabled = !isAnalysisEnabled
            if(isAnalysisEnabled){
                startAnalysis()
                viewBinding.cameraDetectionButton.setBackgroundResource(R.drawable.ai_on_new)
                viewBinding.clearAnalyzeButton.visibility = View.GONE
            } else {
                stopAnalysis()
                viewBinding.cameraDetectionButton.setBackgroundResource(R.drawable.ai_off_new)
                viewBinding.clearAnalyzeButton.visibility = View.VISIBLE
            }
        }


        // clear Analyze
        viewBinding.clearAnalyzeButton.setOnClickListener {
            clearAnalize()
        }
    }

    // fungsi tombol setting kamera
    private fun initSettingDetection(){
        // Threshold
        viewBinding.thresholdSubstractButton.setOnClickListener {
            if(objectDetectorHelper.thershold >= 0.1){
                objectDetectorHelper.thershold -= 0.1f
                updateControlsUi()
            }
        }

        viewBinding.thresholdPlusButton.setOnClickListener {
            if (objectDetectorHelper.thershold <= 0.8) {
                objectDetectorHelper.thershold += 0.1f
                updateControlsUi()
            }
        }

        // Threads
        viewBinding.threadsPlusButton.setOnClickListener {
            if(objectDetectorHelper.numThreads < 4) {
                objectDetectorHelper.numThreads ++
                updateControlsUi()
            }
        }

        viewBinding.threadsSubstractButton.setOnClickListener {
            if(objectDetectorHelper.numThreads > 1){
                objectDetectorHelper.numThreads --
                updateControlsUi()
            }
        }

        // num of detection
        viewBinding.resultSubstractButton.setOnClickListener {
            if(objectDetectorHelper.maxResult > 1) {
                objectDetectorHelper.maxResult--
                updateControlsUi()
            }
        }

        viewBinding.resultPlusButton.setOnClickListener {
            if(objectDetectorHelper.maxResult < 5) {
                objectDetectorHelper.maxResult ++
                updateControlsUi()
            }
        }

        // Delegate CPU
        viewBinding.delegateSpinner.setSelection(0, false)
        viewBinding.delegateSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    objectDetectorHelper.currentDelegate = position
                    updateControlsUi()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {

                }

            }

        // ML Model
        viewBinding.modelSpinner.setSelection(0, false)
        viewBinding.modelSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener{
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    objectDetectorHelper.currentModel = position
                    updateControlsUi()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {

                }

            }
    }

    // kamera saat ditekan
    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun cameraTouch(){
        viewBinding.cameraView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            if(scaleGestureDetector.isInProgress){
                val currentZoomRatio : Float = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                val decimalFormat = DecimalFormat("#.#")
                val formattedNumber = decimalFormat.format(currentZoomRatio)
                viewBinding.textZoomState.apply{
                    text = "x${formattedNumber}"
                    visibility = View.VISIBLE
                }
            } else{
                tapGestureListener.onTouchEvent(event)
                viewBinding.textZoomState.apply{
                    visibility = View.GONE
                }
            }
            if (viewBinding.settingNavigation.visibility == View.VISIBLE) {
                viewBinding.settingNavigation.startAnimation(slideUp)
                slideUp.setAnimationListener(object : Animation.AnimationListener{
                    override fun onAnimationStart(animation: Animation?) {
                        viewBinding.cameraSettingButton.setBackgroundResource(R.drawable.setting_off_new)
                    }

                    override fun onAnimationEnd(animation: Animation?) {
                        viewBinding.settingNavigation.visibility = View.GONE
                    }

                    override fun onAnimationRepeat(animation: Animation?) {

                    }
                })
            }
            true
        }
    }

    // kamera fokus
    private fun focusInit(){
        tapGestureListener = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener(){
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                e.let {
                    val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
                        viewBinding.focusPosition.width.toFloat(),
                        viewBinding.focusPosition.height.toFloat()
                    )

                    val focusMeteringAction: FocusMeteringAction = FocusMeteringAction.Builder(
                        factory.createPoint(e.x, e.y), FocusMeteringAction.FLAG_AF
                    ).build()

                    try{
                        camera.cameraControl.startFocusAndMetering(focusMeteringAction)

                        // Ukuran Fokus
                        val height = viewBinding.focusPosition.height.toFloat()
                        val width = viewBinding.focusPosition.width.toFloat()

                        val x = e.x
                        val y = e.y
                        val titikFokusX = x - (width / 2)
                        val titikFokusY = y - (height / 2)

                        // Titik Fokus
                        viewBinding.focusPosition.translationX = titikFokusX
                        viewBinding.focusPosition.translationY = titikFokusY


//                        val message = "Posisi jari ${x}, ${y} " +
//                                "dan posisi titik fokus ${titikFokusX}, ${titikFokusY}" +
//                                "Lebar ${viewBinding.cameraView.width} dan tinggi ${viewBinding.cameraView.height}"
//
//                        Toast.makeText(baseContext, message, Toast.LENGTH_LONG).show()

                        viewBinding.focusPosition.visibility = View.VISIBLE

                        // Sembunyikan kembali setelah beberapa detik
                        val handler = Handler(Looper.getMainLooper())

                        handler.postDelayed({
                            viewBinding.focusPosition.visibility = View.GONE
                        }, 3000)

                    }catch (e: CameraControl.OperationCanceledException){
                        Log.e(TAG, "error: ${e.message}")
                        // Buat Teks Error Log di folder beserta waktu
                        Toast.makeText(
                            this@CameraActivity,
                            "Error: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                return true
            }
        })
    }

    // kamera zoom
    private fun zoomInit(){
        scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener(){
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scaleFactor = detector.scaleFactor
                    val currentZoomRatio : Float = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    val newZoomRation = scaleFactor * currentZoomRatio
                    camera.cameraControl.setZoomRatio(newZoomRation)

                    return true
                }
            }
        )
    }

    // Rotasi Kamera
    private fun rotationInit(){
        orientationEventListener = object : OrientationEventListener(this){
            override fun onOrientationChanged(orientation: Int) {
                // Monitors orientation values to determine the target rotation value
                when (orientation) {
                    // Landscape
                    in 45 .. 134 -> {
                        imageCapture?.targetRotation = Surface.ROTATION_270
                        rotateButtons(270f)
                    }
                    // Potrait Terbalik
                    in 135 .. 224 -> {
                        imageCapture?.targetRotation = Surface.ROTATION_180
                        rotateButtons(180f)
                    }
                    // Landscape Terbalik
                    in 225 .. 314 -> {
                        imageCapture?.targetRotation = Surface.ROTATION_90
                        rotateButtons(90f)
                    }
                    // Portrait
                    in 0 .. 44 -> {
                        imageCapture?.targetRotation = Surface.ROTATION_0
                        rotateButtons(0f)
                    }
                }
            }

            // Metode untuk memutar tombol sesuai orientasi
            private fun rotateButtons(rotation: Float){
                val objectAnimatorGroup = AnimatorSet()

                val flashObject =
                    ObjectAnimator.ofFloat(viewBinding.cameraFlashButton, "rotation", rotation)
                flashObject.duration = 100

                val detectionObject =
                    ObjectAnimator.ofFloat(viewBinding.cameraDetectionButton, "rotation", rotation)
                detectionObject.duration = 100

                val settingObject =
                    ObjectAnimator.ofFloat(viewBinding.cameraSettingButton, "rotation", rotation)
                settingObject.duration = 100

                val takePictureObject =
                    ObjectAnimator.ofFloat(viewBinding.cameraShotButton, "rotation", rotation)
                takePictureObject.duration = 100

                objectAnimatorGroup.playTogether(
                    flashObject,
                    detectionObject,
                    settingObject,
                    takePictureObject)

                objectAnimatorGroup.start()
            }

        }
    }

    // Update nilai dari setting kamera dan reset detector
    private fun updateControlsUi(){

        viewBinding.resultTextView.text = objectDetectorHelper.maxResult.toString()
        viewBinding.thresholdTextView.text = String.format("%.2f", objectDetectorHelper.thershold)
        viewBinding.threadsTextView.text = objectDetectorHelper.numThreads.toString()

        // Needs to be cleared instead of reinitialized because the GPU
        // delegate needs to be initialized on the thread using it when applicable
        objectDetectorHelper.clearObjectDetector()
        viewBinding.cameraOverlay.clear()
    }

    // Blur
    private fun blurInit(){
        viewBinding.topNavigation.bringToFront()
        viewBinding.bottomNavigation.bringToFront()

        applyBlurEffect(viewBinding.topNavigation)
        applyBlurEffect(viewBinding.bottomNavigation)
        applyBlurEffect(viewBinding.settingNavigation)
    }

    private fun applyBlurEffect(view: View){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            view.setRenderEffect(
                RenderEffect.createBlurEffect(10f, 10f, Shader.TileMode.CLAMP)
            )
        }
    }

    // Perizinan Kamera
    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){
            permissions ->
        // Handle Permission granted/rejected
        var permissionGranted = true
        permissions.entries.forEach {
            if(it.key in REQUIRED_PERMISSIONS && !it.value)
                permissionGranted = false
        }
        if(!permissionGranted){
            Toast.makeText(baseContext, "Permission request denied", Toast.LENGTH_SHORT).show()
        }else{
            startCamera()
        }
    }

    private fun flash(){
        val cameraInfo = camera.cameraInfo
        val cameraControl = camera.cameraControl
        if(cameraInfo.hasFlashUnit()){
            // Menghidupkan flashlight kamera
            if (cameraInfo.torchState.value == TorchState.OFF){
                cameraControl.enableTorch(true)
                viewBinding.cameraFlashButton.setBackgroundResource(R.drawable.flash_on_new)
            }
            // mematikan flashlight kamera
            else {
                cameraControl.enableTorch(false)
                viewBinding.cameraFlashButton.setBackgroundResource(R.drawable.flash_off_new)
            }
        }else{
            Toast.makeText(baseContext, "Perangkat ini tidak memiliki flashlight atau flashlight rusak", Toast.LENGTH_SHORT).show()
        }
    }

    private fun takePhoto(){
        val params = viewBinding.cameraShotButton.layoutParams
        params.height = 50
        params.width = 50
        viewBinding.cameraShotButton.layoutParams = params

        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
//        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
//        val contentValues = ContentValues().apply {
//            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
//            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
//            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P){
//                put(MediaStore.Images.Media.RELATIVE_PATH, "Picture/CameraX-Image")
//            }
//        }

        // Create output options object which contains file + metadata
//        val outputOptions = ImageCapture.OutputFileOptions.Builder(
//            contentResolver,
//            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
//        ).build()

        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback{
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val message = "Photo capture succeeded : ${outputFileResults.savedUri}"
                    Toast.makeText(baseContext, message, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, message)

                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)

                    // Panggil fungsi untuk deteksi objek pada gambar yang diambil
                    detectObjectsFromBitmap(bitmap, photoFile)

                    sendResultActivity(outputFileResults.savedUri.toString())

//                    imageCapture.takePicture(
//                        ContextCompat.getMainExecutor(this@CameraActivity),
//                        object : ImageCapture.OnImageCapturedCallback(){
//                            override fun onCaptureSuccess(image: ImageProxy) {
////                                super.onCaptureSuccess(image)
//                                detectObjects(image)
//
//                                // Handler
//
//
//                                image.close()
//                            }
//
//                            override fun onError(exception: ImageCaptureException) {
//                                Toast.makeText(baseContext, "Failed to capture image : ${exception.message}", Toast.LENGTH_SHORT).show()
//                            }
//                        }
//                    )
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture error: ${exception.message}", exception)
                }

            }
        )

        params.height = 80
        params.width = 80
        viewBinding.cameraShotButton.layoutParams = params
        hideProgressBar()
    }

    private fun sendResultActivity(originalImagePath:String, detectedImagePath:String){
        val intent = Intent(this, ResultActivity::class.java)
        val photoPaths = getPhotoPaths().toMutableList()
        photoPaths.add(originalImagePath)
        photoPaths.add(detectedImagePath)
        intent.putStringArrayListExtra("PHOTO_PATHS", ArrayList(photoPaths))
        startActivity(intent)
    }

    private fun getOutputDirectory(): File{
        val mediaDir = externalMediaDirs.firstOrNull()?.let{
            File(it, resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }
        return if(mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun getPhotoPaths(): List<String>{
        val photoDir = externalMediaDirs.firstOrNull()
        return photoDir?.listFiles()?.map { it.absolutePath }?.toList() ?: emptyList()
    }

    private fun saveBitmap(bitmap: Bitmap, filename:String): String? {
        val dir = File(outputDirectory, "DetectedImages").apply {
            if(!exists()) mkdirs()
        }
        val file = File(dir, filename)
        try{
            FileOutputStream(file).use {
                out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
        } catch (e: IOException){
//            e.printStackTrace()
            Log.e(TAG, "Error saving bitmap", e)
        }
        return file.absolutePath
    }

    private fun startCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()
            // Preview
            preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(viewBinding.cameraView.display.rotation)
                .build()
                .also{
                    it.setSurfaceProvider(viewBinding.cameraView.surfaceProvider)
                }

            viewBinding.cameraView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

            // Image Capture
            imageCapture = ImageCapture.Builder().build()

            // Image Analyze
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(viewBinding.cameraView.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
//                .also {
//                    it.setAnalyzer(cameraExecutor){
//                        image ->
//                        if(!::bitmapBuffer.isInitialized){
//                            bitmapBuffer = Bitmap.createBitmap(
//                                image.width,
//                                image.height,
//                                Bitmap.Config.ARGB_8888
//                            )
//                        }
//
//                        detectObjects(image)
//                    }
//                }

            // kamera belakang sebagai default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try{
                // Lepas semua use case sebelum rebinding
                cameraProvider.unbindAll()

                // Bind use case ke kamera
                // Ambil foto
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )

                camera.cameraControl.enableTorch(false)

            } catch (exc: Exception){
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startAnalysis(){
//        imageAnalyzer?.setAnalyzer(Executors.newSingleThreadExecutor(), ImageAnalyzer())
        imageAnalyzer?.setAnalyzer(cameraExecutor){
            if(!::bitmapBuffer.isInitialized){
                bitmapBuffer = Bitmap.createBitmap(
                    it.width,
                    it.height,
                    Bitmap.Config.ARGB_8888
                )
            }
            detectObjects(it)
        }
        viewBinding.cameraSettingButton.isEnabled = true
        viewBinding.cameraSettingButton.setBackgroundResource(R.drawable.setting_off_new)
        viewBinding.cameraSettingButton.visibility = View.VISIBLE
    }

    private fun stopAnalysis(){
        imageAnalyzer?.clearAnalyzer()
        viewBinding.cameraSettingButton.visibility = View.GONE
        viewBinding.cameraSettingButton.setBackgroundResource(R.drawable.setting_off_new)
    }

    private fun clearAnalize(){
        viewBinding.cameraOverlay.clearResults()
        viewBinding.clearAnalyzeButton.visibility = View.GONE
    }

//    private inner class ImageAnalyzer: ImageAnalysis.Analyzer{
//        override fun analyze(image: ImageProxy) {
//            if(!::bitmapBuffer.isInitialized){
//                bitmapBuffer = Bitmap.createBitmap(
//                    image.width,
//                    image.height,
//                    Bitmap.Config.ARGB_8888
//                )
//            }
//            detectObjects(image)
//        }
//    }

    private fun requestPermissions(){
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all{
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    // Ketika keluar dari halaman camera
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }


    private fun detectObjects(image: ImageProxy){
        // Copy out RGB bits to the shared bitmap buffer
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        val imageRotation = image.imageInfo.rotationDegrees
        // Pass Bitmap and rotation to the object detector helper for processing and detection
        objectDetectorHelper.detect(bitmapBuffer, imageRotation)
    }

    companion object{
        private const val TAG = "Disease Detection"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
            ).apply{
                if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P){
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private fun detectObjectsFromBitmap(bitmap: Bitmap, photoFile: File){
        val imageRotation = image.imageInfo.rotationDegrees
    }

    override fun onError(error: String) {
        this.runOnUiThread{
            Toast.makeText(this,error, Toast.LENGTH_SHORT).show()
        }
    }

    // Update UI after objects have been detected. Extracts original image height/width
    // to scale and place bounding boxes properly through OverlayView
    override fun onResults(
        results: MutableList<Detection>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        this.runOnUiThread{
            // Inference Time
            viewBinding.inferenceTimeTextView.text = String.format("%d ms", inferenceTime)

            // Pass necessary information to OverlayView for Drawing on th canvas
            viewBinding.cameraOverlay.setResults(
                results ?: LinkedList<Detection>(),
                imageHeight,
                imageWidth
            )

            // Force a redraw
            viewBinding.cameraOverlay.invalidate()
        }
    }
}