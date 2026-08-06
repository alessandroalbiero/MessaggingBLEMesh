package com.example.messaggingblemesh.network_mesh

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.messaggingblemesh.data.local.MeshDatabase
import com.example.messaggingblemesh.security.CryptoHelper
import java.util.UUID
import androidx.core.content.edit
import android.content.pm.ServiceInfo
import android.util.Log

class BleMeshService: Service(){
    private val channelId = "mesh_ble_network"

    private lateinit var router : MeshApplicationRouter
    private lateinit var bleConnection: BleConnection
    private lateinit var meshDatabase: MeshDatabase
    private var nodeId =""
    private var isServiceRunning = false

    private val bluethootStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if(intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED){
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when(state){
                    BluetoothAdapter.STATE_ON -> {
                        Log.d("BLEMeshService", "Bluetooth riattivato dal sistema. Riavvio MESH...")
                        bleConnection.startServerAdvertising()
                        bleConnection.startScanningForNeighbors()
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        Log.d("BLEMeshService", "Bluetooth spento dal sistema. Sospensione MESH...")
                        bleConnection.stopAdvertising()
                        bleConnection.stopScanningForNeighbors()
                    }
                }
            }
        }
    }

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
        registerReceiver(bluethootStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BLE Mesh Attivo")
            .setContentText("Dispositivo in ascolto")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, notification)
        }

        if(!isServiceRunning){
            bleConnection.startServerAdvertising()
            bleConnection.startScanningForNeighbors()
            isServiceRunning = true
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bleConnection.stopScanningForNeighbors()
        bleConnection.stopAdvertising()
        unregisterReceiver(bluethootStateReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    fun getNodeId(): String{
        val sharedPref = getSharedPreferences("MeshPrefs", MODE_PRIVATE)
        var nodeId = sharedPref.getString("user_id", null)

        if(nodeId == null){
            nodeId = UUID.randomUUID().toString()
            sharedPref.edit { putString("user_id", nodeId) }
        }

        return nodeId
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        var routerInstance: MeshApplicationRouter? = null
    }
}

