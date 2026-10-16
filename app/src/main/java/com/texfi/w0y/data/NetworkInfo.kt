package com.texfi.w0y.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Снимок состояния сети для диагностики: никаких адресов и имён сетей. */
data class NetworkSnapshot(
    val online: Boolean,
    val transport: String,
    val vpn: Boolean,
    val privateDns: String,
    val dataSaver: String,
    val powerSave: Boolean,
)

@Singleton
class NetworkInfo @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun isOnline(): Boolean = snapshot().online

    fun snapshot(): NetworkSnapshot =
        runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val network = manager?.activeNetwork
            val caps = network?.let { manager.getNetworkCapabilities(it) }
            val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val transport =
                when {
                    caps == null -> "none"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn-only"
                    else -> "other"
                }
            val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            val privateDns =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && network != null) {
                    val link = manager.getLinkProperties(network)
                    when {
                        link == null -> "unknown"
                        link.isPrivateDnsActive -> "on"
                        else -> "off"
                    }
                } else {
                    "n/a"
                }
            val saver =
                when (manager?.restrictBackgroundStatus) {
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "on"
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "on (app allowed)"
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "off"
                    else -> "unknown"
                }
            val power = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
            NetworkSnapshot(online, transport, vpn, privateDns, saver, power)
        }.getOrDefault(NetworkSnapshot(true, "unknown", false, "unknown", "unknown", false))
}
