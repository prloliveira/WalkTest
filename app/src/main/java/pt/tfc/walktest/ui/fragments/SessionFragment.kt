package pt.tfc.walktest.ui.fragments

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.androiddevs.walktest.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_session.*
import pt.tfc.walktest.db.Session
import pt.tfc.walktest.other.Constants.ACTION_PAUSE_SERVICE
import pt.tfc.walktest.other.Constants.ACTION_START_OR_RESUME_SERVICE
import pt.tfc.walktest.other.Constants.ACTION_STOP_SERVICE
import pt.tfc.walktest.other.Constants.MAP_ZOOM
import pt.tfc.walktest.other.Constants.POLYLINE_COLOR
import pt.tfc.walktest.other.Constants.POLYLINE_WIDTH
import pt.tfc.walktest.other.TrackingUtility
import pt.tfc.walktest.services.Polyline
import pt.tfc.walktest.services.TrackingService
import pt.tfc.walktest.ui.viewmodels.MainViewModel
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList

const val CANCEL_TRACKING_DIALOG_TAG = "CANCEL"

@AndroidEntryPoint
class TrackingFragment : Fragment(R.layout.fragment_session) {

    private val viewModel: MainViewModel by viewModels()
    private var menu: Menu? = null
    private var map: GoogleMap? = null
    private var isTracking = false
    private var telephonyHistory = mutableListOf<String>()
    private var pathPoints = mutableListOf<Polyline>()
    private var curTimeInMillis = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    // When Fragment is active
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView.onCreate(savedInstanceState)

        buttonStartSession.setOnClickListener {
            toogleRun()
        }

        if(savedInstanceState != null) {
            val cancelTrackingDialog = parentFragmentManager.findFragmentByTag(
                CANCEL_TRACKING_DIALOG_TAG) as CancelTrackingDialog?
            cancelTrackingDialog?.setYesListener {
                stopRun()
            }
        }

        val sessionEmptyBuilder: AlertDialog.Builder = AlertDialog.Builder(context)

        sessionEmptyBuilder
            .setTitle("Cancel this session?")
            .setMessage("Session has no data yet.")
            .setCancelable(false)
            .setPositiveButton("Yes"
            ) { _, _ -> stopRun() }
            .setNegativeButton("No"
            ) { dialog, _ -> dialog.cancel() }

        val sessionEmptyAlert: AlertDialog = sessionEmptyBuilder.create()

        buttonFinishSession.setOnClickListener {
            if (telephonyHistory.size >= 1) {
                zoomToSeeWholeTrack()
                endRunAndSaveToDb()
            } else {
                sessionEmptyAlert.show()
            }
        }

        mapView.getMapAsync {
            map = it
            addAllPolylines()
            addAllMarkers()
        }

        subscribeToObservers()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    // Toolbar
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.toolbar_tracking_menu, menu)
        this.menu = menu
    }

    // Show top right button ONLY when session begins
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        this.menu?.getItem(0)?.isVisible = true
        /*
        if(curTimeInMillis > 0L) {
            this.menu?.getItem(0)?.isVisible = true
        }
        */
    }

    // Top right button to end capture session
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.miCancelTracking -> {
                showCancelTrackingDialog()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // Cancel dialog to end capture session
    private fun showCancelTrackingDialog() {
        CancelTrackingDialog().apply {
            setYesListener {
                stopRun()
            }
        }.show(parentFragmentManager, null)
    }

    // Observers from TrackingService
    private fun subscribeToObservers() {
        TrackingService.isTracking.observe(viewLifecycleOwner, Observer {
            updateTracking(it)
        })

        TrackingService.telephonyHistory.observe(viewLifecycleOwner, Observer {
            telephonyHistory = it
            updateTelephonyView()
            addLatestMarker()
        })

        TrackingService.pathPoints.observe(viewLifecycleOwner, Observer {
            pathPoints = it
            addLatestPolyLine()
            moveCameraToUser()
        })

        TrackingService.timeRunInMillis.observe(viewLifecycleOwner, Observer {
            curTimeInMillis = it
            val formattedTime = TrackingUtility.getFormattedStopWatchTime(curTimeInMillis, true)
            sessionTimer.text = formattedTime
        })
    }

    // Checks if isTracking and commands service accordingly
    private fun toogleRun() {
        if (isTracking) {
            menu?.getItem(0)?.isVisible = true
            sendCommandToService(ACTION_PAUSE_SERVICE)
        } else {
            sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
        }
    }

    // Sends String to TrackingService
    private fun sendCommandToService(action: String) = Intent(requireContext(), TrackingService::class.java).also {
        it.action = action
        requireContext().startService(it)
    }

    // Updates UI buttons view and text accordingly isTracking
    @SuppressLint("SetTextI18n")
    private fun updateTracking(isTracking: Boolean) {
        this.isTracking = isTracking
        if(!isTracking && curTimeInMillis > 0L) {
            buttonStartSession.text = "Resume"
            buttonFinishSession.visibility = View.VISIBLE
        } else if(isTracking) {
            buttonStartSession.text = "Stop"
            menu?.getItem(0)?.isVisible = true
            buttonFinishSession.visibility = View.GONE
        }
    }

    // Updates UI Telephony info
    @SuppressLint("SetTextI18n")
    private fun updateTelephonyView() {
        if(isTracking && telephonyHistory.isNotEmpty()) {
            Timber.d("telephonyHistory (Fragment): $telephonyHistory")
            val data = telephonyHistory[telephonyHistory.size - 1].split(",")
            operatorName.text = data[1]
            operatorId.text = "Operator ID: " + data[2]
            networkType.text = data[3]
            cid.text = "CID: " + data[4]
            lac.text = "LAC: " + data[5]
            signalStrength.text = data[6] + " dBm"
            signalBarsColour(data[7].toInt())
            callState.text = data[8]
        }
    }

    // Takes snapshot from map session, saves session value to database and STOP session
    private fun endRunAndSaveToDb() {
        map?.snapshot { bmp ->
            var distanceInMeters = 0
            for (polyline in pathPoints) {
                distanceInMeters += TrackingUtility.calculatePolylineLength(polyline).toInt()
            }
            val dateTimestamp = Calendar.getInstance().timeInMillis

            val run = Session(bmp, dateTimestamp, distanceInMeters, curTimeInMillis, telephonyHistory as ArrayList<String>)
            viewModel.insertSession(run)
            /*
            Snackbar.make(
                requireActivity().findViewById(R.id.rootView),
                "Session saved successfully",
                Snackbar.LENGTH_LONG
            ).show()
            */
            stopRun()
        }
    }

    // Reset timer, STOP service and return to history fragment
    private fun stopRun() {
        sessionTimer.text = "00:00:00:00"
        sendCommandToService(ACTION_STOP_SERVICE)
        findNavController().navigate(R.id.action_sessionFragment_to_historyFragment)
    }

    // Changes bars color on UI depending on signal strength
    private fun signalBarsColour(value: Int) {
        if (value == 4) {
            signalBar01.background.setTint(Color.parseColor("#62FF19"))
            signalBar02.background.setTint(Color.parseColor("#62FF19"))
            signalBar03.background.setTint(Color.parseColor("#62FF19"))
            signalBar04.background.setTint(Color.parseColor("#62FF19"))

            //signalBar01.background.setTint(Color.parseColor("#E3F2FD"))
            //signalBar02.background.setTint(Color.parseColor("#E3F2FD"))
            //signalBar03.background.setTint(Color.parseColor("#E3F2FD"))
            //signalBar04.background.setTint(Color.parseColor("#E3F2FD"))
        }

        if (value == 3) {
            signalBar01.background.setTint(Color.parseColor("#F9F900"))
            signalBar02.background.setTint(Color.parseColor("#F9F900"))
            signalBar03.background.setTint(Color.parseColor("#F9F900"))
            //signalBar04.background.setTint(Color.parseColor("#F9F900"))

            //signalBar01.background.setTint(Color.parseColor("#E3F2FD"))
            //signalBar02.background.setTint(Color.parseColor("#E3F2FD"))
            //signalBar03.background.setTint(Color.parseColor("#E3F2FD"))
            signalBar04.background.setTint(Color.parseColor("#EAEAEA"))
        }

        if (value == 2) {
            signalBar01.background.setTint(Color.parseColor("#FF9D0F"))
            signalBar02.background.setTint(Color.parseColor("#FF9D0F"))
            //signalBar03.background.setTint(Color.parseColor("#FF9D0F"))
            //signalBar04.background.setTint(Color.parseColor("#FF9D0F"))

            //signalBar01.background.setTint(Color.parseColor("#E3F2FD"))
            //signalBar02.background.setTint(Color.parseColor("#E3F2FD"))
            signalBar03.background.setTint(Color.parseColor("#EAEAEA"))
            signalBar04.background.setTint(Color.parseColor("#EAEAEA"))
        }

        if (value == 1) {
            signalBar01.background.setTint(Color.parseColor("#FF0A2F"))
            //signalBar02.background.setTint(Color.parseColor("#FF0A2F"))
            //signalBar03.background.setTint(Color.parseColor("#FF0A2F"))
            //signalBar04.background.setTint(Color.parseColor("#FF0A2F"))

            //signalBar01.background.setTint(Color.parseColor("#E3F2FD"))
            signalBar02.background.setTint(Color.parseColor("#EAEAEA"))
            signalBar03.background.setTint(Color.parseColor("#EAEAEA"))
            signalBar04.background.setTint(Color.parseColor("#EAEAEA"))
        }

    }

    private fun moveCameraToUser() {
        if (pathPoints.isNotEmpty() && pathPoints.last().isNotEmpty()) {
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pathPoints.last().last(),
                    MAP_ZOOM
                )
            )
        }
    }

    private fun zoomToSeeWholeTrack() {
        val bounds = LatLngBounds.Builder()

        for (polyline in pathPoints) {
            for (pos in polyline) {
                bounds.include(pos)
            }
        }

        map?.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),
                mapView.width,
                mapView.height,
                (mapView.height * 0.05f).toInt()
            )
        )
    }

    private fun addLatestMarker() {
        if(isTracking && telephonyHistory.isNotEmpty()) {
            val data = telephonyHistory[telephonyHistory.size - 1].split(",")
            val perthLocation = LatLng(data[9].toDouble(), data[10].toDouble())

            when (data[7].toInt()) {
                4 -> map?.addMarker(
                    MarkerOptions()
                        .position(perthLocation) // Marker position
                        .title("${data[3]} | ${data[4]}") // Marker title
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)) // Marker color
                        .alpha(0.7f) // Marker opacity
                        //.flat(true) // ???
                )
                3 -> map?.addMarker(
                    MarkerOptions()
                        .position(perthLocation) // Marker position
                        .title("${data[3]} | ${data[4]}") // Marker title
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)) // Marker color
                        .alpha(0.7f) // Marker opacity
                        //.flat(true) // ???
                )
                2 -> map?.addMarker(
                    MarkerOptions()
                        .position(perthLocation) // Marker position
                        .title("${data[3]} | ${data[4]}") // Marker title
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)) // Marker color
                        .alpha(0.7f) // Marker opacity
                        //.flat(true) // ???
                )
                1 -> map?.addMarker(
                    MarkerOptions()
                        .position(perthLocation) // Marker position
                        .title("${data[3]} | ${data[4]}") // Marker title
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Marker color
                        .alpha(0.7f) // Marker opacity
                        //.flat(true) // ???
                )
                else -> map?.addMarker(
                    MarkerOptions()
                        .position(perthLocation) // Marker position
                        .title("${data[3]} | ${data[4]}") // Marker title
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)) // Marker color
                        .alpha(0.7f) // Marker opacity
                        //.flat(true) // ???
                )
            }
        }
    }

    private fun addAllMarkers() {
        for (capture in telephonyHistory) {
            val data = capture.split(",")
            val perthLocation = LatLng(capture[9].toDouble(), capture[10].toDouble())
            when (data[7].toInt()) {
                4 -> map?.addMarker(
                    MarkerOptions()
                        .position(perthLocation) // Marker position
                        .title("${data[3]} | ${data[4]}") // Marker title
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)) // Marker color
                        .alpha(0.7f) // Marker opacity
                        //.flat(true) // ???
                )
                3 -> map?.addMarker(
                    MarkerOptions()
                        .position(perthLocation) // Marker position
                        .title("${data[3]} | ${data[4]}") // Marker title
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)) // Marker color
                        .alpha(0.7f) // Marker opacity
                        //.flat(true) // ???
                )
                2 -> map?.addMarker(
                    MarkerOptions()
                        .position(perthLocation) // Marker position
                        .title("${data[3]} | ${data[4]}") // Marker title
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)) // Marker color
                        .alpha(0.7f) // Marker opacity
                        //.flat(true) // ???
                )
                1 -> map?.addMarker(
                    MarkerOptions()
                        .position(perthLocation) // Marker position
                        .title("${data[3]} | ${data[4]}") // Marker title
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Marker color
                        .alpha(0.7f) // Marker opacity
                        //.flat(true) // ???
                )
                else -> map?.addMarker(
                    MarkerOptions()
                        .position(perthLocation) // Marker position
                        .title("${data[3]} | ${data[4]}") // Marker title
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)) // Marker color
                        .alpha(0.7f) // Marker opacity
                        //.flat(true) // ???
                )
            }
        }
    }

    private fun addLatestPolyLine() {
        if (pathPoints.isNotEmpty() && pathPoints.last().size > 1) {
            val preLastLatLng = pathPoints.last()[pathPoints.last().size - 2]
            val lastLatLng = pathPoints.last().last()
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .add(preLastLatLng, lastLatLng)
            map?.addPolyline(polylineOptions)
        }
    }

    private fun addAllPolylines() {
        for (polyline in pathPoints) {
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .addAll(polyline)
            map?.addPolyline(polylineOptions)
        }
    }

}