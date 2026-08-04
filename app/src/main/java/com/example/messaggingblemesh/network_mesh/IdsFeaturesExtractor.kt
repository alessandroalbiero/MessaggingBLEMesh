package com.example.messaggingblemesh.network_mesh

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.example.messaggingblemesh.data.local.entities.Packet
import com.google.android.datatransport.runtime.EncodedPayload
import java.io.File
import java.io.FileWriter
import kotlin.math.log2
import kotlin.math.pow

object IdsFeaturesExtractor{
    private const val WINDOW_SIZE = 20
    private const val BIN_SIZE = 50L
    private val windowMap = mutableMapOf<String, MutableList<Packet>>()
    private val globalWindow = mutableListOf<Packet>()
    private val dataset = mutableListOf<String>()

    init {
        dataset.add("payload_entropy,piat_entropy,payload_size_variance,app_ttl,global_piat_entropy,label")
    }

    fun getFeaturesFromPacket(packet: Packet): FloatArray? {
        val window = windowMap.getOrPut(packet.sourceId) { mutableListOf() }
        window.add(packet)
        globalWindow.add(packet)

        if(window.size > WINDOW_SIZE) {
            window.removeAt(0)
        }
        if(globalWindow.size > WINDOW_SIZE) {
            globalWindow.removeAt(0)
        }
        if (globalWindow.size == WINDOW_SIZE){
            val piats = mutableListOf<Long>()
            for(i in 1 until window.size){
                piats.add(window[i].timestamp - window[i - 1].timestamp)
            }
            val payloadBytes = packet.encryptedPayload.toByteArray(Charsets.UTF_8)
            val payloadEntropy = calculateShannonEntropy(payloadBytes)
            val piatEntropy = calculatePiatEntropy(piats)
            val sizeVariance = calculateSizeVariance(window.map{it.payloadSize.toDouble()})
            val appTtl = packet.ttl.toFloat()

            val globalPiats = mutableListOf<Long>()
            for(i in 1 until globalWindow.size){
                globalPiats.add(globalWindow[i].timestamp - globalWindow[i - 1].timestamp)
            }
            val globalPiatEntropy = calculatePiatEntropy(globalPiats)

            val csvRow = "$payloadEntropy,$piatEntropy,$sizeVariance,$appTtl,$globalPiatEntropy,${packet.label}"
            dataset.add(csvRow)
            return floatArrayOf(payloadEntropy.toFloat(), piatEntropy.toFloat(), sizeVariance.toFloat(), globalPiatEntropy.toFloat(), appTtl)
        }
        return null
    }

    private fun calculateShannonEntropy(bytes: ByteArray): Double {
        if (bytes.isEmpty()) return 0.0
        val frequencies = IntArray(256)
        for(b in bytes) {
            frequencies[b.toInt() and 0xFF]++
        }
        var entropy = 0.0
        val length = bytes.size.toDouble()
        for (count in frequencies) {
            if (count > 0) {
                val p = count / length
                entropy -= p * log2(p)
            }
        }
        return entropy
    }

    private fun calculatePiatEntropy(piats: List<Long>): Double {
        if (piats.size < 2) return 0.0
        val binnedPiats = piats.map {it / BIN_SIZE}
        val counts = binnedPiats.groupingBy { it }.eachCount()
        var entropy = 0.0
        val length = binnedPiats.size.toDouble()
        for(count in counts.values) {
            val p = count / length
            entropy -= p*log2(p)
        }
        return entropy
    }

    private fun calculateSizeVariance(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return values.map{(it - mean).pow(2)}.average()
    }

    fun exportCsv(context: Context) {
        try{
            val file = File(context.getExternalFilesDir(null), "dataset_ble_mesh_real.csv")
            FileWriter(file).use { writer ->
                dataset.forEach { writer.appendLine(it) }
            }
            Log.d("IDS", "CSV exported succesfully in:${file.absolutePath}")
        }catch (e: Exception){
            Log.e("IDS", "Error on exporting", e)
        }
    }

}