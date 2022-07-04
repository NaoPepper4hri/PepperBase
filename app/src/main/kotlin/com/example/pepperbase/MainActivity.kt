package com.example.pepperbase

import android.content.res.AssetManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.Animate
import com.aldebaran.qi.sdk.`object`.actuation.Animation
import com.aldebaran.qi.sdk.`object`.conversation.Phrase
import com.aldebaran.qi.sdk.`object`.conversation.Say
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files.list
import java.util.Collections.list

private const val ROBOT_ANIMATION_TAG = "RobotAnimation"

class MainActivity : RobotActivity(), RobotLifecycleCallbacks {

    private var animate: Animate? = null
    private var animations: HashMap<String, Animate?> = HashMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Register the RobotLifecycleCallbacks to this Activity.
        QiSDK.register(this, this)

        val connectToServerButton: Button = findViewById(R.id.connect_to_server_button)
        connectToServerButton.setOnClickListener {
            val ip: String = findViewById<EditText>(R.id.server_ip_input).text.toString()
            val uri = Uri.parse("http://${ip}/")
            val grpcClient = GrpcClient(uri)
            lifecycleScope.launch {
                grpcClient.executeOnCommand {
                    animations[it.movement]?.async()?.run()
                }
            }
        }

    }

    override fun onDestroy() {
        // Unregister the RobotLifecycleCallbacks for this Activity.
        QiSDK.unregister(this, this)
        super.onDestroy()
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        for (file in qiContext.assets.list("animations")!!) {
            animations[File(file).nameWithoutExtension] = buildAnimation(qiContext, "animations/${file}")
        }
    }

    override fun onRobotFocusLost() {
        // The robot focus is lost.
        animate?.removeAllOnStartedListeners()
    }

    override fun onRobotFocusRefused(reason: String) {
        // The robot focus is refused.
    }

    private fun buildAnimation(qiContext: QiContext, assetId: String): Animate? {
        val animation: Animation =
            AnimationBuilder.with(qiContext).withAssets(assetId).build()
        return buildAnimation(qiContext, animation)
    }

    private fun buildAnimation(qiContext: QiContext, ResourceId: Int): Animate? {
        val animation: Animation =
            AnimationBuilder.with(qiContext).withResources(ResourceId).build()
        return buildAnimation(qiContext, animation)
    }

    private fun buildAnimation(qiContext: QiContext, animation: Animation): Animate? {
        animate = AnimateBuilder.with(qiContext).withAnimation(animation).build()

        animate?.addOnStartedListener { Log.i(ROBOT_ANIMATION_TAG, "Animation started.") }
        return animate
    }

    private fun buildSpeech(qiContext: QiContext, text: String): Say.Async {
        val phrase = Phrase(text)
        val say: Say = SayBuilder.with(qiContext)
            .withPhrase(phrase)
            .build()
        return say.async()
    }
}