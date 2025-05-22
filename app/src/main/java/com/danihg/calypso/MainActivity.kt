package com.danihg.calypso

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // NOW we switch off the splash theme into our real app theme:
        setTheme(R.style.Theme_Calypso)
        setContentView(R.layout.activity_main)
    }
}