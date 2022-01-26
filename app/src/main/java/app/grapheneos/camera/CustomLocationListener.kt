package app.grapheneos.camera

import android.Manifest
import android.content.Context.LOCATION_SERVICE
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.MainActivity.Companion.camConfig

class CustomLocationListener(private val mActivity: MainActivity) : LocationListener {

    private val locationManager =
        mActivity.getSystemService(LOCATION_SERVICE) as LocationManager

    var lastKnownLocation: Location? = null
        private set

    // Used to request permission from the user
    private val locationPermissionLauncher = mActivity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (it) {
            start()
        } else {
            camConfig.requireLocation = false
        }
    }

    private val providerType =
        locationManager.getBestProvider(Criteria(), true) ?: LocationManager.GPS_PROVIDER

    var locationPermissionDialog: AlertDialog? = null

    fun stop() {
        locationManager.removeUpdates(this)
    }

    fun start() {

        if (ActivityCompat.checkSelfPermission(
                mActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            ||
            ActivityCompat.checkSelfPermission(
                mActivity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            if (lastKnownLocation == null) {
                lastKnownLocation = locationManager.getLastKnownLocation(providerType)

                if (lastKnownLocation == null) {

                    mActivity.showMessage(
                        "Fetching location... This might take a while"
                    )

                } else {

                    mActivity.showMessage(
                        "Fetched current location successfully!"
                    )

                }
            }

            locationManager.requestLocationUpdates(
                providerType,
                2000,
                10f,
                this
            )

            Log.i(TAG, "Requested for location updates")

        } else {
            Log.i(TAG, "Location permission not granted")

            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    mActivity, Manifest.permission.ACCESS_FINE_LOCATION
                ) || ActivityCompat.shouldShowRequestPermissionRationale(
                    mActivity, Manifest.permission.ACCESS_COARSE_LOCATION
                )
            ) {

                val builder =
                    AlertDialog.Builder(mActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                builder.setTitle(R.string.location_permission_dialog_title)
                builder.setMessage(R.string.location_permission_dialog_message)

                // Open the settings menu for the current app
                builder.setPositiveButton("Settings") { _: DialogInterface?, _: Int ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts(
                        "package",
                        mActivity.packageName, null
                    )
                    intent.data = uri
                    mActivity.startActivity(intent)
                }
                builder.setNegativeButton("Cancel", null)

                builder.setOnDismissListener {
                    if (ContextCompat.checkSelfPermission(
                            mActivity, Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        camConfig.requireLocation = false
                    }
                }

                locationPermissionDialog = builder.show()

            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        if (lastKnownLocation == null) {
            mActivity.showMessage(
                "Fetched current location successfully!"
            )
        }
        lastKnownLocation = location
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {
        mActivity.showMessage(
            "Please enable the location access in your device settings"
        )

        // Revert settings
        camConfig.requireLocation = false
    }

    companion object {
        private const val TAG = "LocationListener"
    }
}