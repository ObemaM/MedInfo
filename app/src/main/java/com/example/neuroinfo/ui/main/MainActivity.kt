package com.example.neuroinfo.ui.main

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.neuroinfo.R
import com.example.neuroinfo.adapter.HospitalizationAdapter
import com.example.neuroinfo.data.FakeData
import com.example.neuroinfo.data.Hospitalization
import com.example.neuroinfo.ui.login.LoginActivity
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private var allHospitalizations = FakeData.hospitalizations.toMutableList()
    private lateinit var adapter: HospitalizationAdapter
    private lateinit var tabLayout: TabLayout
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sharedPreferences = getSharedPreferences("user_session", Context.MODE_PRIVATE)

        // Set up listeners for dialog results
        setupDialogListeners()

        // Initialize components
        setupTabLayout()
        setupRecyclerView()
        setupProfileMenu()
    }

    private fun setupDialogListeners() {
        supportFragmentManager.setFragmentResultListener(ConfirmArchiveDialogFragment.REQUEST_KEY, this) { _, bundle ->
            val confirmed = bundle.getBoolean(ConfirmArchiveDialogFragment.KEY_CONFIRMED)
            val itemId = bundle.getInt("ITEM_ID", -1)
            if (confirmed && itemId != -1) {
                archiveItem(itemId)
            }
        }

        supportFragmentManager.setFragmentResultListener(ConfirmLogoutDialogFragment.REQUEST_KEY, this) { _, bundle ->
            val confirmed = bundle.getBoolean(ConfirmLogoutDialogFragment.KEY_CONFIRMED_LOGOUT)
            if (confirmed) {
                logout()
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun setupRecyclerView() {
        adapter = HospitalizationAdapter(allHospitalizations.filter { it.status != "Завершено" }) { item ->
            val dialog = ConfirmArchiveDialogFragment().apply {
                arguments = Bundle().apply { putInt("ITEM_ID", item.id) }
            }
            dialog.show(supportFragmentManager, ConfirmArchiveDialogFragment.TAG)
        }

        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = this@MainActivity.adapter
        }
    }

    private fun archiveItem(itemId: Int) {
        val index = allHospitalizations.indexOfFirst { it.id == itemId }
        if (index != -1) {
            allHospitalizations[index] = allHospitalizations[index].copy(status = "Завершено")
            updateListBasedOnSelection()
        }
    }

    private fun setupTabLayout() {
        tabLayout = findViewById(R.id.tab_layout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { updateListBasedOnSelection(tab?.position) }
            override fun onTabUnselected(tab: TabLayout.Tab?) { }
            override fun onTabReselected(tab: TabLayout.Tab?) { }
        })
    }

    private fun setupProfileMenu() {
        val profileButton = findViewById<ImageButton>(R.id.profile_button)
        profileButton.setOnClickListener { anchorView ->
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val popupView = inflater.inflate(R.layout.popup_menu_custom, null)

            val popupWindow = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)

            popupView.findViewById<TextView>(R.id.popup_test).setOnClickListener {
                Log.d("MainActivity", "Тест нажат")
                popupWindow.dismiss()
            }

            popupView.findViewById<TextView>(R.id.userdata).setOnClickListener {
                val login = sharedPreferences.getString("user_login", "N/A") ?: "N/A"
                val dialog = UserDataDialogFragment.newInstance(login)
                dialog.show(supportFragmentManager, UserDataDialogFragment.TAG)
                popupWindow.dismiss()
            }

            popupView.findViewById<TextView>(R.id.popup_logout).setOnClickListener {
                val dialog = ConfirmLogoutDialogFragment()
                dialog.show(supportFragmentManager, ConfirmLogoutDialogFragment.TAG)
                popupWindow.dismiss()
            }

            val versionText = popupView.findViewById<TextView>(R.id.popup_version)
            try {
                val pInfo = packageManager.getPackageInfo(packageName, 0)
                versionText.text = "Версия ${pInfo.versionName}"
            } catch (e: PackageManager.NameNotFoundException) {
                versionText.text = "Версия N/A"
                e.printStackTrace()
            }

            popupWindow.showAsDropDown(anchorView)
        }
    }

    private fun logout() {
        with(sharedPreferences.edit()) {
            putBoolean("isLoggedIn", false)
            remove("user_login")
            apply()
        }

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun updateListBasedOnSelection(selectedTabPosition: Int? = null) {
        val currentPosition = selectedTabPosition ?: tabLayout.selectedTabPosition
        if (currentPosition == 0) {
            adapter.updateList(allHospitalizations.filter { it.status != "Завершено" })
        } else {
            adapter.updateList(allHospitalizations.filter { it.status == "Завершено" })
        }
    }
}
