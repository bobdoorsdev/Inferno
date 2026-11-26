package to.eyed.inferno.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide state for a validated Wi-Fi connection. INTERNET alone is not enough: VALIDATED means
 * Android has confirmed that the active Wi-Fi network can reach the public internet.
 */
class NetworkStatus(context: Context) {
    private val manager = requireNotNull(context.applicationContext.getSystemService(ConnectivityManager::class.java))
    private val _wifiConnected = MutableStateFlow(readValidatedWifi())
    val wifiConnected = _wifiConnected.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
    }

    init {
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        refresh()
    }

    private fun refresh() {
        _wifiConnected.value = readValidatedWifi()
    }

    private fun readValidatedWifi(): Boolean {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
