package com.example.messaggingblemesh.ids

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.messagingblemesh.R
import java.nio.FloatBuffer

class DualModelIDSInference(context: Context) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessionNormal: OrtSession
    private val sessionAttack: OrtSession

    init {
        val modelBytesNormal = context.resources.openRawResource(R.raw.isolation_forest_model_normal).readBytes()
        val modelBytesAttack = context.resources.openRawResource(R.raw.isolation_forest_model_attack).readBytes()
        sessionNormal = env.createSession(modelBytesNormal)
        sessionAttack = env.createSession(modelBytesAttack)
    }

    fun analyzePacket(inputData: FloatArray): Int {
        val shape = longArrayOf(1, inputData.size.toLong())
        val floatBuffer = FloatBuffer.wrap(inputData)

        val tensorNormal = OnnxTensor.createTensor(env, floatBuffer, shape)
        val inputNameNormal = sessionNormal.inputNames.iterator().next()
        val resNormal = sessionNormal.run(mapOf(inputNameNormal to tensorNormal))

        val predNormal = (resNormal[0].value as LongArray)[0]
        val isAttackForNormal = if (predNormal == -1L) 1 else 0

        tensorNormal.close()
        resNormal.close()

        floatBuffer.rewind()

        val tensorAttack = OnnxTensor.createTensor(env, floatBuffer, shape)
        val inputNameAttack = sessionAttack.inputNames.iterator().next()
        val resAttack = sessionAttack.run(mapOf(inputNameAttack to tensorAttack))

        val predAttack = (resAttack[0].value as LongArray)[0]
        val isAttackForAttack = if (predAttack == 1L) 1 else 0

        tensorAttack.close()
        resAttack.close()

        return if(isAttackForNormal == 0 && isAttackForAttack == 0){
            0
        } else if (isAttackForNormal == 1 && isAttackForAttack == 1){
            1
        } else {
            1
        }

    }

    fun close() {
        sessionNormal.close()
        sessionAttack.close()
        env.close()
    }
}
