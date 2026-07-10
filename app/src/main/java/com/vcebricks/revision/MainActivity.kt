package com.vcebricks.revision

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vcebricks.revision.ui.MainViewModel
import com.vcebricks.revision.ui.RevisionApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RevisionApplication).container
        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(
                    container.repository,
                    container.settingsStore,
                    container.reminderScheduler,
                ),
            )
            NotificationPermissionHost(viewModel)
        }
    }
}

@Composable
private fun NotificationPermissionHost(viewModel: MainViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    RevisionApp(
        viewModel = viewModel,
        requestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
    )
}
