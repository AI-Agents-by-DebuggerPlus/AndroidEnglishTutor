package com.englishtutor.ui.screens.splash

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.bluetooth.BluetoothPermissionHelper

@Composable
fun SplashScreen(
    onNavigate: (String) -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var permissionsReady by remember { mutableStateOf(false) }
    val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshBluetooth(context)
        permissionsReady = true
    }

    LaunchedEffect(Unit) {
        if (BluetoothPermissionHelper.needsConnectPermission() &&
            !BluetoothPermissionHelper.hasConnectPermission(context)
        ) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            viewModel.refreshBluetooth(context)
            permissionsReady = true
        }
    }

    LaunchedEffect(permissionsReady) {
        viewModel.beginStartup(permissionsReady)
    }

    LaunchedEffect(startDestination) {
        val destination = startDestination ?: return@LaunchedEffect
        onNavigate(destination)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
