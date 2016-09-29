package com.yinhao.locationmock;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.TabHost;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends AppCompatActivity implements
        GoogleMap.OnMarkerClickListener,
        OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        PlaceSelectionListener {

    private final int LOCATION_REQUESTCODE = 101;
    private final int ZOOM_RATE = 14;
    private final int MOCK_LOCATION_ACCU = 1;
    private final String LOCATION_PERMISSION_TYPE = Manifest.permission.ACCESS_FINE_LOCATION;

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Location mCurrentLocation;
    private MapFragment mMapFragment;
    private PlaceAutocompleteFragment mPlaceAutocompleteFragment;
    private LatLng mSelectedPlace;
    private LatLng mCurrentlatlng;
    private boolean mMockOn;
    private Marker mCurrentMarker;
    private Marker mSelectMarker;
    private LocationManager mLocationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMockOn = false;
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        TabHost mTabhost;
        mTabhost = (TabHost) findViewById(R.id.tabHost);
        mTabhost.setup();

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        //set place search
        mPlaceAutocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.autocomplete_fragment);
        mPlaceAutocompleteFragment.setOnPlaceSelectedListener(this);

        //Tab 1
        TabHost.TabSpec spec = mTabhost.newTabSpec("Map");
        spec.setContent(R.id.tab1);
        spec.setIndicator("Map");
        mTabhost.addTab(spec);

        //Tab 2
        spec = mTabhost.newTabSpec("Setting");
        spec.setContent(R.id.tab2);
        spec.setIndicator("Setting");
        mTabhost.addTab(spec);

        mMapFragment = MapFragment.newInstance();
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.add(R.id.tab1, mMapFragment);
        fragmentTransaction.commit();

        //toggle mock
        ToggleButton toggle = (ToggleButton) findViewById(R.id.MockToggle);
        assert toggle != null;
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    System.out.println("Turn on");
                    mMockOn = true;
                } else {
                    System.out.println("Turn off");
                    mMockOn = false;
                }
            }
        });

        //request location permission
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_REQUESTCODE);
            }
        }

        //permission already granted
        else {
            mGoogleApiClient.connect();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case LOCATION_REQUESTCODE: {

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, LOCATION_PERMISSION_TYPE) != PackageManager.PERMISSION_GRANTED) {
                        // In case permission not granted, impossible anyway
                        return;
                    }
                    mGoogleApiClient.connect();

                } else {
                    Toast.makeText(getApplicationContext(), "Location Permission Denied!", Toast.LENGTH_SHORT).show();
                    final Intent i = new Intent();
                    i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.addCategory(Intent.CATEGORY_DEFAULT);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                    i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    startActivity(i);
                }
                return;
            }

        }
    }


    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {

                //mock has turned on, set mock location
                if (mMockOn) {
                    //remove the original marker
                    if (mCurrentMarker != null) {
                        mCurrentMarker.remove();
                    }
                    SetMockLocation(latLng);
                    mCurrentMarker = mMap.addMarker(new MarkerOptions().position(latLng)
                            .title("Mocked Location").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                }
            }
        });

        if (mCurrentLocation != null) {
            if (mCurrentMarker != null) {
                mCurrentMarker.remove();

            }
            mCurrentlatlng =  new LatLng(mCurrentLocation.getLatitude(),mCurrentLocation.getLongitude());
            mCurrentMarker = mMap.addMarker(new MarkerOptions().position(mCurrentlatlng)
                    .title("Current Location").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

            //zoom in to street
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrentlatlng,ZOOM_RATE));
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        return false;
    }

    //query the last known location
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, LOCATION_PERMISSION_TYPE) != PackageManager.PERMISSION_GRANTED) {
            //in case permission is not granted, not possible
            return;
        }
        mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        mMapFragment.getMapAsync(this);

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onPlaceSelected(Place place) {
        //place selected
        mSelectedPlace = place.getLatLng();
        mMap.clear();

        if (mSelectedPlace != null) {

            if (mSelectMarker != null) {
                mSelectMarker.remove();
            }

            mSelectMarker = mMap.addMarker(new MarkerOptions().position(mSelectedPlace)
                    .title("Selected Location"));

            //zoom in to street
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mSelectedPlace, ZOOM_RATE));
        }
    }

    @Override
    public void onError(Status status) {

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void SetMockLocation(LatLng LocationLatLng) {

        if (mLocationManager != null) {

            //gps
            mLocationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            mLocationManager.addTestProvider (
                    LocationManager.GPS_PROVIDER,
                    "requiresNetwork" == "",
                    "requiresSatellite" == "",
                    "requiresCell" == "",
                    "hasMonetaryCost" == "",
                    "supportsAltitude" == "",
                    "supportsSpeed" == "",
                    "supportsBearing" == "",
                    Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
            );

            Location newLocation = new Location(LocationManager.GPS_PROVIDER);
            newLocation.setLatitude (LocationLatLng.latitude);
            newLocation.setLongitude(LocationLatLng.longitude);
            newLocation.setAccuracy(MOCK_LOCATION_ACCU);
            newLocation.setElapsedRealtimeNanos(System.nanoTime());
            newLocation.setTime(System.currentTimeMillis());

            mLocationManager.setTestProviderEnabled (
                    LocationManager.GPS_PROVIDER,
                    true
            );

            mLocationManager.setTestProviderStatus (
                    LocationManager.GPS_PROVIDER,
                    LocationProvider.AVAILABLE,
                    null,
                    System.currentTimeMillis()
            );

            mLocationManager.setTestProviderLocation (
                    LocationManager.GPS_PROVIDER,
                    newLocation
            );

        }
    }

}
