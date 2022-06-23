package com.example.pepperbase

import android.net.Uri
import com.google.protobuf.Empty
import io.grpc.ManagedChannelBuilder
import io.grpc.pepper.pepper_command.Command
import io.grpc.pepper.pepper_command.PepperGrpcKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.collect

import java.io.Closeable
import java.lang.Exception

class GrpcClient(uri: Uri) : Closeable {

    private val channel = let {
        println("Connecting to ${uri.host}:${uri.port}")
        val builder = ManagedChannelBuilder.forAddress(uri.host, uri.port)
        if (uri.scheme == "https") {
            builder.useTransportSecurity()
        } else {
            builder.usePlaintext()
        }
        builder.executor(Dispatchers.IO.asExecutor()).build()
    }

    private val client = PepperGrpcKt.PepperCoroutineStub(channel)

    suspend fun executeOnCommand(onCommand: (Command) -> Unit) {
        try {
            val response = client.listenMovementCommand(Empty.newBuilder().build())
            response.collect { c: Command ->  onCommand(c) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun close() {
        channel.shutdownNow()
    }
}
