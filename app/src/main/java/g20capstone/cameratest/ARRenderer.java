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

import java.util.Arrays;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Drew on 2/16/2017.
 */

public class ARRenderer implements GLSurfaceView.Renderer, SensorEventListener {
    private TagParser mTagParser;

    private Display mDisplay;

    private PositionMarker mPositionMarker;

    // mMVPMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] mVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];

    private final float[] mRotationMatrix = new float[16];
    private final float[] mLookAtVector = new float[4];
    private final float[] mLookAtVector0 = new float[] {0, 0, -1, 1};
    private final float[] mUpVector = new float[4];
    private final float[] mUpVector0 = new float[] {0, 1, 0, 1};

    private SensorManager mSensorManager;
    private Sensor mRotationSensor;

    public ARRenderer(SensorManager sensorManager, Display display, TagParser tagParser) {
        mDisplay = display;
        mSensorManager = sensorManager;
        mRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mTagParser = tagParser;
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

        mPositionMarker = new PositionMarker();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1, 1, 2, 200);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        Matrix.multiplyMV(mLookAtVector, 0, mRotationMatrix, 0, mLookAtVector0, 0);
        Matrix.multiplyMV(mUpVector, 0, mRotationMatrix, 0, mUpVector0, 0);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 0, mLookAtVector[0], mLookAtVector[1], mLookAtVector[2], mUpVector[0], mUpVector[1], mUpVector[2]);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

        mPositionMarker.drawAtPosition(mVPMatrix, mViewMatrix, -5, -5, -5);
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
            float[] origMatrix = new float[16];
            float[] tempMatrix = new float[16];
            SensorManager.getRotationMatrixFromVector(origMatrix, event.values);

            //Remap based on device orientation. Note: these values are the opposite of what you might expect because the rotation matrix above is inverted.
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

            Matrix.transposeM(mRotationMatrix, 0, tempMatrix, 0);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    //Utility methods start

    //Return a rotation matrix which will rotate a given object to rotate the camera (applied first to it), assuming standard +y lookup +z lookat, camera at origin
    //code based on method described here http://www.lighthouse3d.com/opengl/billboarding/index.php?billCyl
    public void billboardM(float[] mat, float x, float y, float z) {
        float[] lookUp = new float[] {0, 1f, 0};
        float[] lookAt = new float[] {0, 0, 1f};
        float[] objToCamProj = new float[] {-x, 0, -z}; //project into xz plane
        normalize(objToCamProj);

        float cosOfAngleYaw = clamp(-1, dot(objToCamProj, lookAt), 1);
        float[] up = cross(objToCamProj, lookAt);
        Matrix.rotateM(mat, 0, (float)(Math.acos(cosOfAngleYaw)*180/Math.PI), up[0], up[1], up[2]);

        float[] objToCam = new float[] {-x, -y, -z};
        normalize(objToCam);

        float cosOfAnglePitch = clamp(-1, dot(objToCamProj, objToCam), 1);
        float[] right = cross(up, lookAt);

        Matrix.rotateM(mat, 0, (float)(Math.acos(cosOfAnglePitch)*180/Math.PI), right[0], right[1], right[2]);
    }

    public float dot(float[] a, float[] b) {
        float result = 0;
        for (int i = 0; i < a.length; i++) {
            result += a[i] * b[i];
        }
        return result;
    }

    public float[] cross(float[] u, float v[]) {
        float[] result = new float[3];
        result[0] = u[1] * v[2] - u[2] * v[1];
        result[1] = u[2] * v[0] - u[0] * v[2];
        result[2] = u[0] * v[1] - u[1] - v[0];
        return result;
    }

    public void normalize(float[] v) {
        float squaredSum = 0;
        for (int i = 0; i < v.length; i++) {
            squaredSum += v[i]*v[i];
        }
        float length = (float) Math.sqrt(squaredSum);
        for (int i = 0; i < v.length; i++) {
            v[i] /= length;
        }
    }

    public float clamp(float min, float val, float max) {
        val = Math.max(min, val);
        val = Math.min(max, val);
        return val;
    }
}
