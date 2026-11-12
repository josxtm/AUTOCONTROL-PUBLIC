package com.josxtm.autocontrol.community.sniffer

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** Interface availability is not proof that any CT device is reachable. */
@Composable fun rememberLanStatus(): String {
    val context=LocalContext.current
    var status by remember { mutableStateOf("Red: comprobando") }
    LaunchedEffect(context) {
        val manager=context.getSystemService(ConnectivityManager::class.java)
        while(true) {
            val links=manager.allNetworks.mapNotNull(manager::getNetworkCapabilities)
            status=when {
                links.any { it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } -> "Ethernet disponible"
                links.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } -> "Wi-Fi disponible"
                else -> "Sin red local"
            }
            delay(2000)
        }
    }
    return status
}
