package com.mapbox.hackathon.bro_drive_app.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.mapbox.geojson.Point
import com.mapbox.hackathon.bro_drive_app.PermissionsHelper
import com.mapbox.hackathon.bro_drive_app.R
import com.mapbox.hackathon.bro_drive_app.databinding.FragmentNavigationBinding
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.coroutine.awaitStyle
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class NavigationFragment : Fragment() {

    private val permissionsHelper = PermissionsHelper()

    private var _binding: FragmentNavigationBinding? = null

    private lateinit var navigationCamera: NavigationCamera
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private val navigationLocationProvider = NavigationLocationProvider()

    private val pixelDensity = Resources.getSystem().displayMetrics.density


    private val followingPadding: EdgeInsets by lazy {
        EdgeInsets(
            180.0 * pixelDensity,
            40.0 * pixelDensity,
            150.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }

    // route line
    private lateinit var routeLineAPI: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    val viewModel: NavigationViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNavigationBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mapView.gestures.addOnMapLongClickListener { point ->
            viewModel.mapLongClick(point)
            true
        }

        routeLineView = MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(this.requireContext())
                .build()
        )
        viewportDataSource = MapboxNavigationViewportDataSource(
            binding.mapView.mapboxMap
        )
        viewportDataSource.followingPadding = followingPadding
        navigationCamera = NavigationCamera(
            binding.mapView.mapboxMap,
            binding.mapView.camera,
            viewportDataSource
        )
        binding.mapView.camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )

        binding.mapView.location.apply {
            this.locationPuck = LocationPuck2D(
                bearingImage = ImageHolder.from(
                    R.drawable.mapbox_navigation_puck_icon
                )
            )
            setLocationProvider(navigationLocationProvider)
            puckBearingEnabled = true
            enabled = true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.location.collect {
                when (it) {
                    is LocationState.Known -> {
                        // update location puck's position on the map
                        navigationLocationProvider.changePosition(
                            location = it.value.enhancedLocation,
                            keyPoints = it.value.keyPoints,
                        )

                        // update camera position to account for new location
                        viewportDataSource.onLocationChanged(it.value.enhancedLocation)
                        viewportDataSource.evaluate()

                        if (navigationCamera.state == NavigationCameraState.IDLE) {
                            if (it.firstUpdate) {
                                navigationCamera.requestNavigationCameraToFollowing()
                            } else {
//                                navigationCamera.requestNavigationCameraToFollowing(
//                                    NavigationCameraTransitionOptions.Builder().maxDuration(0).build()
//                                )
                            }
                        }
                    }
                    LocationState.Unknown -> {
                    }
                }
            }
        }
        val manager = binding.mapView.annotations.createCircleAnnotationManager()


        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.broLocation.filterNotNull().collect {
                Log.d("bro-location", it.toString())
                manager.deleteAll()
                val options = CircleAnnotationOptions()
                    .withPoint(Point.fromLngLat(
                        /* longitude = */ it.location.longitude,
                        /* latitude = */ it.location.latitude
                    ))
                    .withCircleRadius(8.0)
                    .withCircleColor("#ee4e8b")
                    .withCircleStrokeWidth(2.0)
                    .withCircleStrokeColor("#ffffff")
                manager.create(options)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.routeLineRoutesUpdates.filterNotNull().collectLatest {
                val style = binding.mapView.mapboxMap.awaitStyle()
                routeLineView.renderRouteDrawData(style, it)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.routeLineProgressUpdates.filterNotNull().collectLatest {
                val style = binding.mapView.mapboxMap.awaitStyle()
                routeLineView.renderRouteLineUpdate(style, it)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val ready = permissionsHelper.checkAndRequestPermissions(
                this@NavigationFragment.requireActivity(),
                DEFAULT_PERMISSIONS
            )
            if (ready.results.all { it.isGranted }) {
                if (ActivityCompat.checkSelfPermission(
                        this@NavigationFragment.requireActivity(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    viewModel.tripSessionCouldBeStarted()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        val DEFAULT_PERMISSIONS = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            .apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
    }
}