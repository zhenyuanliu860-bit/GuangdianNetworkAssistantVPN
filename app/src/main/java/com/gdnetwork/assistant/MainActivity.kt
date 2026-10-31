package com.gdnetwork.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private val bg = Color.rgb(8, 15, 30)
    private val panel = Color.rgb(17, 29, 51)
    private val cyan = Color.rgb(47, 210, 230)
    private val permissionRequest = 701

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
            text = "5G 网络体检 · DNS 检测 · 多目标延迟诊断"
            textSize = 13f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(18, 18, 18, 18)
            setBackgroundColor(panel)
        }
        resultText = TextView(this).apply {
            text = "准备就绪。点击“一键网络体检”开始检测。"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(18, 18, 18, 18)
            setBackgroundColor(panel)
        }
        content.addView(title)
        content.addView(subtitle)
        content.addView(section("当前网络"))
        content.addView(statusText)
        content.addView(section("网络体检报告"))
        content.addView(resultText)
        content.addView(actionButton("刷新网络状态") { refreshStatus() })
        content.addView(actionButton("一键网络体检") { runDiagnostics() })
        content.addView(actionButton("读取蜂窝信号信息") { readCellSignal() })
        content.addView(actionButton("打开移动网络设置") {
            try {
                startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
            } catch (_: Exception) {
                try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                catch (_: Exception) { resultText.text = "无法打开系统网络设置。" }
            }
        })
        val note = TextView(this).apply {
            text = "说明：检测结果用于定位连接问题，不代表运营商测速。普通应用不能突破限速、改变基站覆盖或强制切换 5G。蜂窝信号读取需要电话状态权限，部分设备或系统版本可能不提供数值。"
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
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val type = when {
            caps == null -> "无可用网络"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝移动网络（4G/5G 由系统决定）"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            else -> "其他网络"
        }
        val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val down = caps?.linkDownstreamBandwidthKbps ?: 0
        val up = caps?.linkUpstreamBandwidthKbps ?: 0
        statusText.text = "连接类型：" + type +
            "\n系统报告可访问互联网：" + if (internet) "是" else "否" +
            "\n系统验证互联网连通：" + if (validated) "是" else "否" +
            "\n系统估算下行带宽：" + if (down > 0) down.toString() + " Kbps" else "暂不可用" +
            "\n系统估算上行带宽：" + if (up > 0) up.toString() + " Kbps" else "暂不可用"
    }

    private fun readCellSignal() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), permissionRequest)
            resultText.text = "请允许电话状态权限，然后再次点击此按钮。"
            return
        }
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val values = tm.signalStrength?.cellSignalStrengths?.map { it.dbm }?.filter { it < 0 } ?: emptyList()
            resultText.text = if (values.isNotEmpty()) {
                "蜂窝信号读取成功\n信号强度（dBm）：" + values.joinToString(" / ") +
                    "\n数值越接近 0 通常代表信号越强；不同制式不宜直接比较。"
            } else {
                "系统没有提供可用的蜂窝信号数值。请确认正在使用移动数据，或尝试在室外重新读取。"
            }
        } catch (e: Exception) {
            resultText.text = "读取信号失败：" + e.javaClass.simpleName + "。设备系统可能限制了该信息。"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) {
            resultText.text = if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                "权限已允许，请再次点击“读取蜂窝信号信息”。"
            } else {
                "未获得电话状态权限，无法读取信号；网络体检仍可使用。"
            }
        }
    }

    private fun runDiagnostics() {
        resultText.text = "正在进行 DNS 与多目标 HTTPS 检测，请稍候…"
        thread {
            val report = StringBuilder()
            val hosts = listOf("www.baidu.com", "www.qq.com")
            var dnsOk = 0
            report.append("【DNS 解析】\n")
            for (host in hosts) {
                val start = System.currentTimeMillis()
                try {
                    val addresses = InetAddress.getAllByName(host)
                    dnsOk++
                    report.append("✓ " + host + "：解析成功，" +
                        (System.currentTimeMillis() - start) + " ms，" +
                        (addresses.firstOrNull()?.hostAddress ?: "已获得地址") + "\n")
                } catch (e: Exception) {
                    report.append("✗ " + host + "：解析失败（" + e.javaClass.simpleName + "）\n")
                }
            }

            val targets = listOf(
                "百度" to "https://www.baidu.com/",
                "腾讯" to "https://www.qq.com/",
                "中国广电" to "https://www.10099.com.cn/"
            )
            var httpOk = 0
            report.append("\n【HTTPS 连通性与响应时间】\n")
            for ((name, address) in targets) {
                val start = System.currentTimeMillis()
                var code = -1
                var error = ""
                var connection: HttpURLConnection? = null
                try {
                    connection = URL(address).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("Range", "bytes=0-0")
                    code = connection.responseCode
                    if (code in 200..499) httpOk++
                } catch (e: Exception) {
                    error = e.javaClass.simpleName
                } finally {
                    connection?.disconnect()
                }
                val elapsed = System.currentTimeMillis() - start
                if (code >= 0) report.append("✓ " + name + "：HTTP " + code + "，约 " + elapsed + " ms\n")
                else report.append("✗ " + name + "：连接失败，约 " + elapsed + " ms（" + error + "）\n")
            }

            report.append("\n【诊断结论】\nDNS 成功：" + dnsOk + "/" + hosts.size +
                "；HTTPS 可响应：" + httpOk + "/" + targets.size + "。\n")
            when {
                dnsOk == 0 -> report.append("建议：检查移动数据开关、飞行模式及当前网络 DNS；尝试重新连接网络。")
                httpOk == 0 -> report.append("DNS 可用但目标站点均无法连接。建议检查信号覆盖、系统代理/VPN、流量套餐及运营商网络状态。")
                dnsOk < hosts.size || httpOk < targets.size -> report.append("部分目标异常。单个网站故障不等于整个网络故障；可更换位置后重测，并对比 Wi-Fi 与移动数据。")
                else -> report.append("多个目标均可访问。若仍感觉网速慢，建议使用独立测速服务测量上下行速率。")
            }
            report.append("\n\n注意：单次 HTTP 响应时间不是 Ping，也不是上下行带宽测速。")
            runOnUiThread { resultText.text = report.toString() }
        }
    }
}