/*  Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
*/

package com.hqas.ridetracker;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.getpebble.android.kit.PebbleKit;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import android.view.View.OnClickListener;

/**
 * Created by joneby on 11/14/2013.
 */
public class RideTrackerFragment extends Fragment {

    private Resources res;
    private GoogleMap map;
    private LocationManager locMan;
    private TextView pebbleStatus;
    private TrackerService tService;
    private LocalBroadcastManager broadcastManager;
    private Button startStop;

    public RideTrackerFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tService = ((MainActivity)getActivity()).getTrackerService();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        if (res == null) {
            res = getActivity().getResources();
        }

        pebbleStatus = (TextView) rootView.findViewById(R.id.pebble_connection_status);
        Typeface iconFont = Typeface.createFromAsset(res.getAssets(), "fonts/Android-Dev-Icons-1.ttf");
        pebbleStatus.setTypeface(iconFont);

        MapFragment mapFrag = (MapFragment) getFragmentManager().findFragmentById(R.id.map_container);
        map = mapFrag.getMap();
        if (map != null) {
            map.setMyLocationEnabled(true);
        }

        startStop = (Button)rootView.findViewById(R.id.startStopButton);

        startStop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tService.getStarted()) {
                    tService.setStopped();
                    startStop.setText(res.getString(R.string.start));
                } else {
                    tService.setStarted();
                    startStop.setText(res.getString(R.string.stop));
                }
            }
        });

        rootView.findViewById(R.id.clearButton).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        rootView.findViewById(R.id.shareButton).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        locMan = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        Criteria crit = new Criteria();
        crit.setAccuracy(Criteria.ACCURACY_FINE);
        locMan.requestLocationUpdates(0L, 0.0f, crit, tService, null);
        map.setLocationSource(tService);
        broadcastManager = LocalBroadcastManager.getInstance(getActivity());
        broadcastManager.registerReceiver(mapUpdateReceiver,
                new IntentFilter(TrackerService.ACTION_MAP_UPDATE_LOCATION));
        broadcastManager.registerReceiver(startStopReceiver,
                new IntentFilter(TrackerService.ACTION_START_STOP_RECEIVED));
        broadcastManager.registerReceiver(pebbleConnectedReceiver,
                new IntentFilter(TrackerService.ACTION_PEBBLE_CONNECTED));
        broadcastManager.registerReceiver(pebbleConnectedReceiver,
                new IntentFilter(TrackerService.ACTION_PEBBLE_DISCONNECTED));

        if (PebbleKit.isWatchConnected(getActivity())) {
            PebbleKit.startAppOnPebble(getActivity(), MainActivity.PEBBLE_APP_UUID);
            pebbleConnected();
        } else {
            pebbleStatus.setText(res.getString(R.string.pebble_status_disconnected));
            pebbleDisconnected();
        }

    }

    @Override
    public void onPause() {
        map.setLocationSource(null);
        broadcastManager.unregisterReceiver(mapUpdateReceiver);
        broadcastManager.unregisterReceiver(startStopReceiver);
        broadcastManager.unregisterReceiver(pebbleConnectedReceiver);
        super.onPause();
    }

    public void pebbleConnected() {
        pebbleStatus.setText(res.getString(R.string.pebble_status_connected));
        pebbleStatus.setTextColor(res.getColor(android.R.color.holo_green_light));
    }

    public void pebbleDisconnected() {
        pebbleStatus.setText(res.getString(R.string.pebble_status_disconnected));
        pebbleStatus.setTextColor(res.getColor(android.R.color.holo_red_light));
    }

    private BroadcastReceiver mapUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Location loc = intent.getParcelableExtra(TrackerService.KEY_LOCATION);
            float zoom = map.getCameraPosition().zoom == 2.0f ? 15.0f : map.getCameraPosition().zoom;
            if (loc != null) {
                CameraPosition myPosition = new CameraPosition.Builder()
                    .target(new LatLng(loc.getLatitude(), loc.getLongitude()))
                    .zoom(zoom).build();
                map.animateCamera(CameraUpdateFactory.newCameraPosition(myPosition));
            }
        }
    };

    private BroadcastReceiver startStopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean start = intent.getBooleanExtra(TrackerService.KEY_START_STOP, false);
            if ((start) && (startStop != null)) {
                startStop.setText(res.getString(R.string.stop));
            } else if ((!start) && (startStop != null)) {
                startStop.setText(res.getString(R.string.start));
            }
        }
    };

    private BroadcastReceiver pebbleConnectedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isAdded()) {
                boolean connected = intent.getBooleanExtra(TrackerService.KEY_PEBBLE_STATUS, false);
                if (connected) {
                    pebbleConnected();
                } else {
                    pebbleDisconnected();
                }
            }
        }
    };
}
