package com.henrasta.opad

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.henrasta.opad.databinding.FragmentPictureUploadBinding
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.DateTimeParseException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

class PictureSelectionFragment : Fragment() {

    private var _binding: FragmentPictureUploadBinding? = null
    private val binding get() = _binding!!

    private val user = Firebase.auth.currentUser
    private val storage = FirebaseStorage.getInstance()
    private val db = Firebase.firestore

    private lateinit var storageReference: StorageReference
    private val documentRef = db.collection("Users").document(user?.uid ?: "").collection("Photos")

    private lateinit var selectedDate: LocalDate

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = uriToBitmap(it)

            // Get the image date from Exif metadata
            val imageDate = getImageDate(requireContext(), it)

            if (imageDate != null && imageDate == selectedDate) {
                // Dates match, proceed with upload
                binding.image.setImageBitmap(bitmap)
                uploadImageToFirebase(bitmap!!)
            } else {
                // Dates don't match, show error message
                Toast.makeText(requireContext(), "Selected date does not match image date", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPictureUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)?.visibility = View.GONE

        selectedDate = LocalDate.parse(arguments?.getString("selectedDate"))

        val formattedDate = getFormattedDateWithSuffix(selectedDate)
        binding.date.text = formattedDate

        fetchAndDisplayImage()

        binding.replacePicBtn.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.backButton.setOnClickListener {
            val fragmentManager = (activity as? AppCompatActivity)?.supportFragmentManager

            // Restore the bottom nav visibility
            (activity as? MainActivity)?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)?.visibility = View.VISIBLE
            val calendarFragment = parentFragmentManager.findFragmentByTag("calendarFragment")

            fragmentManager!!.beginTransaction().apply {
                fragmentManager.popBackStack()
                detach(calendarFragment!!)
                attach(calendarFragment)
            }.commit()
        }
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

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val contentResolver: ContentResolver = requireContext().contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e("PictureSelectionFragment", "Error converting URI to Bitmap", e)
            null
        }
    }

    private fun uploadImageToFirebase(bitmap: Bitmap) {
        val formattedDate = selectedDate.format(DateTimeFormatter.ofPattern("MM.dd.yy", Locale.getDefault()))
        storageReference = storage.reference.child("Users/" + user?.uid + "/DailyPictures/$formattedDate.png")
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val data = outputStream.toByteArray()

        val metadata = StorageMetadata.Builder()
            .setCustomMetadata("caption", "test")
            .build()

        val uploadTask = storageReference.putBytes(data, metadata)

        uploadTask.addOnSuccessListener {
            storageReference.downloadUrl.addOnSuccessListener { uri ->
                val downloadUrl = uri.toString()

                // Create the data to be saved
                val imageInfo = hashMapOf(
                    "url" to downloadUrl,
                    "date" to formattedDate,
                    "userId" to user?.uid
                )

                documentRef.document(formattedDate).set(imageInfo, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("Upload", "DocumentSnapshot successfully written!")
                        //(activity as? HomeFragment.OnImageUploadListener)?.onImageUploaded() // causing issues
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

    private fun fetchAndDisplayImage() {
        val formattedDate = selectedDate.format(DateTimeFormatter.ofPattern("MM.dd.yy", Locale.getDefault()))
        storageReference = storage.reference.child("Users/" + user?.uid + "/DailyPictures/$formattedDate.png")

        storageReference.downloadUrl.addOnSuccessListener { uri ->
            Glide.with(this)
                .load(uri)
                .into(binding.image)
        }.addOnFailureListener { exception ->
            Log.w("FetchImage", "Error getting download URL", exception)
            // Set placeholder if there is an error
            binding.image.setImageResource(R.drawable.baseline_add_photo_alternate_24)
        }
    }

    private fun getImageDate(context: Context, uri: Uri): LocalDate? {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            val exifInterface = ExifInterface(inputStream!!)

            // Retrieve the date information from ExifInterface
            val dateString = exifInterface.getAttribute(ExifInterface.TAG_DATETIME)
            Log.d("PictureSelectionFragment", "Date: $dateString")
            if (dateString.isNullOrEmpty()) {
                Toast.makeText(context, "Error finding date", Toast.LENGTH_SHORT).show()
                return null
            }

            val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
            val localDateTime = LocalDateTime.parse(dateString, dateTimeFormatter)
            val localDate = localDateTime.toLocalDate()

            Log.d("PictureSelectionFragment", "LocalDate: $localDate")
            return localDate

        } catch (e: IOException) {
            Log.e("PictureSelectionFragment", "Error reading image", e)
        } catch (e: DateTimeParseException) {
            Log.e("PictureSelectionFragment", "Error parsing date", e)
        } finally {
            inputStream?.close()
        }
        return null
    }

    companion object {
        fun newInstance(selectedDate: String): PictureSelectionFragment {
            val fragment = PictureSelectionFragment()
            val args = Bundle()
            args.putString("selectedDate", selectedDate)
            fragment.arguments = args
            return fragment
        }
    }
}
