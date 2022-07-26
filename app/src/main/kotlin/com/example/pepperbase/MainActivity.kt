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
import com.aldebaran.qi.sdk.`object`.actuation.*
import com.aldebaran.qi.sdk.`object`.conversation.Phrase
import com.aldebaran.qi.sdk.`object`.conversation.Say
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.`object`.holder.Holder
import com.aldebaran.qi.sdk.builder.*
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import io.grpc.pepper.pepper_command.Command
import kotlinx.coroutines.launch
import java.io.File

private const val ROBOT_ANIMATION_TAG = "RobotAnimation"

class MainActivity : RobotActivity(), RobotLifecycleCallbacks {

    private var qiContext: QiContext? = null
    private var awarenessHolder: Holder? = null
    private var animations: HashMap<String, Animate?> = HashMap()
    private var lastAnimation: Future<Void>? = null
    private var lastSentence: Future<Void>? = null
    private var goTo: Future<Void>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Register the RobotLifecycleCallbacks to this Activity.
        QiSDK.register(this, this)
    }

    override fun onDestroy() {
        // Unregister the RobotLifecycleCallbacks for this Activity.
        QiSDK.unregister(this, this)
        super.onDestroy()
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext
        for (file in qiContext.assets.list("animations")!!) {
            animations[File(file).nameWithoutExtension] = buildAnimation(qiContext, "animations/${file}")
        }

        // Holding abilities means disabling them in this context. The awareness holder allows
        awarenessHolder = HolderBuilder.with(qiContext)
            .withAutonomousAbilities(AutonomousAbilitiesType.BASIC_AWARENESS)
            .build()

        val connectToServerButton: Button = findViewById(R.id.connect_to_server_button)
        connectToServerButton.setOnClickListener {
            setupGrpcResponse()
        }
    }

    override fun onRobotFocusLost() {
        // The robot focus is lost.
        for (animate in animations.values) {
            animate?.removeAllOnStartedListeners()
        }
        animations.clear()
        lastAnimation = null
        lastSentence = null
        goTo = null
        this.qiContext = null
    }

    override fun onRobotFocusRefused(reason: String) {
        // The robot focus is refused.
    }

    private fun setupGrpcResponse() {
        val ip: String = findViewById<EditText>(R.id.server_ip_input).text.toString()
//        val uri = Uri.parse("http://${ip}:50051/")
        val uri = Uri.parse("http://10.204.45.93:50051/")
        val grpcClient = GrpcClient(uri)
        lifecycleScope.launch {
            grpcClient.executeOnCommand { cmd: Command ->
                Log.i("cmd", cmd.toString())
                // Animation process
                if (cmd.animation != null) {
                    if (lastAnimation != null && lastAnimation?.isDone?.not() == true) {
                        lastAnimation?.thenCompose {
                            lastAnimation = animations[cmd.animation.name]?.async()?.run()
                            return@thenCompose lastAnimation
                        }
                        if (cmd.animation.haltLast) {
                            lastAnimation?.requestCancellation()
                        }
                    }
                    else {
                        lastAnimation = animations[cmd.animation.name]?.async()?.run()
                        lastAnimation?.thenConsume { future ->
                            if (future.isSuccess) {
                                Log.i("Animation result:", "Success")
                            } else if (future.isCancelled) {
                                Log.i("Animation result:", "Cancelled")
                            }
                        }
                    }
                }

                // Speech process
                if (cmd.say.isNotEmpty()) {
                    lastSentence = buildSpeech(cmd.say).async().run()
                    lastSentence?.thenConsume { future ->
                        if (future.isSuccess) {
                            Log.i("Speech result:", "Success")
                        } else if (future.isCancelled) {           
                            Log.i("Speech result:", "Cancelled")
                        }
                    }
                }

                // Rotate position
                if (cmd.goto != null) {
                    goTo = buildGoTo(cmd.goto.x, cmd.goto.y, cmd.goto.theta).async()?.run()
                    goTo?.thenConsume { future ->
                        if (future.isSuccess) {
                            Log.i("Movement result:", "Success")
                        } else if (future.isCancelled) {
                            Log.i("Movement result:", "Cancelled")
                        }
                    }
                }

                // Enable/disable look at human
                for (ability in cmd.abilitiesList) {
                    when(ability.ty) {
                        Command.AutonomousAbilities.Ability.BASIC_AWARENESS ->
                            if (ability.enabled) {
                                awarenessHolder?.async()?.hold()
                            } else {
                                awarenessHolder?.async()?.release()
                            }
                        else -> {}
                    }
                }
            }
        }
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

    private fun buildSpeech(text: String): Say {
        val phrase = Phrase(text)
        return SayBuilder.with(qiContext)
            .withPhrase(phrase)
            .buildAsync()
            .get()
    }

    private fun buildGoTo(x: Double, y: Double, theta: Double): GoTo {
        val actuation = qiContext?.actuation
        val robotFrame = actuation?.async()?.robotFrame()?.get()
        val transform: Transform = TransformBuilder
            .create()
            .from2DTransform(x, y, theta)
        val mapping = qiContext?.mapping
        val targetFrame = mapping?.async()?.makeFreeFrame()?.get()
        targetFrame?.async()?.update(robotFrame, transform, 0L)

        return GoToBuilder.with(qiContext)
            .withFrame(targetFrame?.async()?.frame()?.get())
            .buildAsync()
            .get()
    }
}