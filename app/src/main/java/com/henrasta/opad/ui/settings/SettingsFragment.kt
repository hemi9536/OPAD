package com.henrasta.opad.ui.settings

import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.henrasta.opad.LoginActivity
import com.henrasta.opad.NotificationReceiver
import com.henrasta.opad.databinding.FragmentSettingsBinding
import java.util.Calendar


class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val storage = FirebaseStorage.getInstance()
    private val db = Firebase.firestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.logoutBtn.setOnClickListener {
            Firebase.auth.signOut()
            val intent = Intent(activity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        binding.infoBtn.setOnClickListener {
            showInfoDialog()
        }

        binding.GitHubBtn.setOnClickListener {

        }


        // Notifications, may try to fix later
        //timeLabel = view.findViewById(R.id.timeLabel)
        //selectTimeButton = view.findViewById(R.id.selectTimeBtn)

        /*val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val savedHour = sharedPreferences.getInt("notification_hour", 9) // Default hour is 9
        val savedMinute = sharedPreferences.getInt("notification_minute", 0) // Default minute is 0

        timeLabel.text = String.format("Notification Time: %02d:%02d", savedHour, savedMinute) */

        /*selectTimeButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            val hour = savedHour
            val minute = savedMinute

            TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
                timeLabel.text = String.format("Notification Time: %02d:%02d", selectedHour, selectedMinute)

                with(sharedPreferences.edit()) {
                    putInt("notification_hour", selectedHour)
                    putInt("notification_minute", selectedMinute)
                    apply()
                }
                scheduleDailyNotification(selectedHour, selectedMinute)
            }, hour, minute, true).show()
        } */

        binding.deleteAccountBtn.setOnClickListener {
            showDeleteAccountConfirmationDialog()
        }
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("About Me")
            .setMessage("I am Henry Miller, the creator of this app! Feel free to report any bugs or issues you encounter on my github .......")
            .setPositiveButton("Okay!") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun showDeleteAccountConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This cannot be undone.")
            .setPositiveButton("Yes") { dialog, _ ->
                deleteAccount()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteAccount() {
        val user = Firebase.auth.currentUser

        user?.let {

                    deleteAllUserPictures(user.uid) {
                        user.delete().addOnCompleteListener { deleteTask ->
                            if (deleteTask.isSuccessful) {
                                Toast.makeText(requireContext(), "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                                val intent = Intent(activity, LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            } else {
                                Toast.makeText(requireContext(), "Failed to delete account.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
        } ?: run {
            Toast.makeText(requireContext(), "No user is currently signed in.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteAllUserPictures(userId: String, onComplete: () -> Unit) {
        val userPhotosRef = db.collection("Users").document(userId).collection("Photos")

        userPhotosRef.get().addOnSuccessListener { querySnapshot ->
            val deleteTasks = mutableListOf<Task<Void>>()

            for (document in querySnapshot) {
                val pictureUrl = document.getString("date") ?: continue

                val storageRef = storage.reference.child("Users/$userId/DailyPictures/$pictureUrl.png")

                deleteTasks.add(storageRef.delete())
                deleteTasks.add(document.reference.delete())
            }

            Tasks.whenAll(deleteTasks).addOnCompleteListener {
                onComplete()
            }
        }.addOnFailureListener { e ->
            Log.e("SettingsFragment", "Error retrieving user pictures", e)
            Toast.makeText(requireContext(), "Failed to retrieve user pictures.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scheduleDailyNotification(hour: Int, minute: Int) {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1) // If the time has already passed for today, schedule for tomorrow
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}