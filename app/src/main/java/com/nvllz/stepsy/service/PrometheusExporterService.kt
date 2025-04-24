/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.preference.PreferenceManager
import io.prometheus.client.CollectorRegistry
import io.prometheus.metrics.core.metrics.Gauge
import io.prometheus.metrics.exporter.pushgateway.PushGateway
import io.prometheus.metrics.exporter.pushgateway.PushGatewayBuilder
import io.prometheus.metrics.exporter.pushgateway.Scheme
import java.io.IOException

class PrometheusExporterService : Service() {
    private val jobName = "stepsy_steps"
    private val registry = CollectorRegistry.defaultRegistry
    private val liveStepsGauge = Gauge.builder()
        .name("steps")
        .help("Live step count")
        .labelNames("device")
        .register(registry)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val pushGatewayUrl = prefs.getString("prometheus_push_url", "http://your-prometheus-pushgateway-url:9091") ?: "http://your-prometheus-pushgateway-url:9091"
        val username = prefs.getString("prometheus_auth_username", "")
        val password = prefs.getString("prometheus_auth_password", "")
        val bearerToken = prefs.getString("prometheus_bearer_token", "")
        val useSsl = prefs.getBoolean("prometheus_use_ssl", false)

        val pushGatewayBuilder = PushGatewayBuilder()
            .url(pushGatewayUrl)
            .job(jobName)

        if (useSsl) {
            pushGatewayBuilder.scheme(Scheme.HTTPS)
        }

        if (!bearerToken.isNullOrEmpty()) {
            pushGatewayBuilder.bearerToken(bearerToken)
        } else if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            pushGatewayBuilder.basicAuth(username, password)
        }

        val pushGateway = pushGatewayBuilder.build()

        val dailySteps = intent?.getIntExtra(KEY_DAILY_STEPS, 0) ?: 0
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        pushSteps(dailySteps, pushGateway, deviceName)

        return START_NOT_STICKY
    }

    private fun pushSteps(liveSteps: Int, pushGateway: PushGateway, deviceName: String) {
        liveStepsGauge.labelValue("device", deviceName).set(liveSteps.toDouble())
        try {
            pushGateway.pushAdd(registry, "$jobName_live")
            Log.d(TAG, "Successfully pushed live steps to Prometheus Pushgateway with device name: $deviceName")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to push live steps to Prometheus Pushgateway", e)
        }
    }

    companion object {
        private const val TAG = "PrometheusExporterService"
        const val KEY_DAILY_STEPS = "KEY_DAILY_STEPS"
        const val KEY_IS_LIVE = "KEY_IS_LIVE"
    }
}
