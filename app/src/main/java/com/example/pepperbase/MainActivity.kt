package com.example.pepperbase

import android.os.Bundle
import android.util.Log
import android.widget.Button
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

private const val ROBOT_ANIMATION_TAG = "RobotAnimation"

class MainActivity : RobotActivity(), RobotLifecycleCallbacks {

    private var animate: Animate? = null

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
        Thread.sleep(5000)
        val startAnimationButton: Button = findViewById(R.id.startAnimation)
        val animation = buildAnimation(qiContext, R.raw.bow_a001)
        startAnimationButton.setOnClickListener {
            animation?.run()
        }
        val startSpeechButton: Button = findViewById(R.id.startSpeech)
        val speech = buildSpeech(qiContext, "Hello world!")
        startSpeechButton.setOnClickListener {
            speech.run()
        }
    }

    override fun onRobotFocusLost() {
        // The robot focus is lost.
        animate?.removeAllOnStartedListeners()

    }

    override fun onRobotFocusRefused(reason: String) {
        // The robot focus is refused.
    }

    private fun buildAnimation(qiContext: QiContext, ResourceId: Int): Animate.Async? {
        // The robot focus is gained.
        val animation: Animation =
            AnimationBuilder.with(qiContext).withResources(ResourceId).build()

        animate = AnimateBuilder.with(qiContext).withAnimation(animation).build()

        animate?.addOnStartedListener { Log.i(ROBOT_ANIMATION_TAG, "Animation started.") }
        return animate?.async()
    }

    private fun buildSpeech(qiContext: QiContext, text: String): Say.Async {
        val phrase = Phrase(text)
        val say: Say = SayBuilder.with(qiContext)
            .withPhrase(phrase)
            .build()
        return say.async()
    }
}