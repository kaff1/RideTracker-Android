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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;

import android.location.LocationListener;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.google.android.gms.maps.LocationSource;

/**
 * Created by joneby on 11/12/2013.
 */
public class TrackerService extends Service implements LocationListener,
        LocationSource {
    private static final String TAG = TrackerService.class.getSimpleName();

    public static final String ACTION_PEBBLE_CONNECTED = "pebble_connected";
    public static final String ACTION_PEBBLE_DISCONNECTED = "pebble_disconnected";
    public static final String ACTION_MAP_UPDATE_LOCATION = "map_update_location";
    public static final String ACTION_START_STOP_RECEIVED = "start_stop_received";

    public static final String KEY_LOCATION = "key_new_location";
    public static final String KEY_START_STOP = "key_start_stop";

    private float distanceTravelled;

    private OnLocationChangedListener mapLocationListener;
    private final float pebbleUpdateThreshold = 100.0f;
    private Location lastLoc;
    private boolean started;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PebbleKit.registerPebbleConnectedReceiver(this, pebbleConnectedReceiver);
        PebbleKit.registerPebbleDisconnectedReceiver(this, pebbleDisconnectedReceiver);
        PebbleKit.registerReceivedAckHandler(this, pebbleAckReceiver);
        PebbleKit.registerReceivedNackHandler(this, pebbleNackReceiver);
        PebbleKit.registerReceivedDataHandler(this, pebbleDataReceiver);

        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void activate(OnLocationChangedListener listener) {
        this.mapLocationListener = listener;
    }

    @Override
    public void deactivate() {
        this.mapLocationListener = null;
    }

    @Override
    public void onLocationChanged(Location loc) {
        if ((mapLocationListener != null) && (started)) {
            mapLocationListener.onLocationChanged(loc);
            Intent newLocation = new Intent(ACTION_MAP_UPDATE_LOCATION);
            newLocation.putExtra(KEY_LOCATION, loc);
            LocalBroadcastManager.getInstance(this).sendBroadcast(newLocation);
        }

        if (lastLoc == null) {
            lastLoc = loc;
        }

        float distanceFromLastPoint = loc.distanceTo(lastLoc);

        if ((distanceFromLastPoint >= pebbleUpdateThreshold) && (started)) {
            distanceTravelled += distanceFromLastPoint;
            lastLoc = loc;
            PebbleDictionary newLocData = new PebbleDictionary();
            newLocData.addInt32(0, (int) distanceTravelled);
            PebbleKit.sendDataToPebble(this, MainActivity.PEBBLE_APP_UUID, newLocData);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Nothing to do here at this point.
    }

    @Override
    public void onProviderEnabled(String provider) {
        // Nothing to do here at this point.
    }

    @Override
    public void onProviderDisabled(String provider) {
        // Nothing to do here at this point.
    }

    private BroadcastReceiver pebbleConnectedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent connected = new Intent(ACTION_PEBBLE_CONNECTED);
            LocalBroadcastManager.getInstance(context).sendBroadcast(connected);
        }
    };

    private BroadcastReceiver pebbleDisconnectedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent disconnected = new Intent(ACTION_PEBBLE_DISCONNECTED);
            LocalBroadcastManager.getInstance(context).sendBroadcast(disconnected);
        }
    };

    private PebbleKit.PebbleAckReceiver pebbleAckReceiver = new PebbleKit.PebbleAckReceiver(
            MainActivity.PEBBLE_APP_UUID) {
        @Override
        public void receiveAck(Context context, int i) {
            // We don't particularly care about an Ack, but log it anyway.
            Log.i(TAG, "Got Ack from Pebble");
        }
    };

    private PebbleKit.PebbleNackReceiver pebbleNackReceiver = new PebbleKit.PebbleNackReceiver(
            MainActivity.PEBBLE_APP_UUID) {
        @Override
        public void receiveNack(Context context, int i) {
            // Something bad is probably going on if we got a Nack, log it as a warning.
            Log.w(TAG, "Got Nack from Pebble");
        }
    };

    private PebbleKit.PebbleDataReceiver pebbleDataReceiver = new PebbleKit.PebbleDataReceiver(
            MainActivity.PEBBLE_APP_UUID) {
        @Override
        public void receiveData(Context context, int transactionId, PebbleDictionary pebbleTuples) {
            String messageString = pebbleTuples.getString(0);
            if ((messageString != null) && (messageString.equals("true"))) {
                started = true;
                broadcastStartStop(started);
            } else if ((messageString != null) && (messageString.equals("false"))) {
                started = false;
                broadcastStartStop(started);
            } else {
                Log.w(TAG, "Got unknown message from Pebble: " + messageString);
            }

            PebbleKit.sendAckToPebble(context, transactionId);
        }
    };

    private void broadcastStartStop(boolean started) {
        Intent startStopIntent = new Intent(ACTION_START_STOP_RECEIVED);
        startStopIntent.putExtra(KEY_START_STOP, started);
        LocalBroadcastManager.getInstance(this).sendBroadcast(startStopIntent);
    }

    public void setStarted() {
        //TODO: Send update to Pebble saying we're started (if there's a Pebble connected)
        started = true;
    }

    public void setStopped() {
        //TODO: Send update to Pebble saying we're stopped (if there's a Pebble connected)
        started = false;
    }

    public boolean getStarted() {
        return started;
    }
}
