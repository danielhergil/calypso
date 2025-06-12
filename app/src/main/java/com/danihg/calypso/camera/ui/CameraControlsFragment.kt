package com.danihg.calypso.camera.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.danihg.calypso.R
import com.danihg.calypso.camera.models.CameraViewModel
import com.danihg.calypso.constants.ACTION_START_RECORD
import com.danihg.calypso.constants.ACTION_START_STREAM
import com.danihg.calypso.constants.ACTION_STOP_RECORD
import com.danihg.calypso.constants.ACTION_STOP_STREAM
import com.danihg.calypso.constants.EXTRA_PATH
import com.danihg.calypso.constants.EXTRA_URL
import com.danihg.calypso.services.CameraService
import com.danihg.calypso.utils.storage.StorageUtils
import com.danihg.calypso.utils.ui.showTemporarySpinnerOn
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class CameraControlsFragment : Fragment(R.layout.fragment_camera_controls) {

    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream

    private lateinit var btnRecord: MaterialButton
    private lateinit var btnStream: MaterialButton
    private lateinit var btnPicture: MaterialButton

    // Permission launcher for CameraControlsFragment
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            Toast.makeText(
                requireContext(),
                "Permissions are required to record/stream",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // holds the session ID generated when streaming (or recording) starts
    private var sessionId: String? = null

    private var currentStreamUrl: String = ""

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnRecord  = view.findViewById(R.id.btnRecordMode)
        btnStream  = view.findViewById(R.id.btnStreamMode)
        btnPicture = view.findViewById(R.id.btnPictureMode)

        // 1) Forzamos iconos por defecto (para que siempre aparezcan si vienen del XML)
        btnRecord.setIconResource(R.drawable.ic_record_mode)
        btnRecord.iconTint = null

        btnStream.setIconResource(R.drawable.ic_stream_mode)
        btnStream.iconTint = null

        // 2) btnStream deshabilitado hasta que haya URL válida
        btnStream.isEnabled = false
        btnStream.alpha = 0.4f

        cameraViewModel.streamUrl.observe(viewLifecycleOwner) { url ->
            currentStreamUrl = url
            val isValid = url.isNotBlank() && url != "None"
            btnStream.isEnabled = isValid
            btnStream.alpha = if (isValid) 1f else 0.4f
        }

        // 3) Listeners
        btnRecord.setOnClickListener {
            if (hasRequiredPermissions()) {
                toggleRecord()
            } else {
                requestMissingPermissions()
            }
        }
        btnStream.setOnClickListener {
            if (hasRequiredPermissions()) {
                toggleStream()
            } else {
                requestMissingPermissions()
            }
        }
        btnPicture.setOnClickListener {
            val root = requireActivity().findViewById<ViewGroup>(android.R.id.content)

            // 1) FLASH BLANCO
            val flashView = View(requireContext()).apply { setBackgroundColor(Color.WHITE) }
            root.addView(flashView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            flashView.alpha = 0f
            flashView.animate()
                .alpha(1f)
                .setDuration(100)
                .withEndAction {
                    flashView.animate()
                        .alpha(0f)
                        .setDuration(100)
                        .withEndAction { root.removeView(flashView) }
                }

            // 2) Tomar foto
            genericStream.getGlInterface().takePhoto { bitmap ->
                requireActivity().runOnUiThread {
                    val density = resources.displayMetrics.density
                    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val marginStartPx = if (isLandscape) {
                        (64 * density).toInt()
                    } else {
                        (24 * density).toInt()
                    }
                    val marginBottomPx = if (isLandscape) {
                        (24 * density).toInt()
                    } else {
                        (84 * density).toInt()
                    }
                    val maxPx = (96 * density).toInt()

                    // 3) Usar dimensiones reales del bitmap para mantener proporción
                    val (thumbW, thumbH) = if (isLandscape) {
                        // Landscape: ancho fijo
                        val w = maxPx
                        val h = (bitmap.height * w / bitmap.width.toFloat()).toInt()
                        Log.d("CameraControlsFragment", "landscape")
                        w to h
                    } else {
                        // Portrait: alto fijo
                        val h = maxPx
                        val w = 160
                        Log.d("CameraControlsFragment", "portrait")
                        w to h
                    }
                    Log.d("CameraControlsFragment", "onViewCreated(): bitmap.width=${bitmap.width}, bitmap.height=${bitmap.height}")
                    Log.d("CameraControlsFragment", "onViewCreated(): thumbW=$thumbW, thumbH=$thumbH")
                    // 4) CardView con borde blanco y esquinas redondeadas
                    val card = MaterialCardView(requireContext()).apply {
                        layoutParams = FrameLayout.LayoutParams(thumbW, thumbH).apply {
                            gravity = Gravity.BOTTOM or Gravity.START
                            setMargins(marginStartPx, 0, 0, marginBottomPx)
                        }
                        strokeWidth = (2 * density).toInt()
                        strokeColor = Color.WHITE
                        radius = 8 * density
                        preventCornerOverlap = true
                        useCompatPadding = true
                        elevation = 4 * density
                    }

                    val thumb = ImageView(requireContext()).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageBitmap(bitmap)
                    }

                    card.addView(thumb)
                    root.addView(card)

                    Handler(Looper.getMainLooper()).postDelayed({
                        root.removeView(card)
                    }, 3000L)
                }
            }
        }

        // 4) Sincronizamos iconos con el estado real justo al crear la vista
        syncButtonStates()
    }

    private fun rotateBitmapIfNeeded(original: Bitmap): Bitmap {
        return if (original.width > original.height) {
            // Rotamos 90° para que sea visualmente portrait
            val matrix = Matrix().apply { postRotate(90f) }
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        } else {
            original
        }
    }

    override fun onResume() {
        super.onResume()
        // Cada vez que regresamos a foreground, sincronizamos iconos
        syncButtonStates()
    }

    private fun toggleRecord() {
        val ctx = requireContext()
        val intent = Intent(ctx, CameraService::class.java)

        val stream = genericStream

        if (!stream.isRecording) {
            // === INICIAR grabación ===

            // a) (Opcional) Antes del spinner, podemos atenuar el icono para indicar “loading”:
            btnRecord.setIconResource(R.drawable.ic_record_mode)
            btnRecord.iconTint = null
            btnRecord.alpha = 0.5f  // Atenuado al 50% mientras dura spinner

            // b) Llenamos la sessionId
            sessionId = StorageUtils.generateSessionId()
            val tempPath = StorageUtils.getTempRecordFile().absolutePath

            // c) Enviamos Intent al servicio
            intent.action = ACTION_START_RECORD
            intent.putExtra(EXTRA_PATH, tempPath)
            ContextCompat.startForegroundService(ctx, intent)

            // d) Mostramos spinner 2 s; al terminar, volvemos a sincronizar iconos con isRecording=true
            showTemporarySpinnerOn(btnRecord, 2000L) {
                // Restauramos alpha y pintamos el icono según estado real
                btnRecord.alpha = 1f
                syncButtonStates()  // Como el servicio ya habrá puesto isRecording=true, se mostrará ic_stop+rojo
            }
        } else {
            // === DETENER grabación ===

            // a) Atenuamos icono mientras dura spinner:
            btnRecord.setIconResource(R.drawable.ic_stop)
            btnRecord.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.calypso_red)
            )
            btnRecord.alpha = 0.5f

            // b) Enviamos Intent al servicio para остановить la grabación
            intent.action = ACTION_STOP_RECORD
            ContextCompat.startForegroundService(ctx, intent)

            // c) Spinner 2 s; al terminar, restauramos UI según isRecording=false
            showTemporarySpinnerOn(btnRecord, 2000L) {
                btnRecord.alpha = 1f
                syncButtonStates()  // Ahora isRecording=false, así que se pondrá ic_record_mode
            }
        }
    }

    private fun toggleStream() {
        val ctx = requireContext()
        val intent = Intent(ctx, CameraService::class.java)
        val stream = genericStream

        if (!stream.isStreaming) {
            // === INICIAR streaming ===

            // a) Atenuamos icono mientras dura spinner:
            btnStream.setIconResource(R.drawable.ic_stream_mode)
            btnStream.iconTint = null
            btnStream.alpha = 0.5f

            // b) Generamos sessionId (opcional)
            sessionId = StorageUtils.generateSessionId()

            // c) Enviamos Intent para arrancar streaming
            intent.action = ACTION_START_STREAM
            intent.putExtra(EXTRA_URL, currentStreamUrl)
            ContextCompat.startForegroundService(ctx, intent)

            // d) Spinner 2 s; al terminar, restauramos icono según isStreaming=true
            showTemporarySpinnerOn(btnStream, 2000L) {
                btnStream.alpha = 1f
                syncButtonStates()  // Como el servicio ya puso isStreaming=true, se ve ic_stop+rojo
            }
        } else {
            // === DETENER streaming ===

            // a) Atenuamos icono
            btnStream.setIconResource(R.drawable.ic_stop)
            btnStream.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.calypso_red)
            )
            btnStream.alpha = 0.5f

            // b) Enviamos Intent para parar streaming
            intent.action = ACTION_STOP_STREAM
            ContextCompat.startForegroundService(ctx, intent)

            // c) Spinner 2 s; al terminar, restauramos icono según isStreaming=false
            showTemporarySpinnerOn(btnStream, 2000L) {
                btnStream.alpha = 1f
                syncButtonStates()  // isStreaming=false → ic_stream_mode
            }
        }
    }

    private fun syncButtonStates() {
        Log.d("CameraControls", "syncButtonStates(): isRecording=${genericStream.isRecording}, isStreaming=${genericStream.isStreaming}")

        // Record
        if (genericStream.isRecording) {
            btnRecord.setIconResource(R.drawable.ic_stop)
            btnRecord.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.calypso_red)
            )
        } else {
            btnRecord.setIconResource(R.drawable.ic_record_mode)
            btnRecord.iconTint = null
        }

        // Stream
        if (genericStream.isStreaming) {
            btnStream.setIconResource(R.drawable.ic_stop)
            btnStream.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.calypso_red)
            )
        } else {
            btnStream.setIconResource(R.drawable.ic_stream_mode)
            btnStream.iconTint = null
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val context = requireContext()
        val cameraGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val audioGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val fgsMicGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val fgsCameraGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.FOREGROUND_SERVICE_CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return cameraGranted && audioGranted && fgsMicGranted && fgsCameraGranted
    }

    private fun requestMissingPermissions() {
        val permsToRequest = mutableListOf<String>()
        val context = requireContext()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permsToRequest.add(Manifest.permission.CAMERA)
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
                ) != PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.FOREGROUND_SERVICE_CAMERA
                ) != PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_CAMERA)
            }
        }

        if (permsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permsToRequest.toTypedArray())
        } else {
            Toast.makeText(
                context,
                "All permissions already granted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
