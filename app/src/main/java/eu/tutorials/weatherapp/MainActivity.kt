package eu.tutorials.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import eu.tutorials.weatherapp.models.WeatherResponse
import eu.tutorials.weatherapp.network.WeatherService
import eu.tutorials.weatherapp.Constants
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create

// openWeather Link : https://openweathermap.org/api
class MainActivity: AppCompatActivity() {

    // fused location client variable which is further used to get the user's current location
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mProgressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // initialize the fused location variable
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!isLocationEnabled()) {
            Toast.makeText(this, "Your location settings are turned off... Please turn them on...", Toast.LENGTH_SHORT).show()
            // this will redirect user to settings from where they need to turn on location settings
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }
                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(this@MainActivity, "You have denied location permission... Please allow it as it is mandatory...", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                })
                .onSameThread()
                .check()
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double)  {
        if (Constants.isNetworkAvailable(this)) {
            Toast.makeText(this@MainActivity, "You have connected to the internet...", Toast.LENGTH_SHORT).show()
            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL).addConverterFactory(GsonConverterFactory.create()).build()
            val service: WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall: Call<WeatherResponse> = service.getWeather(latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID)
            showCustomProgressDialog()
            listCall.enqueue(object: Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response!!.isSuccessful) {
                        hideProgressDialog()
                        val weatherList: WeatherResponse? = response.body()
                        if (weatherList != null) {
                            setUpUI(weatherList)
                        }
                        Log.i("Response Result", "$weatherList")
                    } else {
                        val rc = response.code()
                        when (rc) {
                            400 -> {
                                Log.i("Error 400", "bad connection")
                            }
                            404 -> {
                                Log.i("Error 404", "not found")
                            }
                            else -> {
                                Log.i("Error", "generic error ")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.i("ERROR", t!!.message.toString())
                    hideProgressDialog()
                }

            })
        } else {
            Toast.makeText(this@MainActivity, "No internet connection is available...", Toast.LENGTH_SHORT).show()
        }
    }

    // function which is used to verify that the location or GPS is enable or not of the user's device.
    private fun isLocationEnabled(): Boolean {
        // provides access to the system location services.
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    // function used to show the alert dialog when the permissions are denied and need to allow it from settings app info.
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    // function to request the current location. Using the fused location provider client.
    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    // location callback object of fused location provider client where we will get the current location details
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            val latitude = mLastLocation?.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation?.longitude
            Log.i("Current Longitude", "$longitude")
            if (latitude != null && longitude != null) {
                getLocationWeatherDetails(latitude, longitude)
            }
        }
    }

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    private fun setUpUI(weatherList: WeatherResponse) {
        for (i in weatherList.weather.indices) {
            Log.i("Weather Name", weatherList.weather.toString())
//            tv_main.text = weatherList.weather[i].main
        }
    }
}