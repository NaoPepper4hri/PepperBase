package com.example.pepperbase

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.aldebaran.qi.Future
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
import kotlinx.coroutines.launch
import java.io.File

private const val ROBOT_ANIMATION_TAG = "RobotAnimation"

class MainActivity : RobotActivity(), RobotLifecycleCallbacks {

    private var animations: HashMap<String, Animate?> = HashMap()
    private var lastAnimation: Future<Void>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Register the RobotLifecycleCallbacks to this Activity.
        QiSDK.register(this, this)

        val connectToServerButton: Button = findViewById(R.id.connect_to_server_button)
        connectToServerButton.setOnClickListener {
            val ip: String = findViewById<EditText>(R.id.server_ip_input).text.toString()
            val uri = Uri.parse("http://${ip}:50051/")
            val grpcClient = GrpcClient(uri)
            lifecycleScope.launch {
                grpcClient.executeOnCommand { cmd ->
                    if (lastAnimation != null && lastAnimation?.isDone?.not() == true)
                    {
                        lastAnimation?.thenCompose {
                            lastAnimation = animations[cmd.movement]?.async()?.run()
                            return@thenCompose lastAnimation
                        }
                        if (cmd.haltLast) {
                            lastAnimation?.requestCancellation()
                        }
                    }
                    else {
                        lastAnimation = animations[cmd.movement]?.async()?.run()
                        lastAnimation?.thenConsume { future ->
                            if (future.isSuccess) {
                                Log.i("Animation result:", "Success")
                            } else if (future.isCancelled) {
                                Log.i("Animation result:", "Cancelled")
                            }
                        }
                    }
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
        for (animate in animations.values) {
            animate?.removeAllOnStartedListeners()
        }
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
        val animate = AnimateBuilder.with(qiContext).withAnimation(animation).build()

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