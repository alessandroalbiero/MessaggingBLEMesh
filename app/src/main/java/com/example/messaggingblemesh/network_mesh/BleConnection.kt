package com.example.messaggingblemesh.network_mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BleConnection(private val context: Context) {
    private val MESH_SERVICE_UUID = UUID.fromString("bf7673bc-b3d1-4093-8716-2b11dd89d237")
    private val MESH_CHARACTERISTIC_UUID = UUID.fromString("4dda5ff7-75f6-46ba-af3a-9807b52c1715")

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    private var gattServer: BluetoothGattServer? = null

    private val ownNeighbors = ConcurrentHashMap.newKeySet<BluetoothDevice>()
    private val esp32ForThesisAddress ="78:1C:3C:2D:30:4E"
    var router: MeshApplicationRouter? = null

    private val scanCallback= object : ScanCallback(){
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if(ownNeighbors.add(device)){
                Log.d("BLELayer", "Neigbhbor trovato e aggiunto in coda [${device.address}]")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLELayer", "Scan fallita [$errorCode]")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("BLELayer", "Advertising MESH BLE avviato con successo")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BLELayer", "Advertising MESH BLE fallito [$errorCode]")
        }
    }

    fun startServerAdvertising(){
        if(bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

        gattServer = bluetoothManager.openGattServer(context, object : BluetoothGattServerCallback(){
            private val preparedData = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                super.onCharacteristicWriteRequest(
                    device,
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value
                )

                if (characteristic?.uuid == MESH_CHARACTERISTIC_UUID) {

                    if(preparedWrite){
                        val concurrentData = preparedData[device.address]?: ByteArray(0)
                        preparedData[device.address] = concurrentData + value

                        if (responseNeeded){
                            try{gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value) }catch (e: Exception) {}
                        }
                    }
                    else {
                        val receivedJsonPacket = String(value, Charsets.UTF_8)
                        Log.d("BLELayer", "Pacchetto ricevuto da ${device.address}")

                        if (responseNeeded) {
                            try {
                                gattServer?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_SUCCESS,
                                    offset,
                                    value
                                )
                            } catch (e: SecurityException) {
                                Log.e(
                                    "BLELayer",
                                    "Impossibile inviare la risposta GATT: Permessi negati"
                                )
                            } catch (e: Exception) {
                                Log.e("BLELayer", "Errore hardware durante sendResponse", e)
                            }
                        }
                        router?.handlerDataReceived(receivedJsonPacket)
                    }
                }
            }

            override fun onExecuteWrite(
                device: BluetoothDevice,
                requestId: Int,
                execute: Boolean
            ) {
                super.onExecuteWrite(device, requestId, execute)

                if(execute){
                    val data = preparedData[device.address]
                    if(data != null){
                        val receivedJson = String(data, Charsets.UTF_8)
                        Log.d("BLELayer", "Pacchetto lungo senza canale allargato inviato e ricomposto")
                        router?.handlerDataReceived(receivedJson)
                    }
                }

                preparedData.remove(device.address)
                try{gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null) }catch (e: Exception) {}
            }
        })

        val bleService = BluetoothGattService(MESH_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val characteristic = BluetoothGattCharacteristic(
            MESH_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        bleService.addCharacteristic(characteristic)
        gattServer?.addService(bleService)

        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    fun stopAdvertising() {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        }catch (e: SecurityException){
            Log.e("BLELayer", "Permessi mancanti per fermare l'advertising")
        }
        gattServer?.close()
        gattServer = null
    }

    fun startScanningForNeighbors() {
        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        val scanFilters = listOf(
            android.bluetooth.le.ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
                .build()
        )
        val scanSettings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
    }

    fun stopScanningForNeighbors() {
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    fun broadcastToNeighbors(jpayload: String){
        val payloadBytes = jpayload.toByteArray(Charsets.UTF_8)

        val esp32Node = ownNeighbors.find { it.address.equals(esp32ForThesisAddress) }

        val targetNeighbors = if(esp32Node != null) {
            Log.d(
                "BLELayer",
                "TOPOLOGIA FORZATA: esp32 presenete nella rete, routin intermedio filtrato!"
            )
            listOf(esp32Node)
        }else{
            Log.d("BLELayer", "TOPOLOGIA STANDARD: esp32 lontano o spento, Broadcast standard verso tutti i vicini")
            ownNeighbors.toList()
        }
        targetNeighbors.toList().forEach { neighbor ->
                @Suppress("DEPRECATION")
                neighbor.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.e("BLELayer", "Errore di connessione con ${neighbor.address}: $status")
                            gatt?.close()
                        }
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.d(
                                "BLELayer",
                                "Connessione avvenuta con ${neighbor.address}, si richiede allargamento MTU..."
                            )
                            gatt?.requestMtu(512)
                        }else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Log.d("BLELayer", "Disconnessione da ${neighbor.address}")
                            gatt?.close()
                        }
                    }

                    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                        if(status == BluetoothGatt.GATT_SUCCESS){
                            Log.d("BLELayer", "MTU espanso, scoperta servizi...")
                            gatt?.discoverServices()
                        } else {
                            Log.e("BLELayer", "Fallimento cambio MTU per ${gatt?.device?.address}")
                            gatt?.disconnect()
                            gatt?.close()
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                        if(status == BluetoothGatt.GATT_SUCCESS){
                            val service = gatt?.getService(MESH_SERVICE_UUID)
                            val characteristic = service?.getCharacteristic(MESH_CHARACTERISTIC_UUID)

                            if(characteristic != null) {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val statusCode = gatt.writeCharacteristic(
                                            characteristic,
                                            payloadBytes,
                                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                                        )
                                        Log.d("BLELayer", "Tentativo di scrittura Android 13+. Status: $statusCode")
                                    } else {
                                        @Suppress("DEPRECATION")
                                        characteristic.value = payloadBytes
                                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                                        @Suppress("DEPRECATION")
                                        val success = gatt.writeCharacteristic(characteristic)
                                        Log.d("BLELayer", "Tentativo di scrittura Legacy. Esito: $success")
                                    }
                                } catch (e: SecurityException) {
                                    Log.e("BLELayer", "Permessi Bluetooth negati durante la scrittura!")
                                    gatt.disconnect()
                                }
                            } else {
                                Log.d("BLELayer", "Caratteristica non trovata sul vicino ${gatt?.device?.address}, chiusura connessione...")
                                gatt?.disconnect()
                            }
                        } else {
                            Log.e("BLELayer", "Scoperta servizi fallita per ${gatt?.device?.address}")
                            gatt?.disconnect()
                        }
                    }

                    override fun onCharacteristicWrite(
                        gatt: BluetoothGatt?,
                        characteristic: BluetoothGattCharacteristic?,
                        status: Int
                    ) {
                        handleWritingResult(gatt, status)
                    }

                    private fun handleWritingResult(gatt: BluetoothGatt?, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d("BLELayer", "Messaggio inviato con successo a ${gatt?.device?.address}")
                        } else {
                            Log.e("BLELayer", "Errore durante l'invio a ${gatt?.device?.address}: $status")
                        }
                        gatt?.disconnect()
                    }
                })
        }
    }
}