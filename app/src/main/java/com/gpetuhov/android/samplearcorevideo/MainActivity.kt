package com.gpetuhov.android.samplearcorevideo

import android.app.Activity
import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.media.CamcorderProfile
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.animation.ModelAnimator
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.TransformableNode
import com.pawegio.kandroid.toast

class MainActivity : AppCompatActivity() {

    companion object {
        const val MIN_OPENGL_VERSION = 3.0
    }

    private var arFragment: WritingArFragment? = null
    private var animationButton: FloatingActionButton? = null
    private var videoButton: FloatingActionButton? = null
    private var modelRenderable: ModelRenderable? = null

    // Controls animation playback.
    private var animator: ModelAnimator? = null

    // VideoRecorder encapsulates all the video recording functionality.
    private var videoRecorder: VideoRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return
        }

        setContentView(R.layout.activity_main)
        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as WritingArFragment
        animationButton = findViewById(R.id.animate)
        videoButton = findViewById(R.id.video)

        animationButton?.setOnClickListener { playAnimation() }
        videoButton?.setOnClickListener { toggleVideo() }

        loadModel()

        arFragment?.setOnTapArPlaneListener(::onPlaneTap)

        // TODO: refactor this

        // Initialize the VideoRecorder.
        videoRecorder = VideoRecorder()
        val orientation = resources.configuration.orientation
        videoRecorder?.setVideoQuality(CamcorderProfile.QUALITY_2160P, orientation)
        videoRecorder?.setSceneView(arFragment?.arSceneView)

        videoButton?.isEnabled = true
        videoButton?.setImageResource(R.drawable.ic_videocam)
    }

    /**
     * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
     * on this device.
     *
     * Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
     *
     * Finishes the activity if Sceneform can not run
     */
    private fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            toast("Sceneform requires Android N or later")
            activity.finish()
            return false
        }

        val openGlVersionString = (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo
            .glEsVersion

        if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION) {
            toast("Sceneform requires OpenGL ES 3.0 or later")
            activity.finish()
            return false
        }

        return true
    }

    private fun loadModel() {
        ModelRenderable.builder()
            .setSource(this, Uri.parse("file:///android_asset/andy_dance.sfb"))
            .build()
            .thenAccept { renderable -> modelRenderable = renderable }
            .exceptionally { throwable ->
                toast("Unable to load renderable")
                null
            }
    }

    private fun onPlaneTap(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent) {
        if (modelRenderable == null) {
            return
        }

        // Create the Anchor at the place of the tap.
        val anchor = hitResult.createAnchor()
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arFragment?.arSceneView?.scene)

        // Create the transformable model and add it to the anchor.
        val model = TransformableNode(arFragment?.transformationSystem)
        model.setParent(anchorNode)
        model.renderable = modelRenderable
        model.select()
    }

    // This plays animation simultaneously on ALL models with the same ModelRenderable
    private fun playAnimation() {
        if (animator == null || animator?.isRunning != true) {
            val data = modelRenderable?.getAnimationData(0)
            animator = ModelAnimator(data, modelRenderable)
            animator?.start()
        }
    }

    private fun toggleVideo() {
        if (arFragment?.hasWritePermission() != true) {
            toast("Video recording requires the WRITE_EXTERNAL_STORAGE permission")
            arFragment?.launchPermissionSettings()
            return
        }

        val recording = videoRecorder?.onToggleRecord() ?: false

        if (recording) {
            videoButton?.setImageResource(R.drawable.ic_stop)

        } else {
            videoButton?.setImageResource(R.drawable.ic_videocam)
            val videoPath = videoRecorder?.videoPath?.absolutePath ?: ""
            toast("Video saved: $videoPath")

            // Send  notification of updated content.
            val values = ContentValues()
            values.put(MediaStore.Video.Media.TITLE, "Sceneform Video")
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            values.put(MediaStore.Video.Media.DATA, videoPath)
            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }
    }
}
