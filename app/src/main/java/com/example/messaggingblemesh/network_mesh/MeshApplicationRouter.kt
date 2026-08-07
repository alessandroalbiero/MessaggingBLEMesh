package com.example.messaggingblemesh.network_mesh

import android.content.Context
import android.util.Log
import com.example.messaggingblemesh.data.local.MeshDatabase
import com.example.messaggingblemesh.data.local.entities.Message
import com.example.messaggingblemesh.data.local.entities.Packet
import com.example.messaggingblemesh.security.CryptoHelper
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class MeshApplicationRouter(
    private val nodeId: String,
    private val bleConnection: BleConnection,
    private val database: MeshDatabase,
    private val cryptoHelper: CryptoHelper
){
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    var onMessageReceived: ((Message) -> Unit)? = null

    fun handlerDataReceived(jdata: String, senderMac: String?= null){
        val meshPacket = try {
            gson.fromJson(jdata, Packet::class.java)
        } catch (e: Exception) {
            Log.e("MeshRouter", "Ricevuti dati corrotti: $jdata")
            return
        }

        scope.launch {
            if(database.packetDao().packetExists(meshPacket.packetId)) {
                Log.d("MeshRouter", "Pacchetto ${meshPacket.packetId} già ricevuto in precedenza. DROP PACKET.")
                return@launch
            }

            database.packetDao().insertPacketLog(meshPacket)
            val featuresForAi = IdsFeaturesExtractor.getFeaturesFromPacket(meshPacket)

            if (meshPacket.destId == nodeId) {
                Log.d("MeshRouter", "Pacchetto arrivato al destinatario!")
                val sender = database.contactDao().getUserById(meshPacket.sourceId)

                if (sender != null) {
                    try {
                        val decryptedText = cryptoHelper.decrypt(
                            encryptedTextBase64 = meshPacket.encryptedPayload,
                            nonceBase64 = meshPacket.nonce,
                            sharedSessionKey = sender.sharedSecretSessionKeyBase64
                        )

                        val message = Message(
                            content = decryptedText,
                            messageId = meshPacket.packetId,
                            senderId = meshPacket.sourceId,
                            destinationId = meshPacket.destId,
                            timestamp = meshPacket.timestamp
                        )



                        database.messageDao().insertMessage(message)
                        onMessageReceived?.invoke(message)
                        Log.d("MeshRouter", "Messaggio ricevuto e salvato: ${message.messageId}")
                    } catch (e: Exception) {
                        Log.e("MeshRouter", "Errore durante la decrittazione o il parsing: ${e.message}")
                    }
                }
                return@launch
            }

            if(meshPacket.ttl > 0){
                val forwardPacket = meshPacket.copy(ttl = meshPacket.ttl - 1)
                bleConnection.broadcastToNeighbors(gson.toJson(forwardPacket), senderMac)
            }
        }
    }

    fun sendNewMessage(destId: String, plainContent: String, isFloodingAttack: Boolean = false) {
        scope.launch {
            val destUser = database.contactDao().getUserById(destId) ?: return@launch

            val(cipherText, nonce) = cryptoHelper.encrypt(plainContent, destUser.sharedSecretSessionKeyBase64)

            val packet = Packet(
                packetId = UUID.randomUUID().toString(),
                sourceId = nodeId,
                destId = destId,
                encryptedPayload = cipherText,
                nonce = nonce,
                payloadSize = cipherText.length,
                ttl = if(isFloodingAttack) 255 else 7,
                timestamp = System.currentTimeMillis(),
                label = if(isFloodingAttack) 1 else 0,
                droppedByIds = false
            )

            database.packetDao().insertPacketLog(packet)
            bleConnection.broadcastToNeighbors(gson.toJson(packet), null)
        }
    }

}