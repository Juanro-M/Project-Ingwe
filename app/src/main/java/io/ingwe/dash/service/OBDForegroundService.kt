package io.ingwe.dash.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * OBD polling foreground service.
 *
 * STUB — full implementation in M7.
 *
 * In M7, this service will:
 *   1. Acquire a partial WakeLock so the CPU doesn't sleep mid-poll.
 *   2. Post a persistent notification (required for foreground service).
 *   3. Hold the ELM driver and scheduler, keeping them alive when the
 *      head unit pushes the app to the background.
 *   4. Reconnect with exponential backoff if the BLE link drops.
 *   5. Expose a Binder / Broadcast interface so the UI can bind and
 *      receive live data without the service being killed.
 */
class OBDForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OBDForegroundService started (stub)")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "Ingwe/OBDService"
    }
}
