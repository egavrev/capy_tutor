package com.capytutor.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * RECORD_AUDIO is asked for only when the mic is first tapped, after a short Romanian
 * explanation (HANDOFF.md §4.4). Refused: the mic looks inactive and says "Permite microfonul".
 */
@Stable
class MicPermission(
    val granted: Boolean,
    /** Show the explanation card. */
    val explaining: Boolean,
    /** Asked and refused. */
    val refused: Boolean,
    /** Mic tapped: record if allowed, otherwise explain first. */
    val tap: () -> Unit,
    /** "Permite microfonul" on the explanation card. */
    val allow: () -> Unit,
    val dismiss: () -> Unit,
)

fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun rememberMicPermission(onGranted: () -> Unit): MicPermission {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ctx.hasMicPermission()) }
    var explaining by rememberSaveable { mutableStateOf(false) }
    var refused by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        refused = !ok
        explaining = false
        if (ok) onGranted()
    }
    // Allowed later in the phone's settings: pick it up when the app comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = ctx.hasMicPermission()
        if (granted) refused = false
    }
    return MicPermission(
        granted = granted,
        explaining = explaining,
        refused = refused,
        tap = { if (granted) onGranted() else explaining = true },
        allow = {
            val activity = ctx.findActivity()
            val blocked = refused && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            if (blocked) ctx.openAppSettings() else launcher.launch(Manifest.permission.RECORD_AUDIO)
        },
        dismiss = { explaining = false },
    )
}
