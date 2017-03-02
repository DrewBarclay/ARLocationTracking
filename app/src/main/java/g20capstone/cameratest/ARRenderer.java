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
    private OutOfBoundsPositionMarker mOutOfBoundsPositionMarker;

    // mMVPMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] mVPMatrix = new float[16];
    private final float[] mInvertedVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mInvertedViewMatrix = new float[16];

    private final float[] mRotationMatrix = new float[16];
    private final float[] mLookAtVector = new float[4];
    private final float[] mLookAtVector0 = new float[] {0, 0, -1, 1};
    private final float[] mUpVector = new float[4];
    private final float[] mUpVector0 = new float[] {0, 1, 0, 1};

    private SensorManager mSensorManager;
    private Sensor mRotationSensor;

    public ARRenderer(Activity context, SensorManager sensorManager, Display display, TagParser tagParser) {
        mContext = context;
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
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        mGlText = new GLText(null, mContext.getAssets()); //based on https://github.com/d3alek/Texample2/blob/master/Texample23D/src/com/android/texample2/GLText.java
        mGlText.load("OpenSans-Regular.ttf", 40, 2, 2);

        int textureHandle = loadTexture(mContext, mContext.getResources().getIdentifier("tricolor_circle", "drawable", mContext.getPackageName()));
        mPositionMarker = new PositionMarker(textureHandle);
        textureHandle = loadTexture(mContext, mContext.getResources().getIdentifier("arrow", "drawable", mContext.getPackageName()));
        mOutOfBoundsPositionMarker = new OutOfBoundsPositionMarker(textureHandle);
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
        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        Matrix.multiplyMV(mLookAtVector, 0, mRotationMatrix, 0, mLookAtVector0, 0);
        Matrix.multiplyMV(mUpVector, 0, mRotationMatrix, 0, mUpVector0, 0);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 0, mLookAtVector[0], mLookAtVector[1], mLookAtVector[2], mUpVector[0], mUpVector[1], mUpVector[2]);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

        Matrix.invertM(mInvertedViewMatrix, 0, mViewMatrix, 0);
        Matrix.invertM(mInvertedVPMatrix, 0, mVPMatrix, 0);

        mGlText.begin(mVPMatrix);
        mGlText.setScale(0.05f);
        mGlText.draw("Test String :)", -5, -5, -5, mInvertedViewMatrix);
        mGlText.end();

        float[] p = {-5f, -5f, -5f, 1};
        float[] pScreen = new float[4];
        Matrix.multiplyMV(pScreen, 0, mVPMatrix, 0, p, 0);
        boolean onScreen = true;
        for (int i = 0; i < 3; i++) {
            if (pScreen[i] <= -pScreen[3] || pScreen[i] >= pScreen[3]) { //if v outside the bounds -w <= x_i <= w
                onScreen = false; //is clipped
            }
        }
        if (onScreen) {
            mPositionMarker.drawAtPosition(mVPMatrix, mInvertedViewMatrix, -5, -5, -5);
        } else {
            mOutOfBoundsPositionMarker.drawAtPosition(mVPMatrix, mInvertedVPMatrix, mInvertedViewMatrix, pScreen[0]/pScreen[3], pScreen[1]/pScreen[3]);
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
}
