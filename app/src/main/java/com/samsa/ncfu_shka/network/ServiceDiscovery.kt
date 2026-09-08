package com.samsa.ncfu_shka.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class ServiceDiscovery(private val context: Context) {
    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_agario._tcp."
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null
    private var registeredServiceName: String? = null

    companion object {
        private const val TAG = "ServiceDiscovery"
    }

    fun registerService(port: Int, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "AgarIo_${System.currentTimeMillis() % 10000}"
            serviceType = SERVICE_TYPE
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                registeredServiceName = serviceInfo.serviceName
                Log.d(TAG, "✅ Сервер зарегистрирован: ${serviceInfo.serviceName}")
                onSuccess(serviceInfo.serviceName)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "❌ Ошибка регистрации: $errorCode")
                onError("Ошибка регистрации: $errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "✅ Сервер отрегистрирован")
                registeredServiceName = null
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "❌ Ошибка отрегистрации: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка регистрации: ${e.message}")
            onError("Ошибка: ${e.message}")
        }
    }

    fun unregisterService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
                Log.d(TAG, "✅ NSD-сервис удалён")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка удаления сервиса: ${e.message}")
            }
        }
        registeredServiceName = null
        registrationListener = null
    }

    fun discoverServices(onFound: (String, String, Int) -> Unit, onError: (String) -> Unit) {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "🔍 Поиск начат: $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Поиск остановлен")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "✅ Найден сервер: ${serviceInfo.serviceName}")
                resolveService(serviceInfo, onFound, onError)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "❌ Сервер потерян: ${serviceInfo.serviceName}")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Ошибка начала поиска: $errorCode")
                onError("Ошибка поиска: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Ошибка остановки поиска: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка поиска: ${e.message}")
            onError("Ошибка: ${e.message}")
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo, onFound: (String, String, Int) -> Unit, onError: (String) -> Unit) {
        resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Ошибка разрешения: $errorCode")
                onError("Ошибка разрешения: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val ip = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                val name = serviceInfo.serviceName
                Log.d(TAG, "✅ Разрешён: $name -> $ip:$port")
                onFound(name, ip, port)
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка разрешения: ${e.message}")
            onError("Ошибка: ${e.message}")
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {}
        }
    }

    fun cleanup() {
        stopDiscovery()
        unregisterService()
        discoveryListener = null
        resolveListener = null
    }
}