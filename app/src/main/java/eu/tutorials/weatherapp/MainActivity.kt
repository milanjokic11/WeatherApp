package eu.tutorials.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// openWeather Link : https://openweathermap.org/api
class MainActivity: AppCompatActivity() {

    // fused location client variable which is further used to get the user's current location
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mSharedPreferences: SharedPreferences

    private var mProgressDialog: Dialog? = null

    // A global variable for Current Latitude
    private var mLatitude: Double = 0.0
    // A global variable for Current Longitude
    private var mLongitude: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // initialize the fused location variable
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

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

    private fun getLocationWeatherDetails()  {
        if (Constants.isNetworkAvailable(this)) {
            Toast.makeText(this@MainActivity, "You have connected to the internet...", Toast.LENGTH_SHORT).show()
            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL).addConverterFactory(GsonConverterFactory.create()).build()
            val service: WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall: Call<WeatherResponse> = service.getWeather(mLatitude, mLongitude, Constants.METRIC_UNIT, Constants.APP_ID)
            showCustomProgressDialog()
            listCall.enqueue(object: Callback<WeatherResponse> {
                @RequiresApi(Build.VERSION_CODES.N)
                @SuppressLint("SetTextI18n")
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "response successful...", Toast.LENGTH_SHORT).show()
                        hideProgressDialog()
                        val weatherList: WeatherResponse? = response.body()
                        if (weatherList != null) {
                            setUpUI(weatherList)
                            Log.i("Response Result", "$weatherList")
                        } else {
                            Log.e("Response Error", "Response body is null")
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "response unsuccessful...", Toast.LENGTH_SHORT).show()
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
                    // Toast.makeText(this@MainActivity, "response unsuccessful...", Toast.LENGTH_SHORT).show()
                    Log.i("ERROR", t.message.toString())
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

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    // location callback object of fused location provider client where we will get the current location details
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            if (mLastLocation != null) {
                mLatitude = mLastLocation.latitude
                mLongitude = mLastLocation.longitude
                Log.i("Current Latitude", "$mLatitude")
                Log.i("Current Longitude", "$mLongitude")
                getLocationWeatherDetails()
            } else {
                Log.e("LocationCallback", "No location data received")
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

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setUpUI(weatherList: WeatherResponse) {
        for (i in weatherList.weather.indices) {
            Log.i("Weather Name", weatherList.weather.toString())
            // setting values to the screen based on data
            val tvMain = findViewById<TextView>(R.id.tv_main)
            tvMain.text = weatherList.weather[i].main
            val tvMainDescription = findViewById<TextView>(R.id.tv_main_description)
            tvMainDescription.text = weatherList.weather[i].description
            val tvTemp = findViewById<TextView>(R.id.tv_temp)
            tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
            // set min./max., wind speed and country
            val tvHumidity = findViewById<TextView>(R.id.tv_humidity)
            tvHumidity.text = weatherList.main.humidity.toString() + " %"
            val tvMin = findViewById<TextView>(R.id.tv_min)
            tvMin.text = weatherList.main.temp_min.toString() + " min."
            val tvMax = findViewById<TextView>(R.id.tv_max)
            tvMax.text = weatherList.main.temp_max.toString() + " max."
            val tvSpeed = findViewById<TextView>(R.id.tv_speed)
            tvSpeed.text = weatherList.wind.speed.toString()
            val tvName = findViewById<TextView>(R.id.tv_name)
            tvName.text = weatherList.name
            val tvCountry = findViewById<TextView>(R.id.tv_country)
            tvCountry.text = weatherList.sys.country
            // setting sunrise and sunset
            val tvSunriseTime = findViewById<TextView>(R.id.tv_sunrise_time)
            tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
            val tvSunsetTime = findViewById<TextView>(R.id.tv_sunset_time)
            tvSunsetTime.text = unixTime(weatherList.sys.sunset)

            val ivMain = findViewById<ImageView>(R.id.iv_main)
            when (weatherList.weather[i].icon) {
                "01d" -> ivMain.setImageResource(R.drawable.sunny)
                "02d" -> ivMain.setImageResource(R.drawable.cloud)
                "03d" -> ivMain.setImageResource(R.drawable.cloud)
                "04d" -> ivMain.setImageResource(R.drawable.cloud)
                "04n" -> ivMain.setImageResource(R.drawable.cloud)
                "10d" -> ivMain.setImageResource(R.drawable.rain)
                "11d" -> ivMain.setImageResource(R.drawable.storm)
                "13d" -> ivMain.setImageResource(R.drawable.snowflake)
                "01n" -> ivMain.setImageResource(R.drawable.cloud)
                "02n" -> ivMain.setImageResource(R.drawable.cloud)
                "03n" -> ivMain.setImageResource(R.drawable.cloud)
                "10n" -> ivMain.setImageResource(R.drawable.cloud)
                "11n" -> ivMain.setImageResource(R.drawable.rain)
                "13n" -> ivMain.setImageResource(R.drawable.snowflake)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.action_refresh -> {
                getLocationWeatherDetails()
                true
            } else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getUnit(value: String): String? {
        var v = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            v = "°F"
        }
        return v
    }

    private fun unixTime(time: Long): String? {
        val date = Date(time * 1000L)
        val sdf = SimpleDateFormat("hh:mm", Locale.US)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }





}