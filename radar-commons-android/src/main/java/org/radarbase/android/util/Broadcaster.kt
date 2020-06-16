package org.radarbase.android.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

fun LocalBroadcastManager.register(action: String, receiver: BroadcastReceiver, filter: (IntentFilter.() -> Unit)? = null) {
    val intentFilter = IntentFilter(action)
    filter?.let { intentFilter.apply(it) }
    registerReceiver(receiver, intentFilter)
}

fun LocalBroadcastManager.register(action: String, listener: (Context, Intent) -> Unit): BroadcastRegistration {
    val intentFilter = IntentFilter(action)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context != null && intent?.action == action) {
                listener(context, intent)
            }
        }
    }

    registerReceiver(receiver, intentFilter)
    return BroadcastRegistration(this, receiver)
}

fun LocalBroadcastManager.send(action: String, intent: (Intent.() -> Unit)? = null) {
    val broadcast = Intent(action)
    intent?.let { broadcast.apply(it) }
    sendBroadcast(broadcast)
}

data class BroadcastRegistration(private val broadcaster: LocalBroadcastManager,
                                 private var receiver: BroadcastReceiver?) {
    fun unregister() {
        receiver?.let { broadcaster.unregisterReceiver(it) }
        receiver = null
    }
}
