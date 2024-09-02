package com.henrasta.opad.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.henrasta.opad.MonthAdapter
import com.henrasta.opad.R
import com.henrasta.opad.databinding.FragmentCalendarBinding
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.YearMonth
import org.threeten.bp.ZoneId
import org.threeten.bp.temporal.ChronoUnit

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private lateinit var user: FirebaseUser
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        val view = binding.root

        user = Firebase.auth.currentUser!!
        db = Firebase.firestore
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(context)



        // Fetch user creation date from Firebase Authentication
        val userCreationDate = user.metadata?.creationTimestamp
            // Convert timestamp to LocalDateTime
        val userCreationLocalDateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(userCreationDate!!),
            ZoneId.systemDefault()
        )
        val userCreationYearMonth = YearMonth.from(userCreationLocalDateTime)

        // Calculate the number of months since user creation up to the current month
        val currentYearMonth = YearMonth.now()
        val monthsSinceCreation =
            ChronoUnit.MONTHS.between(userCreationYearMonth, currentYearMonth).toInt() + 1

        recyclerView.adapter = MonthAdapter(userCreationYearMonth, monthsSinceCreation)

        val currentMonthPosition = monthsSinceCreation - 1 // Adjusting for zero-indexed position
        recyclerView.scrollToPosition(currentMonthPosition)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}