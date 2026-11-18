package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.model.NetworkTransport
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _transport = MutableStateFlow(readCurrentTransport())
    val transport: StateFlow<NetworkTransport> = _transport.asStateFlow()

    private val _networkChangedEvents = MutableSharedFlow<NetworkTransport>(extraBufferCapacity = 8)
    val networkChangedEvents: SharedFlow<NetworkTransport> = _networkChangedEvents.asSharedFlow()

    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateTransport()
        }

        override fun onLost(network: Network) {
            updateTransport()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateTransport()
        }
    }

    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            connectivityManager.registerNetworkCallback(request, callback)
            registered = true
        }
        updateTransport()
    }

    fun stop() {
        if (!registered) return
        runCatching {
            connectivityManager.unregisterNetworkCallback(callback)
        }
        registered = false
    }

    fun isOnline(): Boolean = _transport.value != NetworkTransport.NONE

    private fun updateTransport() {
        val next = readCurrentTransport()
        if (next != _transport.value) {
            _transport.value = next
            _networkChangedEvents.tryEmit(next)
        }
    }

    private fun readCurrentTransport(): NetworkTransport {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkTransport.NONE
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkTransport.NONE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkTransport.NONE
        }
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            else -> NetworkTransport.OTHER
        }
    }
}
