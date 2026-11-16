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
 * Starts a UPI payment with the system chooser. Google Pay, PhonePe, Paytm and
 * friends all register for the upi:// scheme, so the chooser lets the person
 * pick whichever app they actually use. Returns false when no UPI app can
 * handle the request (the caller then offers the UPI ID to copy).
 *
 * Notes on the parameters: `tr` is a unique transaction reference and
 * `mode=02` marks an intent payment; a few apps (notably Paytm) reject
 * requests that omit them.
 */
fun Context.openUpiPayment(
    upiId: String,
    payeeName: String,
    note: String? = null,
    amount: String? = null,
): Boolean {
    val transactionRef = "MEO" + System.currentTimeMillis()
    val uri = Uri.parse("upi://pay").buildUpon()
        .appendQueryParameter("pa", upiId)
        .appendQueryParameter("pn", payeeName)
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tr", transactionRef)
        .appendQueryParameter("mode", "02")
        .apply {
            if (!note.isNullOrBlank()) appendQueryParameter("tn", note)
            if (!amount.isNullOrBlank()) appendQueryParameter("am", amount)
        }
        .build()

    val payIntent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // With the <queries> entry in the manifest this sees every installed UPI
    // app, so devices without one get the copy fallback instead of an empty
    // chooser.
    if (packageManager.queryIntentActivities(payIntent, 0).isEmpty()) return false
    val chooser = Intent.createChooser(payIntent, "Pay with UPI").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        startActivity(chooser)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }
}

fun Context.copyText(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
}
