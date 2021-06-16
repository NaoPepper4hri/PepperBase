package com.example.pepperbase

import android.os.Bundle
import android.util.Log
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.Animate
import com.aldebaran.qi.sdk.`object`.actuation.Animation
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity

private const val ROBOT_ANIMATION_TAG = "RobotAnimation"

class MainActivity : RobotActivity(), RobotLifecycleCallbacks {

    private var animate: Animate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        // The robot focus is gained.
        val animation: Animation =
            AnimationBuilder.with(qiContext).withResources(R.raw.bow_a001).build()

        animate = AnimateBuilder.with(qiContext).withAnimation(animation).build()

        animate?.addOnStartedListener { Log.i(ROBOT_ANIMATION_TAG, "Animation started.") }
        val animateFut = animate?.async()?.run()
        animateFut?.thenConsume { future ->
            if (future.isSuccess) {
                Log.i(ROBOT_ANIMATION_TAG, "Animation finished with success.")
            } else if (future.hasError()) {
                Log.e(ROBOT_ANIMATION_TAG, "Animation finished with error.", future.error)
            }
        }
    }

    override fun onRobotFocusLost() {
        // The robot focus is lost.
        animate?.removeAllOnStartedListeners()

    }

    override fun onRobotFocusRefused(reason: String) {
        // The robot focus is refused.
    }
}