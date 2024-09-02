package com.henrasta.opad.ui.home

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import com.henrasta.opad.R
import com.henrasta.opad.databinding.FragmentHomeBinding
import java.util.Locale
import android.media.ExifInterface
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val user = Firebase.auth.currentUser
    private val storage = FirebaseStorage.getInstance()
    private val db = Firebase.firestore

    private lateinit var storageReference: StorageReference
    private val documentRef = db.collection("Users").document(user?.uid ?: "").collection("Photos")

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    private var imageUri: Uri? = null

    interface OnImageUploadListener {
        fun onImageUploaded()
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = uriToBitmap(it)
            binding.image.setImageBitmap(bitmap)
            uploadImageToFirebase(bitmap!!)
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success) {
            //setExifOrientation(imageUri!!, ExifInterface.ORIENTATION_ROTATE_180)
            //logAllExifData(imageUri!!)
            val bitmap = uriToBitmap(imageUri!!)
            binding.image.setImageBitmap(bitmap)
            uploadImageToFirebase(bitmap!!)
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true &&
            (Build.VERSION.SDK_INT > Build.VERSION_CODES.P || permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true)) {
            imageUri?.let {
                takePictureLauncher.launch(it)
            }
            Log.i("Permission", "Camera and Storage Granted")
        } else {
            Toast.makeText(context, "Camera or storage permission denied. Unable to take pictures.", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.i("Permission", "Storage Granted")
            pickImageLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "Storage permission denied. Unable to read/write images.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logAllExifData(uri: Uri) {
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            inputStream?.use {
                val exif = ExifInterface(it)
                val tags = listOf(
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_FLASH,
                    ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_IMAGE_LENGTH,
                    ExifInterface.TAG_IMAGE_WIDTH,
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.TAG_WHITE_BALANCE
                )
                for (tag in tags) {
                    val value = exif.getAttribute(tag)
                    Log.d("ExifData", "$tag: $value")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view = binding.root

        // Set formatted date
        val formattedDate = getFormattedDateWithSuffix(LocalDate.now())
        binding.date.text = formattedDate
        startDateUpdater()

        val formattedDate2 = LocalDate.now().format(DateTimeFormatter.ofPattern("MM.dd.yy"))
        documentRef.document(formattedDate2).get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val imageUrl = document.getString("url")
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this).load(imageUrl).into(binding.image)
                }
            }
        }.addOnFailureListener { exception ->
            Log.w("LoadImage", "Error getting document", exception)
        }

        binding.selectPicBtn.setOnClickListener {
            Log.i("Permission", "Select Picture Button Clicked")
            checkAndRequestStoragePermission()
        }

        binding.takePicBtn.setOnClickListener {
            Log.i("Permission", "Take Picture Button Clicked")
            checkAndRequestCameraPermission()
        }

        calculateStreak()
        return view
    }

    private fun checkAndRequestCameraPermission() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // API 28 and lower
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val permissionStatus = permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        if (permissionStatus) {
            // Create a file to save the image
            createImageUri()?.let {
                imageUri = it
                takePictureLauncher.launch(it)
            }
        } else {
            requestCameraPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun setExifOrientation(uri: Uri, orientation: Int) {
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            inputStream?.use {
                val exif = ExifInterface(it)
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                exif.saveAttributes()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val permissionStatus = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
            if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                pickImageLauncher.launch("image/*")
            } else {
                requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            pickImageLauncher.launch("image/*")
        }
    }


    private fun createImageUri(): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.ORIENTATION, ExifInterface.ORIENTATION_ROTATE_180)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29 and above
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }
        val uri = requireContext().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        return uri
    }

    private fun getFormattedDateWithSuffix(date: LocalDate): String {
        val dayOfMonth = date.dayOfMonth
        val suffix = when (dayOfMonth) {
            1, 21, 31 -> "st"
            2, 22 -> "nd"
            3, 23 -> "rd"
            else -> "th"
        }
        val dateFormat = DateTimeFormatter.ofPattern("MMMM d'$suffix'")
        return date.format(dateFormat)
    }

    // Don't think these are necessary anymore, keeping in case
    /* private fun saveImageToGallery(bitmap: Bitmap) {
        val currentTimeMillis = System.currentTimeMillis()
        val filename = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".png"
        val resolver: ContentResolver = requireContext().contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DATE_ADDED, currentTimeMillis / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, currentTimeMillis)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }

        try {
            val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it).use { stream ->
                    if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream!!)) {
                        // Write EXIF metadata
                        setExifMetadata(resolver, it, currentTimeMillis)
                        Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setExifMetadata(resolver: ContentResolver, uri: Uri, timestamp: Long) {
        try {
            resolver.openFileDescriptor(uri, "rw")?.use { fileDescriptor ->
                val exifInterface = ExifInterface(fileDescriptor.fileDescriptor)
                val formatter = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                val dateString = formatter.format(Date(timestamp))
                exifInterface.setAttribute(ExifInterface.TAG_DATETIME, dateString)
                exifInterface.saveAttributes()
            }
        } catch (e: IOException) {
            Log.e("PictureSelectionFragment", "Error setting EXIF metadata", e)
        }
    } */

    private fun startDateUpdater() {
        runnable = Runnable {
            val formattedDate = getFormattedDateWithSuffix(LocalDate.now())
            if (binding.date.text.toString() != formattedDate) {
                binding.date.text = formattedDate
                binding.image.setImageResource(R.drawable.baseline_add_photo_alternate_24)
            }
            handler.postDelayed(runnable, 60000) // Check every minute
        }
        handler.post(runnable)
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val contentResolver: ContentResolver = requireContext().contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error converting URI to Bitmap", e)
            null
        }
    }

    private fun uploadImageToFirebase(bitmap: Bitmap) {
        val formattedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MM.dd.yy", Locale.getDefault()))
        storageReference = storage.reference.child("Users/" + user?.uid + "/DailyPictures/$formattedDate.png")
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val data = outputStream.toByteArray()

        val metadata = StorageMetadata.Builder().setCustomMetadata("caption", "test").build()
        val uploadTask = storageReference.putBytes(data, metadata)

        uploadTask.addOnSuccessListener {
            storageReference.downloadUrl.addOnSuccessListener { uri ->
                val downloadUrl = uri.toString()
                val imageInfo = hashMapOf(
                    "url" to downloadUrl,
                    "date" to formattedDate,
                    "userId" to user?.uid
                )

                documentRef.document(formattedDate).set(imageInfo, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("Upload", "DocumentSnapshot successfully written!")
                        (activity as? OnImageUploadListener)?.onImageUploaded()
                        calculateStreak()
                    }
                    .addOnFailureListener { e ->
                        Log.w("Upload", "Error writing document", e)
                    }
            }.addOnFailureListener { exception ->
                Log.w("Upload", "Error getting download URL", exception)
            }
        }.addOnFailureListener { exception ->
            Log.w("Upload", "Image upload failed", exception)
        }
    }

    private fun calculateStreak() {
        documentRef.get().addOnSuccessListener { documents ->
            val dates = documents.mapNotNull { it.getString("date") }
            val streak = calculateCurrentStreak(dates)
            binding.streakCounter.text = streak.toString()
        }.addOnFailureListener { e ->
            Log.w("HomeFragment", "Error getting documents: ", e)
        }
    }

    private fun calculateCurrentStreak(dates: List<String>): Int {
        if (dates.isEmpty()) return 0

        val formatter = DateTimeFormatter.ofPattern("MM.dd.yy", Locale.getDefault())
        val dateSet = dates.map { LocalDate.parse(it, formatter) }.toSortedSet()

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        var streak = 0
        var currentDate = today

        // If today is not in dateSet but yesterday is, start streak from yesterday
        if (!dateSet.contains(today) && dateSet.contains(yesterday)) {
            currentDate = yesterday
        }

        while (dateSet.contains(currentDate)) {
            streak++
            currentDate = currentDate.minusDays(1)
        }

        return streak
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(runnable)
        _binding = null
    }
}