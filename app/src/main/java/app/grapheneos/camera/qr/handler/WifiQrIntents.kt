package app.grapheneos.camera.qr.handler

import android.content.Intent
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import app.grapheneos.camera.qr.data.Wifi
import app.grapheneos.camera.qr.data.WifiSecurityType

@RequiresApi(Build.VERSION_CODES.R)
fun convertWifiQrDataToIntent(wifi: Wifi): Intent {

    return Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
        putExtra(
            Settings.EXTRA_WIFI_NETWORK_LIST,
            arrayListOf(convertWifiQrDataToWifiNetworkSuggestion(wifi))
        )
    }
}

fun convertWifiQrDataToWifiNetworkSuggestion(wifi: Wifi): WifiNetworkSuggestion {

    val builder = WifiNetworkSuggestion.Builder()
        .setIsHiddenSsid(wifi.isHidden)
        .setSsid(wifi.ssid)

    return when (wifi.securityType) {
        WifiSecurityType.Open -> builder.build()

        WifiSecurityType.WPA, WifiSecurityType.WPA2 ->
            builder.setWpa2Passphrase(wifi.sharedKey)
                .build()

        WifiSecurityType.WPA3 -> builder.setWpa3Passphrase(wifi.sharedKey).build()
    }

}
