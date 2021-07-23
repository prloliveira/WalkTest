package pt.tfc.walktest.ui.fragments

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.androiddevs.walktest.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_history.*
import pt.tfc.walktest.adapters.RunAdapter
import pt.tfc.walktest.other.Constants.REQUEST_CODE_LOCATION_PERMISSION
import pt.tfc.walktest.other.SortType
import pt.tfc.walktest.other.TrackingUtility
import pt.tfc.walktest.ui.viewmodels.MainViewModel
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions

@AndroidEntryPoint
class HistoryFragment : Fragment(R.layout.fragment_history), EasyPermissions.PermissionCallbacks {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var runAdapter: RunAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestPermissions()
        setupRecyclerView()

        when(viewModel.sortType) {
            SortType.DATE -> sessionsFilterMenu.setSelection(0)
            SortType.RUNNING_TIME -> sessionsFilterMenu.setSelection(1)
            SortType.DISTANCE -> sessionsFilterMenu.setSelection(2)
            //SortType.AVG_SPEED -> spFilter.setSelection(3)
            //SortType.CALORIES_BURNED -> spFilter.setSelection(4)
        }

        sessionsFilterMenu.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {}

            override fun onItemSelected(
                adapterView: AdapterView<*>?,
                view: View?,
                pos: Int,
                id: Long
            ) {
                when (pos) {
                    0 -> viewModel.sortRuns(SortType.DATE)
                    1 -> viewModel.sortRuns(SortType.RUNNING_TIME)
                    2 -> viewModel.sortRuns(SortType.DISTANCE)
                    //3 -> viewModel.sortRuns(SortType.AVG_SPEED)
                    //4 -> viewModel.sortRuns(SortType.CALORIES_BURNED)
                }
            }
        }

        viewModel.sessions.observe(viewLifecycleOwner, Observer {
            runAdapter.submitList(it)
        })

        newSessionButton.setOnClickListener {
            findNavController().navigate(R.id.action_historyFragment_to_sessionFragment)
        }

    }

    private fun setupRecyclerView() = sessionsList.apply {
        runAdapter = RunAdapter(viewModel)
        adapter = runAdapter
        layoutManager = LinearLayoutManager(requireContext())
    }

    private fun requestPermissions() {
        if (TrackingUtility.hasLocationPermissions(requireContext()) && TrackingUtility.hasTelephonePermissions(
                requireContext())) {
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            EasyPermissions.requestPermissions(
                this,
                "You need to accept location permissions to use this app.",
                REQUEST_CODE_LOCATION_PERMISSION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
            )
        } else {
            EasyPermissions.requestPermissions(
                this,
                "You need to accept location permissions to use this app.",
                REQUEST_CODE_LOCATION_PERMISSION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.READ_PHONE_STATE
            )
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        } else {
            requestPermissions()
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

}