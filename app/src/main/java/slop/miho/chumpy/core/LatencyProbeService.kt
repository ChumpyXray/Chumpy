package slop.miho.chumpy.core

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class LatencyProbeService : Service() {
    private val worker = ThreadPoolExecutor(
        TunnelClient.MAX_PARALLEL_DELAY_TESTS,
        TunnelClient.MAX_PARALLEL_DELAY_TESTS,
        20L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(TunnelClient.MAX_PARALLEL_DELAY_TESTS),
    ) { Thread(it, "latency-probe") }.apply { allowCoreThreadTimeOut(true) }

    private val binder = object : ILatencyProbe.Stub() {
        override fun measure(configJson: String?, testUrl: String?, result: IDelayResult?) {
            if (result == null) return
            try {
                worker.execute {
                    report(result, XrayCore.measureOutboundDelay(configJson.orEmpty(), testUrl.orEmpty()))
                }
            } catch (_: RejectedExecutionException) {
                report(result, -1)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        XrayCore.init(this, probeOnly = true)
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun report(result: IDelayResult, delay: Long) {
        try {
            result.onResult(delay)
        } catch (_: RemoteException) {
        }
    }
}
