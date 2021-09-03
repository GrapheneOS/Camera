package org.grapheneos.camera.notifier;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import org.grapheneos.camera.ui.MainActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class SensorOrientationChangeNotifier {

    private final ArrayList<WeakReference<Listener>> mListeners =
            new ArrayList<>(3);

    private int mOrientation = 0;
    private final SensorEventListener mSensorEventListener;
    private final SensorManager mSensorManager;

    private static SensorOrientationChangeNotifier mInstance;

    public static SensorOrientationChangeNotifier
        getInstance(final MainActivity mActivity) {

        if (mInstance == null)
            mInstance = new SensorOrientationChangeNotifier(mActivity);

        return mInstance;
    }

    private SensorOrientationChangeNotifier(final MainActivity mainActivity) {
        mSensorEventListener = new NotifierSensorEventListener();
        mSensorManager = (SensorManager) mainActivity.getSystemService(Context.SENSOR_SERVICE);

    }

    /**
     * Call on activity reset()
     */
    private void onResume() {
        mSensorManager.registerListener(mSensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }

    /**
     * Call on activity onPause()
     */
    private void onPause() {
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    private class NotifierSensorEventListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {
            float x = event.values[0];
            float y = event.values[1];
            int newOrientation = mOrientation;
            if (x < 5 && x > -5 && y > 5)
                newOrientation = 0;
            else if (x < -5 && y < 5 && y > -5)
                newOrientation = 90;
            else if (x < 5 && x > -5 && y < -5)
                newOrientation = 180;
            else if (x > 5 && y < 5 && y > -5)
                newOrientation = 270;

            //Log.e(TAG,"mOrientation="+mOrientation+"   ["+event.values[0]+","+event.values[1]+","+event.values[2]+"]");
            if (mOrientation != newOrientation){
                mOrientation = newOrientation;
                notifyListeners();
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

    }

    public interface Listener {
        void onOrientationChange(int orientation);
    }

    public void addListener(SensorOrientationChangeNotifier.Listener listener) {
        if (get(listener) == null) // prevent duplications
            mListeners.add(new WeakReference<>(listener));

        if (mListeners.size() == 1) {
            onResume(); // this is the first client
        }
    }

    public void remove(SensorOrientationChangeNotifier.Listener listener) {
        WeakReference<SensorOrientationChangeNotifier.Listener> listenerWR = get(listener);
        remove(listenerWR);
    }

    private void remove(WeakReference<SensorOrientationChangeNotifier.Listener> listenerWR) {
        if (listenerWR != null)
            mListeners.remove(listenerWR);

        if (mListeners.size() == 0) {
            onPause();
        }

    }

    private WeakReference<SensorOrientationChangeNotifier.Listener> get(SensorOrientationChangeNotifier.Listener listener) {
        for (WeakReference<SensorOrientationChangeNotifier.Listener> existingListener : mListeners)
            if (existingListener.get() == listener)
                return existingListener;
        return null;
    }

    private void notifyListeners() {
        ArrayList<WeakReference<SensorOrientationChangeNotifier.Listener>> deadLinksArr = new ArrayList<>();
        for (WeakReference<SensorOrientationChangeNotifier.Listener> wr : mListeners) {
            if (wr.get() == null)
                deadLinksArr.add(wr);
            else
                wr.get().onOrientationChange(mOrientation);
        }

        // remove dead references
        for (WeakReference<SensorOrientationChangeNotifier.Listener> wr : deadLinksArr) {
            mListeners.remove(wr);
        }
    }
}