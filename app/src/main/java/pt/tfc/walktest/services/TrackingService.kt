package pt.tfc.walktest.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import android.telephony.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.androiddevs.walktest.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.tfc.walktest.other.Constants.ACTION_PAUSE_SERVICE
import pt.tfc.walktest.other.Constants.ACTION_START_OR_RESUME_SERVICE
import pt.tfc.walktest.other.Constants.ACTION_STOP_SERVICE
import pt.tfc.walktest.other.Constants.FASTEST_LOCATION_INTERVAL
import pt.tfc.walktest.other.Constants.LOCATION_UPDATE_INTERVAL
import pt.tfc.walktest.other.Constants.NOTIFICATION_CHANNEL_ID
import pt.tfc.walktest.other.Constants.NOTIFICATION_CHANNEL_NAME
import pt.tfc.walktest.other.Constants.NOTIFICATION_ID
import pt.tfc.walktest.other.Constants.TIMER_UPDATE_INTERVAL
import pt.tfc.walktest.other.TrackingUtility
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

typealias Polyline = MutableList<LatLng>
typealias Polylines = MutableList<Polyline>
typealias Telephony = MutableList<String>

@AndroidEntryPoint
class TrackingService : LifecycleService() {

    var isFirstRun = true
    var serviceKilled = false

    lateinit var telephonyManager: TelephonyManager

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private val timeRunInSeconds = MutableLiveData<Long>()

    @Inject
    lateinit var baseNotificationBuilder: NotificationCompat.Builder

    lateinit var curNotificationBuilder: NotificationCompat.Builder

    companion object {
        val isTracking = MutableLiveData<Boolean>()
        val timeRunInMillis = MutableLiveData<Long>()
        val pathPoints = MutableLiveData<Polylines>()

        val telephonyHistory = MutableLiveData<Telephony>()
    }

    override fun onCreate() {
        super.onCreate()

        curNotificationBuilder = baseNotificationBuilder

        postInitialValues()

        fusedLocationProviderClient = FusedLocationProviderClient(this)
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        isTracking.observe(this, Observer {
            updateLocationTracking(it)
            updateTelephonyTracking(it)
            updateNotificationTrackingState(it)
        })
    }

    private fun postInitialValues() {
        isTracking.postValue(false)
        pathPoints.postValue(mutableListOf())
        timeRunInSeconds.postValue(0L)
        timeRunInMillis.postValue(0L)
        telephonyHistory.postValue(ArrayList())
    }

    // When service receives command from TrackingFragment...
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when(it.action) {
                ACTION_START_OR_RESUME_SERVICE -> {
                    if (isFirstRun) {
                        Timber.d("Started service")
                        startForegroundService()
                        isFirstRun = false
                    } else {
                        Timber.d("Resumed service")
                        startTimer()
                        //startForegroundService()
                    }
                }
                ACTION_PAUSE_SERVICE -> {
                    pauseService()
                    Timber.d("Started or resumed service")
                }
                ACTION_STOP_SERVICE -> {
                    Timber.d("Stopped service")
                    killService()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private var isTimerEnabled = false
    private var lapTime = 0L
    private var timeRun = 0L
    private var timeStarted = 0L
    private var lastSecondTimestamp = 0L

    private fun startTimer() {
        addEmptyPolyLine()
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnabled = true

        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value!!) {
                // Time difference between now and timeStarted
                lapTime = System.currentTimeMillis() - timeStarted
                // Post the new lapTime
                timeRunInMillis.postValue(timeRun + lapTime)

                if (timeRunInMillis.value!! >= lastSecondTimestamp + 1000L) {
                    timeRunInSeconds.postValue(timeRunInSeconds.value!! + 1)
                    lastSecondTimestamp += 1000L
                }
                delay(TIMER_UPDATE_INTERVAL)
            }
            timeRun += lapTime
        }

    }

    private fun startForegroundService() {
        startTimer()

        isTracking.postValue(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }

        startForeground(NOTIFICATION_ID, baseNotificationBuilder.build())

        timeRunInSeconds.observe(this, Observer {
            if (!serviceKilled) {
                val notification = curNotificationBuilder
                    .setContentText(TrackingUtility.getFormattedStopWatchTime(it * 1000L))
                notificationManager.notify(NOTIFICATION_ID, notification.build())
            }
        })
    }

    private fun pauseService() {
        isTracking.postValue(false)
        isTimerEnabled = false
    }

    private fun killService() {
        serviceKilled = true
        isFirstRun = true
        pauseService()
        postInitialValues()
        stopForeground(true)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationTracking(isTracking: Boolean) {
        if (isTracking) {
            if (TrackingUtility.hasLocationPermissions(this)) {
                val request = LocationRequest().apply {
                    interval = LOCATION_UPDATE_INTERVAL
                    fastestInterval = FASTEST_LOCATION_INTERVAL
                    priority = PRIORITY_HIGH_ACCURACY
                }
                fusedLocationProviderClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
        } else {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }

    private val locationCallback = object : LocationCallback() {
        @SuppressLint("NewApi")
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            if (isTracking.value!!) {
                result?.locations?.let { locations ->
                    for (location in locations) {
                        addPathPoint(location)
                        Timber.d("New Location: ${location.latitude}, ${location.longitude}")

                        var result = "${LocalDateTime.now().toString()},${updateTelephonyTracking(true)},${location.latitude},${location.longitude}"

                        //updateTelephonyTracking(true)
                        addTelephony(result)

                        Timber.d("Test ----> ${telephonyHistory.value}")
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotificationTrackingState(isTracking: Boolean) {
        val notificationActionText = if (isTracking) "Pause" else "Resume"
        val pendingIntent = if (isTracking) {
            val pauseIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_PAUSE_SERVICE
            }
            PendingIntent.getService(this, 1, pauseIntent, FLAG_UPDATE_CURRENT)
        } else {
            val resumeIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_START_OR_RESUME_SERVICE
            }
            PendingIntent.getService(this, 2, resumeIntent, FLAG_UPDATE_CURRENT)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        curNotificationBuilder.javaClass.getDeclaredField("mActions").apply {
            isAccessible = true
            set(curNotificationBuilder, ArrayList<NotificationCompat.Action>())
        }

        if (!serviceKilled) {
            curNotificationBuilder = baseNotificationBuilder
                .addAction(R.drawable.ic_pause_black_24dp, notificationActionText, pendingIntent)
            notificationManager.notify(NOTIFICATION_ID, curNotificationBuilder.build())
        }
    }

    //return String with Telephony values
    @SuppressLint("MissingPermission")
    private fun updateTelephonyTracking(isTracking: Boolean) : String {
        var result = ""
        if (isTracking) {
            if (TrackingUtility.hasTelephonePermissions(this)) {

                var operatorName = telephonyManager.networkOperatorName
                //Timber.d("Operator Name: $operatorName")
                result += "$operatorName,"

                var operatorId = telephonyManager.networkOperator
                //Timber.d("Operator ID: $operatorId")
                result += "$operatorId,"

                var networkType = when (telephonyManager.networkType) {

                    TelephonyManager.NETWORK_TYPE_GSM -> "GSM" // 2G
                    TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS" // 2G
                    TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE" // 2G

                    TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA" // 3G
                    TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS" // 3G
                    TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA" // 3G
                    TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP" // 3G
                    TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA" // 3G
                    TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA" // 3G
                    TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT" // 3G
                    TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD_SCDMA" // 3G
                    TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO_0" // 3G
                    TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A" // 3G
                    TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO_B" // 3G

                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE" // 4G
                    TelephonyManager.NETWORK_TYPE_EHRPD -> "EHRPD" // 4G

                    TelephonyManager.NETWORK_TYPE_NR -> "5G" // 5G

                    TelephonyManager.NETWORK_TYPE_IDEN -> "IDEN" // ???
                    TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN" // ???

                    else -> "UNKNOWN"
                }
                //Timber.d("Network type: $networkType")
                result += "$networkType,"

                var strength = 0
                var cid = 0
                var lac = 0
                val cellInfo: List<CellInfo> = telephonyManager.allCellInfo

                if (cellInfo != null) {
                    for (i in cellInfo.indices) {
                        if (cellInfo[i].isRegistered) {
                            when {
                                cellInfo[i] is CellInfoGsm -> { //2G
                                    strength = (cellInfo[i] as CellInfoGsm).cellSignalStrength.dbm
                                    cid = (cellInfo[i] as CellInfoGsm).cellIdentity.cid
                                    lac = (cellInfo[i] as CellInfoGsm).cellIdentity.lac
                                }
                                cellInfo[i] is CellInfoWcdma -> { //3G
                                    strength = (cellInfo[i] as CellInfoWcdma).cellSignalStrength.dbm
                                    cid = (cellInfo[i] as CellInfoWcdma).cellIdentity.cid
                                    lac = (cellInfo[i] as CellInfoWcdma).cellIdentity.lac
                                }
                                cellInfo[i] is CellInfoLte -> { //4G
                                    strength = (cellInfo[i] as CellInfoLte).cellSignalStrength.dbm
                                    cid = (cellInfo[i] as CellInfoLte).cellIdentity.ci
                                    lac = (cellInfo[i] as CellInfoLte).cellIdentity.tac
                                }
                            }
                        }
                    }
                }

                //Timber.d("CID: $cid")
                result += "$cid,"
                //Timber.d("LAC: $lac")
                result += "$lac,"
                //Timber.d("Signal strength: $strength")
                result += "$strength,"

                result += "${signalNrBars(networkType, strength)},"

                var callState = when (telephonyManager.callState) {
                    TelephonyManager.CALL_STATE_IDLE -> "IDLE"
                    TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
                    TelephonyManager.CALL_STATE_RINGING -> "RINGING"
                    else -> "UNKNOWN"
                }
                //Timber.d("Call state: $callState")
                result += callState

                Timber.d("updateTelephonyTracking: $result")
                //addTelephony(result)
            }
        }
        return result
    }

    private fun signalNrBars(networkType: String, signalStrength: Int): Int {
        if (networkType == "GSM" || networkType == "GPRS" || networkType == "EDGE" || networkType == "CDMA" || networkType == "UMTS" ||
            networkType == "HSPA" || networkType == "HSPAP" || networkType == "HSDPA" || networkType == "HSUPA" || networkType == "1xRTT" ||
            networkType == "TD_SCDMA" ||  networkType == "EVDO_0" || networkType == "EVDO_A" ||  networkType == "EVDO_B"
        ) {
            if (signalStrength >= -70) {
                return 4
            } else if (signalStrength <= -71 && signalStrength >= -85) {
                return 3
            } else if (signalStrength <= -86 && signalStrength >= -100) {
                return 2
            } else if (signalStrength <= -101) {
                return 1
            }
        }

        if (networkType == "LTE" || networkType == "EHRPD") {
            if (signalStrength >= -90) {
                return 4
            } else if (signalStrength <= -91 && signalStrength >= -105) {
                return 3
            } else if (signalStrength <= -106 && signalStrength >= -110) {
                return 2
            } else if (signalStrength <= -111) {
                return 1
            }
        }
        return -1
    }

    private fun addTelephony(string: String?) {
        string?.let {
            Timber.d("addTelephony: $string")
            telephonyHistory.value?.apply {
                add(string)
                telephonyHistory.postValue(this)
            }
        }
    }

    private fun addPathPoint(location: Location?) {
        location?.let {
            val pos = LatLng(location.latitude, location.longitude)
            pathPoints.value?.apply {
                last().add(pos)
                pathPoints.postValue(this)
            }
        }
    }

    private fun addEmptyPolyLine() = pathPoints.value?.apply {
        add(mutableListOf())
        pathPoints.postValue(this)
    } ?: pathPoints.postValue(mutableListOf(mutableListOf()))

}