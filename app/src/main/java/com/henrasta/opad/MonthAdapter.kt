package com.henrasta.opad

import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import org.threeten.bp.YearMonth


class MonthAdapter(startYearMonth: YearMonth, numberOfMonths: Int) : RecyclerView.Adapter<MonthAdapter.ViewHolder>() {

    private val monthsList = ArrayList<YearMonth>()

    init {
        for (i in 0 until numberOfMonths) {
            monthsList.add(startYearMonth.plusMonths(i.toLong()))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val monthView = MonthView(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return ViewHolder(monthView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val yearMonth = monthsList[position]
        holder.monthView.setYearMonth(yearMonth)
    }

    override fun getItemCount(): Int = monthsList.size

    class ViewHolder(val monthView: MonthView) : RecyclerView.ViewHolder(monthView)
}
