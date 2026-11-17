package slop.miho.chumpy.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun measureIsolatedLatency(context: Context, config: String, url: String): Long {
    val connected = CompletableDeferred<ILatencyProbe?>()
    val answer = CompletableDeferred<Long>()
    val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            connected.complete(ILatencyProbe.Stub.asInterface(binder))
        }

        override fun onServiceDisconnected(name: ComponentName) {
            connected.complete(null)
            answer.complete(-1L)
        }

        override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)

        override fun onNullBinding(name: ComponentName) = onServiceDisconnected(name)
    }
    val bound = try {
        context.bindService(Intent(context, LatencyProbeService::class.java), connection, Context.BIND_AUTO_CREATE)
    } catch (_: SecurityException) {
        false
    }
    if (!bound) {
        return -1L
    }
    return try {
        val probe = withTimeoutOrNull(8_000L) { connected.await() } ?: return -1L
        probe.measure(config, url, object : IDelayResult.Stub() {
            override fun onResult(delayMillis: Long) {
                answer.complete(delayMillis)
            }
        })
        withTimeoutOrNull(45_000L) { answer.await() } ?: -1L
    } catch (_: RemoteException) {
        -1L
    } finally {
        context.unbindService(connection)
    }
}
