package com.fatihyolcu

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.setPadding
import java.io.BufferedReader
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1
    }

    private lateinit var wifiManager: WifiManager
    private lateinit var wifiReceiver: BroadcastReceiver
    private val wifiList = mutableListOf<ScanResult>()
    private lateinit var mainLayout: LinearLayout

    private var currentPasswordIndex = 1 // Şifre denemesi için başlangıç indeksi
    private var currentPasswordList: List<String> = listOf()
    private var currentPasswordTryIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createMainLayout())
        setupWifiManager()
        checkAndRequestPermissions()
    }

    private fun createMainLayout(): ScrollView {
        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(16)
        }

        scrollView.addView(mainLayout)
        return scrollView
    }

    private fun setupWifiManager() {
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    updateWifiList()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CHANGE_WIFI_STATE)
        if (permissions.all { ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            scanWifi()
        } else {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            scanWifi()
        } else {
            Toast.makeText(this, "WiFi taraması için izinler gereklidir.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateWifiList() {
        mainLayout.removeAllViews()
        wifiList.clear()
        wifiList.addAll(wifiManager.scanResults.filter { !it.SSID.isNullOrEmpty() }) // Boş SSID'leri filtrele

        wifiList.forEach { scanResult ->
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setPadding(8)
            }

            val wifiNameTextView = TextView(this).apply {
                text = scanResult.SSID
                textSize = 18f
            }
            itemLayout.addView(wifiNameTextView)

            val checkWifiButton = Button(this).apply {
                text = "WiFi Check"
                setOnClickListener { showWifiDetails(scanResult) }
            }
            itemLayout.addView(checkWifiButton)

            mainLayout.addView(itemLayout)
        }
    }

    private fun showWifiDetails(scanResult: ScanResult) {
        AlertDialog.Builder(this)
            .setTitle(scanResult.SSID)
            .setMessage("Bu WiFi ağına bağlanmak istiyor musunuz?")
            .setPositiveButton("Evet") { dialog, _ ->
                dialog.dismiss()
                Handler(Looper.getMainLooper()).postDelayed({
                    tryConnectToWifi(scanResult)
                }, 100) // 1 saniye bekle
            }
            .setNegativeButton("Hayır", null)
            .show()
    }

    private fun connectToWifi(scanResult: ScanResult, password: String) {
        val alertDialog = AlertDialog.Builder(this).create()
        alertDialog.setTitle("Bağlantı Deneniyor")
        alertDialog.setMessage("Şifre: $password ile bağlantı deneniyor...")
        alertDialog.setButton(
            AlertDialog.BUTTON_NEGATIVE,
            "İptal"
        ) { dialog, _ ->
            dialog.dismiss()
        }
        alertDialog.show()

        Handler(Looper.getMainLooper()).postDelayed({
            alertDialog.dismiss()
            val isConnected = wifiManager.connectionInfo.ssid == "\"${scanResult.SSID}\""
            if (isConnected) {
                Toast.makeText(this, "${scanResult.SSID} ağına başarıyla bağlandınız!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "${scanResult.SSID} ağına bağlantı başarısız oldu.", Toast.LENGTH_SHORT).show()

                // Bağlantı başarılı olmadıysa, bir sonraki şifreyi dene
                currentPasswordTryIndex++
                if (currentPasswordTryIndex < currentPasswordList.size) {
                    val nextPassword = currentPasswordList[currentPasswordTryIndex]
                    connectToWifi(scanResult, nextPassword)
                }
                else {
                    // Şifreler tükendiğinde veya başka bir durumda işlem yapabilirsiniz
                    tryConnectToWifi(scanResult)
                    Toast.makeText(this, "Şifreler tükendi veya bağlantı sağlanamadı.", Toast.LENGTH_SHORT).show()
                    currentPasswordIndex = 1 // Şifre indeksini sıfırla veya bir sonraki dosyaya geç
                }
            }
        }, 1000) // 1 saniye bekle
    }

    private fun tryConnectToWifi(scanResult: ScanResult) {
        if (currentPasswordTryIndex >= currentPasswordList.size) {
            currentPasswordList = readPasswordsFromAssets(currentPasswordIndex)
            currentPasswordTryIndex = 0
            currentPasswordIndex++ // Bir sonraki dosyaya geç
        }

        if (currentPasswordList.isNotEmpty() && currentPasswordTryIndex < currentPasswordList.size) {
            val password = currentPasswordList[currentPasswordTryIndex]
            connectToWifi(scanResult, password)
            currentPasswordTryIndex++

            // Deneme yüzdesini hesapla ve göster
            val percentage = (currentPasswordTryIndex.toFloat() / currentPasswordList.size.toFloat()) * 100
            Toast.makeText(this, "Denenen şifre: $currentPasswordTryIndex / ${currentPasswordList.size} (${String.format("%.2f", percentage)}%)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Şifreler tükendi.", Toast.LENGTH_SHORT).show()
            currentPasswordIndex = 1 // Şifre indeksini sıfırla
        }
    }

    private fun readPasswordsFromAssets(index: Int): List<String> {
        val filename = "$index.txt"
        return try {
            assets.open(filename).bufferedReader().use { reader ->
                reader.lineSequence().toList()
            }
        } catch (e: IOException) {
            e.printStackTrace() // Hata durumunda log'a yaz
            emptyList() // Dosya bulunamadığında boş liste döndür
        }
    }

    private fun scanWifi() {
        registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        wifiManager.startScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wifiReceiver)
    }
}
