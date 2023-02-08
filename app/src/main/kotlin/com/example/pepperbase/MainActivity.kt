package com.example.pepperbase

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
import com.aldebaran.qi.sdk.util.FutureUtils
import io.grpc.pepper.pepper_command.Command
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader

private const val ROBOT_ANIMATION_TAG = "RobotAnimation"

class MainActivity : RobotActivity(), RobotLifecycleCallbacks {

    private var qiContext: QiContext? = null
    private var grpcClient: GrpcClient? = null
    private var awarenessHolder: Holder? = null
    private var animations: HashMap<String, Animate?> = HashMap()
    private var lastAnimation: Future<Void>? = null
    private var lastSentence: Future<Void>? = null
    private var goTo: Future<Void>? = null
    private var homeFrame: FreeFrame? = null

    private val job = Job()
    private val bgContext = job + Dispatchers.Default

    private val bgScope = CoroutineScope(bgContext)

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
            animations[File(file).nameWithoutExtension] =
                buildAnimation(qiContext, "animations/${file}")
        }

        // Holding abilities means disabling them in this context. The awareness holder allows
        awarenessHolder = HolderBuilder.with(qiContext)
            .withAutonomousAbilities(AutonomousAbilitiesType.BASIC_AWARENESS)
            .build()

        val connectToServerButton: Button = findViewById(R.id.connect_to_server_button)
        connectToServerButton.setOnClickListener {
            setupGrpcResponse()
        }

        // Store initial frame as Home.
        val robotFrameFuture = qiContext.actuation?.async()?.robotFrame()
        robotFrameFuture?.andThenConsume { robotFrame ->
            // Create a FreeFrame representing the current robot frame.
            val locationFrame: FreeFrame? = qiContext.mapping?.makeFreeFrame()
            val transform: Transform = TransformBuilder.create().fromXTranslation(0.0)
            locationFrame?.update(robotFrame, transform, 0L)

            // Store the FreeFrame.
            homeFrame = locationFrame
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
        homeFrame = null
        this.qiContext = null
    }

    override fun onRobotFocusRefused(reason: String) {
        // The robot focus is refused.
    }

    private fun pingServer(url: URL): String? {
        var logText: String? = null

        try {
            with(url.openConnection() as HttpURLConnection) {
                this.requestMethod = "GET"
                BufferedReader(InputStreamReader(inputStream)).use {
                    val msg: String = it.readText()
                    logText = msg
                }
            }
        } catch (e: Exception) {
            logText = e.stackTraceToString()
        }
        return logText
    }

    private fun setupGrpcResponse() {
        //val ip = "192.168.1.15" // set to host ip for testing
        //val ip = "10.204.18.88"
        val ip = findViewById<EditText>(R.id.raspberryIP).text

        lifecycleScope.launch {
            val response: String? = async(bgScope.coroutineContext) {
                return@async pingServer(URL("http://${ip}:5002/ping"))
            }.await()
            val ltv: TextView = findViewById(R.id.log_text_view)
            ltv.text = response

            val uri = Uri.parse("http://${ip}:50051/")
            grpcClient = GrpcClient(uri)
            grpcClient?.executeOnCommand { cmd: Command ->
                Log.i("cmd", cmd.toString())
                val cmdFutures: MutableList<Future<Void>?> = mutableListOf()

                // Animation process
                if (cmd.animation != Command.Animation.getDefaultInstance()) {
                    lastAnimation =
                        if (lastAnimation != null && lastAnimation?.isDone?.not() == true) {
                            if (cmd.animation.haltLast) {
                                lastAnimation?.requestCancellation()
                            }
                            lastAnimation?.thenCompose {
                                runAnimation(cmd)
                            }
                        } else {
                            runAnimation(cmd)
                        }
                    cmdFutures.add(lastAnimation)
                }

                // Speech process
                if (cmd.say.isNotEmpty()) {
                    lastSentence =
                        if (lastSentence != null && lastSentence?.isDone?.not() == true) {
                            lastSentence?.thenCompose {
                                runTTS(cmd)
                            }
                        } else {
                            runTTS(cmd)
                        }
                    cmdFutures.add(lastSentence)
                }

                // Rotate position
                if (cmd.goto != Command.Translation2D.getDefaultInstance()) {
                    goTo = if (goTo != null && goTo?.isDone?.not() == true) {
                        goTo?.thenCompose {
                            runGoTo(cmd)
                        }
                    } else {
                        runGoTo(cmd)
                    }
                    cmdFutures.add(goTo)
                }

                // Enable/disable look at human
                if (cmd.abilitiesList.isNotEmpty()) {
                    val awToggle = runAbilityToggle(cmd)
                    cmdFutures.add(awToggle)
                }

                // Collect all futures and send result when they are done.
                FutureUtils.zip(cmdFutures)?.thenConsume { future ->
                    lifecycleScope.launch {
                        if (future.isSuccess) {
                            grpcClient?.notifyAnimationEnded(cmd.uuid, "Command Success")
                        } else {
                            grpcClient?.notifyAnimationEnded(cmd.uuid, "Command Failure")
                        }
                    }
                }
            }
        }
    }

    private fun runAnimation(cmd: Command): Future<Void>? {
        return animations[cmd.animation.name]?.async()?.run()?.thenConsume { future ->
            lifecycleScope.launch {
                if (future.isSuccess) {
                    grpcClient?.notifyAnimationEnded(cmd.uuid, "Animation Success")
                } else {
                    grpcClient?.notifyAnimationEnded(cmd.uuid, "Animation Failure")
                }
            }
        }
    }

    private fun runTTS(cmd: Command): Future<Void>? {
        return buildSpeech(cmd.say)
            .async().run()?.thenConsume { future ->
            lifecycleScope.launch {
                if (future.isSuccess) {
                    grpcClient?.notifyAnimationEnded(cmd.uuid, "Speech Success")
                } else {
                    grpcClient?.notifyAnimationEnded(cmd.uuid, "Speech Failure")
                }
            }
        }
    }

    private fun runGoTo(cmd: Command): Future<Void>? {
        val fut: GoTo = if (cmd.goto.relative) {
            buildGoTo(cmd.goto.x, cmd.goto.y, cmd.goto.theta)
        } else {
            // TODO: Make new method to perform global GoTo movements, move the GoToHome  command to
            //  a different gRPC variable
            buildGoToHome()
        }
        return fut.async()?.run()?.thenConsume { future ->
            lifecycleScope.launch {
                if (future.isSuccess) {
                    grpcClient?.notifyAnimationEnded(cmd.uuid, "Movement Success")
                } else {
                    grpcClient?.notifyAnimationEnded(cmd.uuid, "Movement Failure")
                }
            }
        }
    }

    private fun runAbilityToggle(cmd: Command): Future<Void>? {
        val awToggleList: MutableList<Future<Void>?> = mutableListOf()
        for (ability in cmd.abilitiesList) {
            when(ability.ty) {
                Command.AutonomousAbilities.Ability.BASIC_AWARENESS ->
                    if (ability.enabled) {
                        awToggleList.add(awarenessHolder?.async()?.release())
                    } else {
                        awToggleList.add(awarenessHolder?.async()?.hold())
                    }
                else -> {}
            }
        }

        return FutureUtils.zip(awToggleList)?.thenConsume { future ->
            lifecycleScope.launch {
                if (future.isSuccess) {
                    grpcClient?.notifyAnimationEnded(cmd.uuid, "Speech Success")
                } else {
                    grpcClient?.notifyAnimationEnded(cmd.uuid, "Speech Failure")
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

    private fun buildGoToHome(): GoTo {
        return GoToBuilder.with(qiContext)
            .withFrame(homeFrame?.async()?.frame()?.get())
            .withFinalOrientationPolicy(OrientationPolicy.ALIGN_X)
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
            .withFinalOrientationPolicy(OrientationPolicy.ALIGN_X)
            .buildAsync()
            .get()
    }
}
