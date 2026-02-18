/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.settings.gateway

import android.Manifest
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Internal state of the QR scanner screen.
 */
private sealed interface ScannerState {
    /** Camera permission has not been granted. */
    data object PermissionRequired : ScannerState

    /** Camera is active and scanning for QR codes. */
    data object Scanning : ScannerState

    /**
     * A QR code was successfully scanned.
     *
     * @property token The decoded token string.
     */
    data class Scanned(
        val token: String,
    ) : ScannerState

    /**
     * An error occurred.
     *
     * @property message Human-readable error description.
     */
    data class Error(
        val message: String,
    ) : ScannerState
}

/**
 * Full-screen QR code scanner for gateway pairing tokens.
 *
 * Uses CameraX for camera preview and ML Kit barcode scanning
 * for QR code detection. Only FORMAT_QR_CODE is enabled to
 * reduce frame analysis overhead.
 *
 * Provides runtime camera permission request, a viewfinder overlay,
 * and haptic feedback on successful scan. Cleans up camera resources
 * via [DisposableEffect].
 *
 * @param onTokenScanned Callback with the decoded token string on success.
 * @param onBack Callback when the user navigates back.
 * @param modifier Modifier applied to the root layout.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod", "UnusedParameter")
@Composable
fun QrScannerScreen(
    onTokenScanned: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hapticFeedback = LocalHapticFeedback.current

    var scannerState by remember { mutableStateOf<ScannerState>(ScannerState.PermissionRequired) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            scannerState =
                if (granted) ScannerState.Scanning else ScannerState.PermissionRequired
        }

    LaunchedEffect(Unit) {
        val hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            scannerState = ScannerState.Scanning
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    when (val state = scannerState) {
        is ScannerState.PermissionRequired -> {
            PermissionRequestContent(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = modifier,
            )
        }

        is ScannerState.Scanning -> {
            CameraScanContent(
                onScanned = { token ->
                    scannerState = ScannerState.Scanned(token)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTokenScanned(token)
                },
                onError = { scannerState = ScannerState.Error(it) },
                lifecycleOwner = lifecycleOwner,
                modifier = modifier,
            )
        }

        is ScannerState.Scanned -> {
            ScanSuccessContent(
                token = state.token,
                modifier = modifier,
            )
        }

        is ScannerState.Error -> {
            ErrorContent(
                message = state.message,
                onRetry = { scannerState = ScannerState.Scanning },
                modifier = modifier,
            )
        }
    }
}

/**
 * Content shown when camera permission is required.
 *
 * @param onRequestPermission Callback to request the camera permission.
 * @param modifier Modifier applied to the layout.
 */
@Composable
private fun PermissionRequestContent(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.semantics {
                    contentDescription =
                        "Camera permission required to scan QR codes"
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SPACING_MEDIUM))
            Text(
                text = "Camera permission required",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(SPACING_SMALL))
            Text(
                text = "Grant camera access to scan QR codes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SPACING_LARGE))
            FilledTonalButton(
                onClick = onRequestPermission,
                modifier =
                    Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Grant camera permission"
                    },
            ) {
                Text("Grant Permission")
            }
        }
    }
}

/**
 * Camera preview content with viewfinder overlay and barcode scanning.
 *
 * @param onScanned Callback with decoded QR token on first successful scan.
 * @param onError Callback when camera setup fails.
 * @param lifecycleOwner Lifecycle owner for camera binding.
 * @param modifier Modifier applied to the layout.
 */
@Suppress("TooGenericExceptionCaught")
@Composable
private fun CameraScanContent(
    onScanned: (String) -> Unit,
    onError: (String) -> Unit,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasScanned by remember { mutableStateOf(false) }
    val scanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose { scanner.close() }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Camera viewfinder for QR code scanning"
                },
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener(
                    {
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview =
                                Preview
                                    .Builder()
                                    .build()
                                    .also { it.surfaceProvider = previewView.surfaceProvider }

                            @Suppress("MagicNumber")
                            val imageAnalysis =
                                ImageAnalysis
                                    .Builder()
                                    .setTargetResolution(Size(1280, 720))
                                    .setBackpressureStrategy(
                                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST,
                                    ).build()
                                    .also { analysis ->
                                        analysis.setAnalyzer(
                                            ContextCompat.getMainExecutor(ctx),
                                        ) { imageProxy ->
                                            if (hasScanned) {
                                                imageProxy.close()
                                                return@setAnalyzer
                                            }
                                            val mediaImage = imageProxy.image
                                            if (mediaImage != null) {
                                                val inputImage =
                                                    InputImage.fromMediaImage(
                                                        mediaImage,
                                                        imageProxy.imageInfo.rotationDegrees,
                                                    )
                                                scanner
                                                    .process(inputImage)
                                                    .addOnSuccessListener { barcodes ->
                                                        val qrCode =
                                                            barcodes.firstOrNull {
                                                                it.format == Barcode.FORMAT_QR_CODE
                                                            }
                                                        val value = qrCode?.rawValue
                                                        if (value != null && !hasScanned) {
                                                            hasScanned = true
                                                            onScanned(value)
                                                        }
                                                    }.addOnCompleteListener {
                                                        imageProxy.close()
                                                    }
                                            } else {
                                                imageProxy.close()
                                            }
                                        }
                                    }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis,
                            )
                        } catch (e: Exception) {
                            onError(e.message ?: "Camera initialization failed")
                        }
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )

        ViewfinderOverlay(modifier = Modifier.fillMaxSize())

        Text(
            text = "Point at a QR code",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = OVERLAY_BOTTOM_PADDING)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * Semi-transparent overlay with a centered viewfinder cutout.
 *
 * @param modifier Modifier applied to the canvas.
 */
@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cutoutSize = size.minDimension * VIEWFINDER_RATIO
        val left = (size.width - cutoutSize) / 2f
        val top = (size.height - cutoutSize) / 2f

        drawRect(color = OVERLAY_COLOR)
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size =
                androidx.compose.ui.geometry
                    .Size(cutoutSize, cutoutSize),
            cornerRadius = CornerRadius(VIEWFINDER_CORNER_RADIUS),
            blendMode = BlendMode.Clear,
        )
    }
}

/**
 * Success content shown after a QR code is scanned.
 *
 * @param token The decoded token.
 * @param modifier Modifier applied to the layout.
 */
@Composable
private fun ScanSuccessContent(
    token: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.semantics {
                    contentDescription = "QR code scanned successfully"
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Text(
                text = "Token scanned",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(SPACING_SMALL))
            Text(
                text = token.take(TOKEN_PREVIEW_LENGTH) + if (token.length > TOKEN_PREVIEW_LENGTH) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Error content with retry option.
 *
 * @param message Error description.
 * @param onRetry Callback to retry camera initialization.
 * @param modifier Modifier applied to the layout.
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.semantics {
                    contentDescription = "Camera error: $message"
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Text(
                text = "Camera error",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(SPACING_SMALL))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SPACING_LARGE))
            FilledTonalButton(
                onClick = onRetry,
                modifier =
                    Modifier.semantics {
                        role = Role.Button
                        contentDescription = "Retry camera"
                    },
            ) {
                Text("Retry")
            }
        }
    }
}

/** Size of decorative icons (48dp). */
private val ICON_SIZE = 48.dp

/** Small spacing (8dp). */
private val SPACING_SMALL = 8.dp

/** Medium spacing (12dp). */
private val SPACING_MEDIUM = 12.dp

/** Large spacing (24dp). */
private val SPACING_LARGE = 24.dp

/** Bottom padding for the overlay hint text. */
private val OVERLAY_BOTTOM_PADDING = 64.dp

/** Viewfinder cutout as a ratio of the smaller screen dimension. */
private const val VIEWFINDER_RATIO = 0.65f

/** Corner radius for the viewfinder cutout in pixels. */
private const val VIEWFINDER_CORNER_RADIUS = 24f

/** Semi-transparent overlay color. */
private val OVERLAY_COLOR = Color(0x80000000)

/** Maximum number of characters to show in the token preview. */
private const val TOKEN_PREVIEW_LENGTH = 20
