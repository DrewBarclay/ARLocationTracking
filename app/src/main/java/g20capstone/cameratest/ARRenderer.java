package g20capstone.cameratest;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import com.android.texample2.GLText;

import java.util.Arrays;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Drew on 2/16/2017.
 */

public class ARRenderer implements GLSurfaceView.Renderer, SensorEventListener {
    private Activity mContext;
    private GLText mGlText;
    private TagParser mTagParser;

    private Display mDisplay;

    private PositionMarker mPositionMarker;

    // mMVPMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] mVPMatrix = new float[16];
    private final float[] mInvertedVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mInvertedViewMatrix = new float[16];

    private final float[] mRotationMatrixRaw = new float[16];
    private final float[] mRotationCalibrationMatrix = new float[16];
    private final float[] mRotationMatrix = new float[16];
    private final float[] mLookAtVector = new float[4];
    private final float[] mLookAtVector0 = new float[] {0, 0, -1, 1};
    private final float[] mUpVector = new float[4];
    private final float[] mUpVector0 = new float[] {0, 1, 0, 1};

    private SensorManager mSensorManager;
    private Sensor mRotationSensor;

    private boolean mCalibrating = false;

    private long mPositionTimer;
    private Map<Integer, Point3D> positions;

    public ARRenderer(Activity context, SensorManager sensorManager, Display display, TagParser tagParser) {
        mContext = context;
        mDisplay = display;
        mSensorManager = sensorManager;
        mRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mTagParser = tagParser;
        Matrix.setIdentityM(mRotationCalibrationMatrix, 0);
        mPositionTimer = System.nanoTime();
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
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        mGlText = new GLText(null, mContext.getAssets()); //based on https://github.com/d3alek/Texample2/blob/master/Texample23D/src/com/android/texample2/GLText.java
        mGlText.load("OpenSans-Regular.ttf", 40, 2, 2);

        int textureOnScreenHandle = loadTexture(mContext, mContext.getResources().getIdentifier("tricolor_circle", "drawable", mContext.getPackageName()));
        int textureOffScreenHandle = loadTexture(mContext, mContext.getResources().getIdentifier("arrow", "drawable", mContext.getPackageName()));
        mPositionMarker = new PositionMarker(textureOnScreenHandle, textureOffScreenHandle, mGlText);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1, 1, 1f, 200);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        //Every 50 ms...
        try {
            if (System.nanoTime() - mPositionTimer > 50e-3 * 1e9) {
                Map<Integer, Point3D> possiblePositions = mTagParser.getPositions();
                if (possiblePositions != null) {
                    positions = possiblePositions;
                }
                mPositionTimer = System.nanoTime();
            }
        } catch (Exception e) {

        }
        Point3D ourPos = (positions != null) ? (positions.get(mTagParser.ourId)) : (null);

        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        //Start by doing the rotation matrix...
        Matrix.multiplyMM(mRotationMatrix, 0, mRotationCalibrationMatrix, 0, mRotationMatrixRaw, 0);

        Matrix.multiplyMV(mLookAtVector, 0, mRotationMatrix, 0, mLookAtVector0, 0);
        Matrix.multiplyMV(mUpVector, 0, mRotationMatrix, 0, mUpVector0, 0);

        // Set the camera position (View matrix)
        if (mCalibrating) {
            Point3D calibrationDevicePos = positions.get(1); //change this later TODO
            if (calibrationDevicePos != null && ourPos != null) {
                Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 0, (float)(calibrationDevicePos.x - ourPos.x), (float)(calibrationDevicePos.y - ourPos.y), (float)(calibrationDevicePos.z - ourPos.z), mUpVector[0], mUpVector[1], mUpVector[2]);
            } else {
                Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 0, mLookAtVector[0], mLookAtVector[1], mLookAtVector[2], mUpVector[0], mUpVector[1], mUpVector[2]);
            }
        } else {
            Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 0, mLookAtVector[0], mLookAtVector[1], mLookAtVector[2], mUpVector[0], mUpVector[1], mUpVector[2]);
        }

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

        Matrix.invertM(mInvertedViewMatrix, 0, mViewMatrix, 0);
        Matrix.invertM(mInvertedVPMatrix, 0, mVPMatrix, 0);

        //Draw cubes on positions grabbed
        if (positions != null && ourPos != null) {
            for (Map.Entry<Integer, Point3D> e : positions.entrySet()) {
                if (e.getKey() != mTagParser.ourId) {
                    Point3D pos = e.getValue();
                    mPositionMarker.drawAtPosition(mVPMatrix, mInvertedVPMatrix, mInvertedViewMatrix, 5f * (float) (pos.x - ourPos.x), 5f * (float) (pos.y - ourPos.y), 5f * (float) (pos.z - ourPos.z), "Tag " + e.getKey());
                }
            }
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

    //Code copied from http://www.learnopengles.com/android-lesson-four-introducing-basic-texturing/
    public static int loadTexture(final Context context, final int resourceId)
    {
        final int[] textureHandle = new int[1];

        GLES20.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] != 0)
        {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;   // No pre-scaling

            // Read in the resource
            final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);

            // Bind to the texture in OpenGL
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

            // Set filtering
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

            // Load the bitmap into the bound texture.
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

            // Recycle the bitmap, since its data has been loaded into OpenGL.
            bitmap.recycle();
        }

        if (textureHandle[0] == 0)
        {
            throw new RuntimeException("Error loading texture.");
        }

        return textureHandle[0];
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

    public float[] getRotationCalibrationMatrix() {
        return mRotationCalibrationMatrix;
    }

    public float[] getRotationMatrix() {
        return mRotationMatrix;
    }


    public boolean isCalibrating() {
        return mCalibrating;
    }

    public void setCalibrating(boolean mCalibrating) {
        this.mCalibrating = mCalibrating;
    }

    public float[] getInvertedViewMatrix() {
        return mInvertedViewMatrix;
    }
}
