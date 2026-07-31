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
import androidx.core.os.postDelayed
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Handler

@SuppressLint("MissingPermission")
class BleConnection(private val context: Context) {
    private val MESH_SERVICE_UUID = UUID.fromString("bf7673bc-b3d1-4093-8716-2b11dd89d237")
    private val MESH_CHARACTERISTIC_UUID = UUID.fromString("4dda5ff7-75f6-46ba-af3a-9807b52c1715")

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    private var gattServer: BluetoothGattServer? = null

    private val ownNeighbors = ConcurrentHashMap.newKeySet<BluetoothDevice>()
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

    fun startServerAdvertising(){
        if(bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

        gattServer = bluetoothManager.openGattServer(context, object : BluetoothGattServerCallback(){
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

                if(characteristic?.uuid == MESH_CHARACTERISTIC_UUID){
                    val receivedJsonPacket = String(value, Charsets.UTF_8)
                    Log.d("BLELayer", "Pacchetto ricevuto da ${device.address}")

                    router?.handlerDataReceived(receivedJsonPacket)

                    if(responseNeeded){
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                    }
                }
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

        advertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d("BLELayer", "Advertising MESH BLE avviato con successo")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("BLELayer", "Advertising MESH BLE fallito [$errorCode]")
            }
        })
    }

    fun stopAdvertising() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
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
        ownNeighbors.toList().forEach { neighbor ->
                @Suppress("DEPRECATION")
                neighbor.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.d(
                                "BLELayer",
                                "Connessione avvenuta con ${neighbor.address}, si richiede allargamento MTU..."
                            )
                            gatt?.requestMtu(512)
                        } else if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.e("BLELayer", "Errore di connessione con ${neighbor.address}: $status")
                            ownNeighbors.remove(neighbor)
                            gatt?.disconnect()
                            gatt?.close()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
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
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                        if(status == BluetoothGatt.GATT_SUCCESS){
                            val service = gatt?.getService(MESH_SERVICE_UUID)
                            val characteristic = service?.getCharacteristic(MESH_CHARACTERISTIC_UUID)
                            if(characteristic != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeCharacteristic(
                                        characteristic,
                                        payloadBytes,
                                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    characteristic.value = payloadBytes
                                    characteristic.writeType =
                                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                                    @Suppress("DEPRECATION")
                                    gatt.writeCharacteristic(characteristic)
                                }
                            }
                            else{
                                Log.d("BLELayer", "Caratteristica non trovata, chiusura connessione...")
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