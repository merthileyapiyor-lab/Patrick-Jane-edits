package com.overdrive.quest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.overdrive.quest.core.AppGraph
import com.overdrive.quest.ui.MainViewModel
import com.overdrive.quest.ui.OverdriveRoot

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OverdriveRoot(vm) }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        AppGraph.metrics.acquire(UI_TAG)
        AppGraph.shell.refresh()
    }

    override fun onStop() {
        AppGraph.metrics.release(UI_TAG)
        super.onStop()
    }

    private companion object {
        const val UI_TAG = "ui"
    }
}
