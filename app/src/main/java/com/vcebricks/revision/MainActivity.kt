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
import androidx.lifecycle.ViewModelProvider
import com.vcebricks.revision.ui.MainViewModel
import com.vcebricks.revision.ui.RevisionApp
import com.vcebricks.revision.widgets.RevisionWidgetUpdater

class MainActivity : ComponentActivity() {
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RevisionApplication).container
        mainViewModel = ViewModelProvider(
            this,
            MainViewModel.Factory(
                container.repository,
                container.settingsStore,
                container.reminderScheduler,
                refreshWidgets = { RevisionWidgetUpdater.updateAll(applicationContext) },
            ),
        )[MainViewModel::class.java]

        setContent {
            NotificationPermissionHost(mainViewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::mainViewModel.isInitialized) mainViewModel.refreshToday()
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
