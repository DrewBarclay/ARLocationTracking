package g20capstone.cameratest;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Drew on 2/16/2017.
 */

public class ARRenderer implements GLSurfaceView.Renderer, SensorEventListener {

    private Display mDisplay;

    private Cube mCube;

    // mMVPMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mModelMatrix = new float[16];

    private final float[] mRotationMatrix = new float[16];

    private SensorManager mSensorManager;
    private Sensor mRotationSensor;

    public ARRenderer(SensorManager sensorManager, Display display) {
        mDisplay = display;
        mSensorManager = sensorManager;
        mRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void onResume() {
        mSensorManager.registerListener(this, mRotationSensor, 10000); //10ms
    }

    public void onPause() {
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        mCube = new Cube();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 0, 0f, 0f, 3f, 0f, 1.0f, 0.0f);

        //Rotate based on orientation
        Matrix.multiplyMM(mViewMatrix, 0, mRotationMatrix, 0, mViewMatrix, 0);

        for (int i = 0; i < 2; i++) {
            // Calculate the projection and view transformation
            Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

            //Draw two cubes, one at (0, 0, 5), the other at (0, 0, -5)
            Matrix.setIdentityM(mModelMatrix, 0);
            Matrix.translateM(mModelMatrix, 0, 0, 0, 10.0f * i - 5.0f);
            Matrix.multiplyMM(mMVPMatrix, 0, mMVPMatrix, 0, mModelMatrix, 0);
            mCube.draw(mMVPMatrix);
        }
    }

    //Code copied from Google's sample code.
    public static int loadShader(int type, String shaderCode){
        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        int shader = GLES20.glCreateShader(type);

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        return shader;
    }

    //Copied from Google's sample code
    public static void checkGlError(String glOperation) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e("OpenGL", glOperation + ": glError " + error);
            throw new RuntimeException(glOperation + ": glError " + error);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] tempMatrix = new float[16];
            SensorManager.getRotationMatrixFromVector(tempMatrix, event.values);
            //Remap based on device orientation. Note: these values are the opposite of what you might expect because the rotation matrix above is inverted.
            switch (mDisplay.getRotation()) {
                case Surface.ROTATION_0:
                    SensorManager.remapCoordinateSystem(tempMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, mRotationMatrix);
                    break;
                case Surface.ROTATION_90:
                    SensorManager.remapCoordinateSystem(tempMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, mRotationMatrix);
                    break;
                case Surface.ROTATION_180:
                    SensorManager.remapCoordinateSystem(tempMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, mRotationMatrix);
                    break;
                case Surface.ROTATION_270:
                    SensorManager.remapCoordinateSystem(tempMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, mRotationMatrix);
                    break;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
