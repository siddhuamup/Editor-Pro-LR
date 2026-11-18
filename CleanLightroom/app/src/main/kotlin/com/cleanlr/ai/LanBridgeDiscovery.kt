package com.cleanlr.ai

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mobile-to-PC LAN Wi-Fi Bridge (mDNS / NSD Auto-Discovery).
 *
 * Automatically discovers a local CleanLR Desktop instance running FLUX.2 Klein 4B (GGUF)
 * or FLUX.1 Fill [dev] via `stable-diffusion.cpp` / FastAPI on the same Wi-Fi LAN
 * so mobile can offload ultra-quality Expand & Generative Fill jobs with 0 API cost!
 */
data class DiscoveredLanServer(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val modelName: String = "FLUX.2 Klein 4B (GGUF)"
) {
    val endpointUrl: String get() = "http://$hostAddress:$port/v1/flux-fill"
}

class LanBridgeDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "LanBridgeDiscovery"
        const val SERVICE_TYPE = "_cleanlr-ai._tcp."
    }

    private val nsdManager: NsdManager? = runCatching {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }.getOrNull()

    private val _discoveredServers = MutableStateFlow<List<DiscoveredLanServer>>(emptyList())
    val discoveredServers: StateFlow<List<DiscoveredLanServer>> = _discoveredServers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        val mgr = nsdManager ?: return
        if (_isScanning.value) return

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                _isScanning.value = true
                Log.d(TAG, "mDNS LAN Bridge discovery started for $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("cleanlr-ai")) {
                    @Suppress("DEPRECATION")
                    mgr.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.w(TAG, "Resolve failed ($errorCode) for ${serviceInfo.serviceName}")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = serviceInfo.host?.hostAddress ?: return
                            val entry = DiscoveredLanServer(
                                serviceName = serviceInfo.serviceName,
                                hostAddress = host,
                                port = serviceInfo.port
                            )
                            val current = _discoveredServers.value.filterNot { it.endpointUrl == entry.endpointUrl }
                            _discoveredServers.value = current + entry
                            Log.i(TAG, "Discovered CleanLR Desktop LAN Bridge: ${entry.endpointUrl}")
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                _discoveredServers.value = _discoveredServers.value.filterNot {
                    it.serviceName == service.serviceName
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isScanning.value = false
                Log.w(TAG, "Start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isScanning.value = false
            }
        }

        discoveryListener = listener
        runCatching {
            mgr.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            _isScanning.value = false
            Log.w(TAG, "NSD discovery unavailable: ${it.message}")
        }
    }

    fun stopDiscovery() {
        val mgr = nsdManager ?: return
        val listener = discoveryListener ?: return
        runCatching { mgr.stopServiceDiscovery(listener) }
        discoveryListener = null
        _isScanning.value = false
    }
}
