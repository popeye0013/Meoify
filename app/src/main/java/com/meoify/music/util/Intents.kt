package com.meoify.music.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Opens an http(s) link, showing a toast if no browser can handle it. */
fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No app found to open this link", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Starts a UPI payment. Google Pay, PhonePe, Paytm and friends all register for
 * the upi:// scheme, so the system picker lets the person choose. Returns false
 * when the device has no UPI app at all.
 */
fun Context.openUpiPayment(
    upiId: String,
    payeeName: String,
    note: String? = null,
    amount: String? = null,
): Boolean {
    val uri = Uri.parse("upi://pay").buildUpon()
        .appendQueryParameter("pa", upiId)
        .appendQueryParameter("pn", payeeName)
        .appendQueryParameter("cu", "INR")
        .apply {
            if (!note.isNullOrBlank()) appendQueryParameter("tn", note)
            if (!amount.isNullOrBlank()) appendQueryParameter("am", amount)
        }
        .build()

    val candidates = listOf(
        Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.nbu.paisa.user"),
        Intent(Intent.ACTION_VIEW, uri),
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            // try the next candidate
        } catch (e: SecurityException) {
            // try the next candidate
        }
    }
    return false
}

fun Context.copyText(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
}
