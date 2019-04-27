package com.example.vena67.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionCloudTextRecognizerOptions
import com.google.firebase.ml.vision.text.FirebaseVisionCloudTextRecognizerOptions.DENSE_MODEL
import com.google.firebase.ml.vision.text.FirebaseVisionText
import com.yalantis.ucrop.UCrop
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.label_main.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var mSelectedImage: Bitmap
    private val APP_TAG = "MyApp ML Kit"
    private val CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1234
    private val PERMISSION_CAMERA = 10235
    private val PERMISSION_STORAGE = 10234
    private val PICK_PHOTO_CODE = 12345
    lateinit var photoFile: File
    var eKtpNumber: String = ""
    var dob: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        scan_text.isEnabled = false
        take_photo.setOnClickListener {
            resetValues()
            getCapturedImage()
        }
        choose_from_gallery.setOnClickListener {
            resetValues()
            chooseFromGallery()
        }
        scan_text.setOnClickListener {
            val radioButton = findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
            if (radioButton.text.isEmpty()) {
                Toast.makeText(this, "Select type of 'Text Recognizer' Model", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            resetValues()
            runTextRecognition()
        }
        requestStoragePermission()
    }

    private fun resetValues() {
        eKtpNumber = ""
        dob = ""
        editText.setText(eKtpNumber)
        editText2.setText(dob)
    }

    @SuppressLint("NewApi")
    private fun requestStoragePermission() {
        if (!havePermissions()) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf<String>(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_STORAGE)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun runTextRecognition() {
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
                .setModelType(DENSE_MODEL)
                .build()
        /**
         * This cloud model. Means, it sends our data to Firebase cloud to get the results with more accuracy.
         *
         */
//        val detector = FirebaseVision.getInstance().cloudTextRecognizer
// Or, to change the default settings:
        val cloudRecognizer = FirebaseVision.getInstance().getCloudTextRecognizer(options)
        scan_text.isEnabled = false
        val selectedRecognizer = if (radioGroup.checkedRadioButtonId == onDeviceRadio.id)  onDeviceRecognizer else cloudRecognizer
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

    private fun processTextRecognitionResult(texts: FirebaseVisionText?) {
        val blocks = texts?.textBlocks
        if (blocks?.size == 0) {
            showToast("No text found")
            return
        }
        for (i in blocks?.indices!!) {
            val lines = blocks[i].lines
            println("\n start indice ###\n")
            for (j in lines.indices) {
                val elements = lines[j].elements
                var lineElement = ""
                for (k in elements.indices) {
                    checkForEKTPNumber(elements[k].text)
                    lineElement += (elements[k].text)
//                    print(elements[k].text.toString().plus("   "))
                }
                checkForDOB(lineElement)
            }
            println("\nend indice ###\n")
        }
        enableLabelLayout()
    }

    private fun checkForEKTPNumber(text: String?) {
        if (eKtpNumber.isEmpty() && containsEKTPNumber(text!!)) {
            editText.setText(eKtpNumber)
        }
    }

    private fun checkForDOB(text: String?) {
        println("\n line element : $text")
        //This is a second check for eKTP with whole line of text from blocks
        if (eKtpNumber.isEmpty() && containsEKTPNumber(text!!)) {
            editText.setText(eKtpNumber)
        }
        if (dob.isEmpty() && containsDOB(text!!)) {
            editText2.setText(dob)
        }
    }

    private fun enableLabelLayout() {
        label_layout.visibility = View.VISIBLE
    }

    private fun enableRadioGroupLayout() {
        radio_group_layout.visibility = View.VISIBLE
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun getCapturedImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            photoFile = getPhotoFileUri("photo.jpg")
            val fileProvider = FileProvider.getUriForFile(this, "com.example.vena67.myapplication.fileprovider", photoFile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, fileProvider)
            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE)
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf<String>(Manifest.permission.CAMERA), PERMISSION_CAMERA)
        }
    }

    // Trigger gallery selection for a photo
    private fun chooseFromGallery() {
        // Create intent for picking a photo from the gallery
        val intent = Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, PICK_PHOTO_CODE)
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                cropSelectedPhoto(Uri.fromFile(photoFile))
            } else { // Result was a failure
                Toast.makeText(this, "Picture wasn't taken!", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == PICK_PHOTO_CODE) {
            if (data != null) {
                val photoUri = data.data
                // Do something with the photo based on Uri
                mSelectedImage = MediaStore.Images.Media.getBitmap(this.contentResolver, photoUri)
                // Load the selected image into a preview
                image_view.setImageBitmap(mSelectedImage)
                scan_text.isEnabled = true
            }
        } else if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            mSelectedImage = BitmapFactory.decodeFile(photoFile.absolutePath)
            scan_text.isEnabled = true
            enableRadioGroupLayout()
            image_view.setImageBitmap(mSelectedImage)
            saveFile(photoFile)
            print("size of final photo" + photoFile.length() / (1024))
        } else if (requestCode == PERMISSION_CAMERA) {
            getCapturedImage()
        }
    }

    private fun saveFile(fileToBeSaved: File) {
        if (!havePermissions()) return
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val generator = Random()
        var n = 10000
        n = generator.nextInt(n)
        val fName = "Image-$n.jpg"
        val file = File(path, fName)
        try {
            val stream = FileOutputStream(file, true)
            stream.write(fileToBeSaved.readBytes())
            stream.close()
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
            Log.i("saveData", "Data Saved")
        } catch (e: IOException) {
            Log.e("SAVE DATA", "Could not write file " + e.message)
        }
    }

    @SuppressLint("NewApi")
    private fun havePermissions(): Boolean {
        return (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }

    @SuppressLint("NewApi")
    private fun cropSelectedPhoto(fromFile: Uri) {
        val options = UCrop.Options()
        val colorId = getColor(R.color.colorPrimary)
        options.setToolbarColor(colorId)
        options.setStatusBarColor(getColor(R.color.colorPrimaryDark))
        options.setActiveWidgetColor(colorId)
        options.setMaxBitmapSize(400 * 1024)
        options.setCompressionQuality(100)
        UCrop.of(fromFile, fromFile)
                .withAspectRatio(4.8f, 3f)
                .withMaxResultSize(1020, 640)
                .withOptions(options)
                .start(this)
    }

    // Returns the File for a photo stored on disk given the fileName
    private fun getPhotoFileUri(fileName: String): File {
        // Get safe storage directory for photos
        // Use `getExternalFilesDir` on Context to access package-specific directories.
        // This way, we don't need to request external read/write runtime permissions.
        val mediaStorageDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), APP_TAG)
        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            Log.d(APP_TAG, "failed to create directory")
        }
        // Return the file target for the photo based on filename
        return File(mediaStorageDir.path + File.separator + fileName)
    }

    private fun correctImageRotation(photoFilePath: String) {
        // Create and configure BitmapFactory
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(photoFilePath, bounds)
        val opts = BitmapFactory.Options()
        val bm = BitmapFactory.decodeFile(photoFilePath, opts)
        // Read EXIF Data
        var exif: ExifInterface? = null
        try {
            exif = ExifInterface(photoFilePath)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val orientString = exif!!.getAttribute(ExifInterface.TAG_ORIENTATION)
        val orientation = if (orientString != null) Integer.parseInt(orientString) else ExifInterface.ORIENTATION_NORMAL
        var rotationAngle = 0
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotationAngle = 90
        if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotationAngle = 180
        if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotationAngle = 270
        // Rotate Bitmap
        val matrix = Matrix()
        matrix.setRotate(rotationAngle.toFloat(), bm.width.toFloat() / 2, bm.height.toFloat() / 2)
        val bitmap = Bitmap.createBitmap(bm, 0, 0, bounds.outWidth, bounds.outHeight, matrix, true)
        val file = File(photoFilePath) // the File to save , append increasing numeric counter to prevent files from getting overwritten.
        val fOut = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut) // saving the Bitmap to a file compressed as a JPEG with 85% compression rate
        fOut.flush() // Not really required
        fOut.close()
    }

    private fun containsEKTPNumber(value: String): Boolean {
        val newString = value?.replace("[^0-9]".toRegex(), "")
        if (newString?.length == 16) {
            eKtpNumber = newString
            return true
        }
        return false
    }

    /**
     * This case only looks for dates in numbers like 10-09-1985
     * Todo: Need to write logic to find dates with month in alphabets, like '10 november 1985'
     */
    private fun containsDOB(value: String): Boolean {
        if (-1 != value.indexOfAny(arrayListOf("tempat", "tgl", "Lahir"), ignoreCase = true) && value.contains("-")) {
            val stringArray = value.split("-")
            if (stringArray.size != 3) return false
            val dateValue = stringArray[0]?.replace("[^0-9]".toRegex(), "")
            val monthValue = stringArray[1]?.replace("[^0-9]".toRegex(), "")
            val yearValue = stringArray[2]?.replace("[^0-9]".toRegex(), "")
            dob = String.format("%s-%s-%s", dateValue, monthValue, yearValue)
            return true
        }
        return false
    }
}

