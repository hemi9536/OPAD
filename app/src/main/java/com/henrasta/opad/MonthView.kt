package com.henrasta.opad

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth
import org.threeten.bp.format.DateTimeFormatter

class MonthView(context: Context) : LinearLayout(context), View.OnClickListener {

    private var monthYearText: TextView? = null
    private var selectedDate: LocalDate? = null
    private lateinit var calendarGrid: GridLayout
    private val firestore = FirebaseFirestore.getInstance()
    private val user = Firebase.auth.currentUser
    private val documentRef = firestore.collection("Users").document(user?.uid ?: "").collection("Photos")

    private var yearMonth: YearMonth = YearMonth.now()

    init {
        init(context)
    }

    private fun init(context: Context?) {
        selectedDate = LocalDate.now()

        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        orientation = VERTICAL
        setBackgroundColor(0xFFD3D3D3.toInt())

        monthYearText = TextView(context).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 32) // Adjust padding to move title up
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }
        addView(monthYearText)

        // Add GridLayout for the calendar
        calendarGrid = GridLayout(context).apply {
            columnCount = 7
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(calendarGrid)

        setMonthView()
    }

    fun setYearMonth(yearMonth: YearMonth) {
        this.yearMonth = yearMonth
        this.selectedDate = yearMonth.atDay(1)
        updateMonthTitle()
        setMonthView()
    }

    private fun updateMonthTitle() {
        monthYearText?.text = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    }

    private fun setMonthView() {
        calendarGrid.removeAllViews()

        // Add day labels
        val dayLabels = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        for (label in dayLabels) {
            val dayLabel = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            }
            calendarGrid.addView(dayLabel)
        }

        val daysInMonthArray = daysInMonthArray(selectedDate)

        val rowCount = if (daysInMonthArray.size > 35) 7 else 6
        calendarGrid.rowCount = rowCount

        val screenWidth = resources.displayMetrics.widthPixels
        val cellSize = screenWidth / 7

        // Add cells to the GridLayout
        for (row in 1 until rowCount) {
            for (col in 0 until 7) {
                val dayIndex = (row - 1) * 7 + col
                if (dayIndex >= daysInMonthArray.size) {
                    continue
                }

                val lp = GridLayout.LayoutParams(
                    GridLayout.spec(row, GridLayout.CENTER),
                    GridLayout.spec(col, GridLayout.CENTER)
                ).apply {
                    width = cellSize
                    height = cellSize // Ensures cells are square
                }

                val dayOfMonth = daysInMonthArray[dayIndex].toIntOrNull()

                val cellLayout = FrameLayout(context).apply {
                    layoutParams = lp
                    setOnClickListener {
                        if (dayOfMonth != null) {
                            val clickedDate = LocalDate.of(yearMonth.year, yearMonth.month, dayOfMonth)
                            handleClickOnDate(clickedDate)
                        }
                    }
                }

                val cellText = TextView(context).apply {
                    text = daysInMonthArray[dayIndex]
                    gravity = Gravity.CENTER
                    setTextColor(Color.BLACK)
                    if (dayOfMonth != null) {
                        // Apply background only for cells with dates and when showDates is true
                        background = context.getDrawable(R.drawable.circle)
                    } else {
                        background = null
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                    setPadding(8, 4, 8, 4)
                }

                val cellImage = ImageView(context).apply {
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                if (dayOfMonth != null) {
                    val localDate = LocalDate.of(yearMonth.year, yearMonth.month, dayOfMonth)
                    fetchAndDisplayImage(localDate, cellImage)
                }

                cellLayout.addView(cellImage)
                cellLayout.addView(cellText)
                calendarGrid.addView(cellLayout)
            }
        }
    }

    private fun fetchAndDisplayImage(date: LocalDate, imageView: ImageView) {
        if (date.isBefore(LocalDate.now()) || date.isEqual(LocalDate.now())) {
            val formattedDate = String.format("%02d.%02d.%02d", date.monthValue, date.dayOfMonth, date.year % 100)

            documentRef.document(formattedDate).get().addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val imageUrl = document.getString("url")

                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(context)
                            .load(imageUrl)
                            .placeholder(android.R.drawable.ic_menu_report_image) // placeholder
                            .into(imageView)
                    }
                } else {
                    imageView.setImageResource(
                        if (date.isEqual(LocalDate.now())) {
                            android.R.drawable.ic_menu_camera
                        } else {
                            android.R.drawable.ic_delete
                        }
                    )
                }
            }.addOnFailureListener { exception ->
                Log.w("LoadImage", "Error getting document", exception)
                imageView.setImageResource(android.R.drawable.ic_delete)
            }
        } else {
            imageView.setImageResource(android.R.color.transparent)
        }
    }

    private fun daysInMonthArray(date: LocalDate?): Array<String> {
        val yearMonth = YearMonth.from(date)
        val daysInMonthArray = Array(42) { "" }

        val firstOfMonth = yearMonth.atDay(1)
        val dayOfWeek = firstOfMonth.dayOfWeek.value % 7 // Adjust to have Sunday as 0

        for (i in 1..yearMonth.lengthOfMonth()) {
            daysInMonthArray[i + dayOfWeek - 1] = i.toString()
        }

        return daysInMonthArray
    }

    override fun onClick(view: View?) {
        val cellText = view as TextView
        val dayText = cellText.text.toString()
        if (dayText.isNotEmpty()) {
            val message = "Selected Date $dayText ${yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}"
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleClickOnDate(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) {
            return
        }

        // Open the dialog/activity for picture selection
        val fragmentManager = (context as AppCompatActivity).supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()

        // Pass the selected date to the fragment
        val selectedDate = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val fragment = PictureSelectionFragment.newInstance(selectedDate)

        fragmentTransaction.replace(R.id.fragment_container, fragment)
        fragmentTransaction.addToBackStack("Picture Selection")
        fragmentTransaction.commit()
    }
}