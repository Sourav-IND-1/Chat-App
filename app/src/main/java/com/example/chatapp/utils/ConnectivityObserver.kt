package com.example.chatapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkStatus { Available, Unavailable, Losing }

/**
 * Flow-based network connectivity observer.
 * Emits [NetworkStatus] whenever connectivity changes.
 */
object ConnectivityObserver {

    fun observe(context: Context): Flow<NetworkStatus> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(NetworkStatus.Available)
            }
            override fun onLosing(network: Network, maxMsToLive: Int) {
                trySend(NetworkStatus.Losing)
            }
            override fun onLost(network: Network) {
                trySend(NetworkStatus.Unavailable)
            }
            override fun onUnavailable() {
                trySend(NetworkStatus.Unavailable)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, callback)

        // Emit initial state
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val initialStatus = if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
            NetworkStatus.Available else NetworkStatus.Unavailable
        trySend(initialStatus)

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** One-shot check — use for quick guards before network calls. */
    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
