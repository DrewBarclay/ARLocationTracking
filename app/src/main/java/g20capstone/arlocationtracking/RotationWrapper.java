package g20capstone.arlocationtracking;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;
import android.view.Display;
import android.view.Surface;

/**
 * Created by Drew on 3/22/2017.
 */

public class RotationWrapper implements SensorEventListener {
    private SensorManager mSensorManager;
    private Sensor mRotationSensor;
    private Display mDisplay;

    private final float[] mRotationMatrixRaw = new float[16];

    public RotationWrapper(SensorManager sensorManager, Display display) {
        mSensorManager = sensorManager;
        mDisplay = display;
        mRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void onPause() {
        mSensorManager.unregisterListener(this);
    }

    public void onResume() {
        mSensorManager.registerListener(this, mRotationSensor, 20000); //10ms
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] origMatrix = new float[16];
            float[] tempMatrix = new float[16];
            SensorManager.getRotationMatrixFromVector(origMatrix, event.values);

            //Remap based on device orientation.
            switch (mDisplay.getRotation()) {
                case Surface.ROTATION_0:
                    SensorManager.remapCoordinateSystem(origMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, tempMatrix);
                    break;
                case Surface.ROTATION_90:
                    SensorManager.remapCoordinateSystem(origMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, tempMatrix);
                    break;
                case Surface.ROTATION_180:
                    SensorManager.remapCoordinateSystem(origMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, tempMatrix);
                    break;
                case Surface.ROTATION_270:
                    SensorManager.remapCoordinateSystem(origMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, tempMatrix);
                    break;
            }

            Matrix.transposeM(mRotationMatrixRaw, 0, tempMatrix, 0);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public float[] getRotationMatrixRaw() {
        return mRotationMatrixRaw;
    }
}
