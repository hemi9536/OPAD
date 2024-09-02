package com.henrasta.opad

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.henrasta.opad.databinding.ActivityMainBinding
import com.henrasta.opad.ui.calendar.CalendarFragment
import com.henrasta.opad.ui.home.HomeFragment
import com.henrasta.opad.ui.settings.SettingsFragment
import com.jakewharton.threetenabp.AndroidThreeTen


class MainActivity : AppCompatActivity(), HomeFragment.OnImageUploadListener {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment = HomeFragment()
    private val calendarFragment = CalendarFragment()
    private val settingsFragment = SettingsFragment()
    private lateinit var activeFragment: Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        activeFragment = homeFragment
        AndroidThreeTen.init(this)

        // Initial fragment setup
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragment_container, homeFragment, "homeFragment")
            addToBackStack("Home Fragment")
            add(R.id.fragment_container, calendarFragment, "calendarFragment").hide(calendarFragment)
            addToBackStack("Calendar Fragment")
            add(R.id.fragment_container, settingsFragment, "settingsFragment").hide(settingsFragment)
            addToBackStack("Settings Fragment")
        }.commit()

        val navView: BottomNavigationView = binding.bottomNavigationView
        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    supportFragmentManager.beginTransaction().hide(activeFragment).show(homeFragment).commit()
                    activeFragment = homeFragment
                    true
                }
                R.id.navigation_calendar -> {
                    supportFragmentManager.beginTransaction().hide(activeFragment).show(calendarFragment).commit()
                    activeFragment = calendarFragment
                    true
                }
                R.id.navigation_settings -> {
                    supportFragmentManager.beginTransaction().hide(activeFragment).show(settingsFragment).commit()
                    activeFragment = settingsFragment
                    true
                }
                else -> false
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "your_channel_id"
            val channelName = "Daily Notification Channel"
            val channelDescription = "Channel for daily notifications"

            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = channelDescription

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onImageUploaded() {
        val fragmentManager = supportFragmentManager
        val existingFragment = fragmentManager.findFragmentByTag("calendarFragment")
        fragmentManager.beginTransaction().detach(existingFragment!!).commit()
        fragmentManager.beginTransaction().attach(existingFragment).commit()
    }

}