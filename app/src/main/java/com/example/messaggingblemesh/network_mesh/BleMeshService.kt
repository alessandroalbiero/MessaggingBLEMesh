package com.example.messaggingblemesh.network_mesh

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.messaggingblemesh.data.local.MeshDatabase
import com.example.messaggingblemesh.security.CryptoHelper
import java.util.UUID
import androidx.core.content.edit

class BleMeshService: Service(){
    private val channelId = "mesh_ble_network"

    private lateinit var router : MeshApplicationRouter
    private lateinit var bleConnection: BleConnection
    private lateinit var meshDatabase: MeshDatabase
    private var nodeId =""

    override fun onCreate(){
        super.onCreate()
        val cryptoHelper = CryptoHelper
        val channel = NotificationChannel(
            channelId,
            "BLE Mesh Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        bleConnection = BleConnection(applicationContext)
        nodeId = getNodeId()
        meshDatabase = MeshDatabase.getDatabase(applicationContext)
        router = MeshApplicationRouter(nodeId, bleConnection, meshDatabase, cryptoHelper)
        bleConnection.router = router
        routerInstance = router
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BLE Mesh Attivo")
            .setContentText("Dispositivo in ascolto")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        bleConnection.startServerAdvertising()
        bleConnection.startScanningForNeighbors()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bleConnection.stopScanningForNeighbors()
        bleConnection.stopAdvertising()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    fun getNodeId(): String{
        val sharedPref = getSharedPreferences("MeshPreferences", MODE_PRIVATE)
        var nodeId = sharedPref.getString("DeviceNodeId", null)

        if(nodeId == null){
            nodeId = UUID.randomUUID().toString()
            sharedPref.edit { putString("DeviceNodeId", nodeId) }
        }

        return nodeId
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        var routerInstance: MeshApplicationRouter? = null
    }
}

