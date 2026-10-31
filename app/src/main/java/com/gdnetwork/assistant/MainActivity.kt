package com.gdnetwork.assistant

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private val bg = Color.rgb(8, 15, 30)
    private val panel = Color.rgb(17, 29, 51)
    private val cyan = Color.rgb(47, 210, 230)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshStatus()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 24)
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "广电网络优化助手"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "5G 网络检测 · 连接诊断 · 延迟测试"
            textSize = 13f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(18, 18, 18, 18)
            setBackgroundColor(panel)
        }
        resultText = TextView(this).apply {
            text = "准备就绪。点击下方按钮开始检测。"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(18, 18, 18, 18)
            setBackgroundColor(panel)
        }
        content.addView(title)
        content.addView(subtitle)
        content.addView(section("当前网络"))
        content.addView(statusText)
        content.addView(section("网络诊断"))
        content.addView(resultText)
        content.addView(actionButton("刷新网络状态") { refreshStatus() })
        content.addView(actionButton("开始连通性与延迟测试") { runDiagnostics() })
        content.addView(actionButton("打开系统网络设置") {
            try {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
            } catch (_: Exception) {
                resultText.text = "无法打开系统网络设置。"
            }
        })
        val note = TextView(this).apply {
            text = "说明：本工具负责检测和诊断，不会突破运营商限速或改变基站覆盖。信号详情受 Android 系统权限及设备开放能力限制。"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(8, 24, 8, 12)
        }
        content.addView(note)
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun section(label: String) = TextView(this).apply {
        text = label
        textSize = 17f
        setTextColor(cyan)
        setPadding(4, 22, 4, 10)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(21, 105, 145))
        setOnClickListener { action() }
        val p = LinearLayout.LayoutParams(-1, -2)
        p.setMargins(0, 14, 0, 0)
        layoutParams = p
    }

    private fun refreshStatus() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val type = when {
            caps == null -> "无可用网络"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝移动网络（可能为 4G/5G，系统未必开放具体制式）"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            else -> "其他网络"
        }
        val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        statusText.text = "连接类型：" + type +
            "\n系统报告可访问互联网：" + if (internet) "是" else "否" +
            "\n系统验证互联网连通：" + if (validated) "是" else "否"
    }

    private fun runDiagnostics() {
        resultText.text = "正在测试网络连通性与响应时间…"
        thread {
            val started = System.currentTimeMillis()
            var code = -1
            var error = ""
            try {
                val connection = URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 6000
                connection.readTimeout = 6000
                connection.instanceFollowRedirects = false
                code = connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {
                error = e.javaClass.simpleName + ": " + (e.message ?: "连接失败")
            }
            val elapsed = System.currentTimeMillis() - started
            val message = if (code in 200..399) {
                "测试完成\nHTTP 响应码：" + code + "\n往返耗时：约 " + elapsed +
                    " ms\n结论：测试服务器可连接。该数值是单次 HTTP 响应耗时，不等同于完整测速。"
            } else {
                "测试未成功\n耗时：" + elapsed + " ms\n" + error +
                    "\n建议：检查移动数据开关、飞行模式、APN 和当前信号覆盖。"
            }
            runOnUiThread { resultText.text = message }
        }
    }
}
