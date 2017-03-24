package g20capstone.arlocationtracking;

import android.app.Activity;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.util.Pair;
import android.view.Display;

import com.android.texample2.GLText;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Drew on 2/16/2017.
 */

public class ARRenderer implements GLSurfaceView.Renderer {
    private Activity mContext;
    private GLText mGlText;
    private TagParser mTagParser;
    private RotationWrapper mRotationWrapper;

    private PositionMarker mPositionMarker;

    // mMVPMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] mVPMatrix = new float[16];
    private final float[] mInvertedVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mInvertedViewMatrix = new float[16];

    private final float[] mRotationCalibrationMatrix = new float[16];
    private final float[] mRotationMatrix = new float[16];
    private final float[] mLookAtVector = new float[4];
    private final float[] mLookAtVector0 = new float[] {0, 0, -1, 1};
    private final float[] mUpVector = new float[4];
    private final float[] mUpVector0 = new float[] {0, 1, 0, 1};

    private boolean mCalibrating = false;

    private long mPositionTimer;
    private AtomicReference<Map<Integer, Point3D>> positionsRef = new AtomicReference<>(null);
    private Point3D ourPos;

    private float zFlip = 1;

    private static final float SCALE = 1f;//45f;

    private PositionCalculationRunnable curPosRunnable;

    public ARRenderer(Activity context, SensorManager sensorManager, Display display, TagParser tagParser) {
        mContext = context;
        mRotationWrapper = new RotationWrapper(sensorManager, display);
        mTagParser = tagParser;
        Matrix.setIdentityM(mRotationCalibrationMatrix, 0);
        mPositionTimer = System.nanoTime();
    }

    public void onResume() {
        mRotationWrapper.onResume();

        //Start up position calculation
        if (curPosRunnable != null) {
            curPosRunnable.keepRunning = false;
            curPosRunnable = null;
        }
        curPosRunnable = new PositionCalculationRunnable();
        new Thread(curPosRunnable).start();
    }

    public void onPause() {
        mRotationWrapper.onPause();

        //Stop position calculation
        if (curPosRunnable != null) {
            curPosRunnable.keepRunning = false;
            curPosRunnable = null;
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        mGlText = new GLText(null, mContext.getAssets()); //based on https://github.com/d3alek/Texample2/blob/master/Texample23D/src/com/android/texample2/GLText.java
        mGlText.load("OpenSans-Regular.ttf", 40, 2, 2);

        int textureOnScreenHandle = OpenGLUtils.loadTexture(mContext, mContext.getResources().getIdentifier("tricolor_circle", "drawable", mContext.getPackageName()));
        int textureOffScreenHandle = OpenGLUtils.loadTexture(mContext, mContext.getResources().getIdentifier("arrow", "drawable", mContext.getPackageName()));
        mPositionMarker = new PositionMarker(textureOnScreenHandle, textureOffScreenHandle, mGlText);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        //Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1, 1, 1f, 200);

        //From http://blog.db-in.com/cameras-on-opengl-es-2-x/
        float[] matrix = new float[16];

        float near = 0.1f, far = 1000.0f;
        float angleOfView = 60.0f;
        float aspectRatio = ratio;

        // tan(FOV_H/2) / screen_width = tan(FOV_V/2) / screen_height
        // http://answers.unity3d.com/questions/888008/relationship-between-fov-and-aspect-ratio.html
        //FOV_V = 2 * atan(screen_height / screen_width * tan(FOV_H/2))
        float fovy = 180f / (float)Math.PI * 2f * (float)Math.atan(Math.tan(angleOfView*Math.PI/180/2/aspectRatio));
        Matrix.perspectiveM(mProjectionMatrix, 0, fovy, aspectRatio, near, far);
    }

    //Constantly calculates new positions
    protected class PositionCalculationRunnable implements Runnable {
        public volatile boolean keepRunning = true;

        @Override
        public void run() {
            while (keepRunning) { //If told to stop, stop!
                try {
                    Map<Pair<Integer, Integer>, Float> ranges = mTagParser.getRanges();
                    Map<Integer, Point3D> possiblePositions = PositionCalculation.getPositions(ranges);
                    if (possiblePositions != null) {
                        Map<Integer, Point3D> positions = possiblePositions;
                        positionsRef.lazySet(positions);
                        //Log.d("ARRenderer", positions.toString());
                    }
                    Thread.sleep(0, 1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        Map<Integer, Point3D> positions = positionsRef.get(); //multithreaded
        ourPos = positions.get(mTagParser.ourId);

        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        //Start by doing the rotation matrix...
        Matrix.multiplyMM(mRotationMatrix, 0, mRotationCalibrationMatrix, 0, mRotationWrapper.getRotationMatrixRaw(), 0);

        Matrix.multiplyMV(mLookAtVector, 0, mRotationMatrix, 0, mLookAtVector0, 0);
        Matrix.multiplyMV(mUpVector, 0, mRotationMatrix, 0, mUpVector0, 0);

        // Set the camera position (View matrix)
        if (mCalibrating) {
            Point3D calibrationDevicePos = positions.get(1); //change this later TODO
            if (calibrationDevicePos != null && ourPos != null) {
                Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 0, (float)(SCALE*(calibrationDevicePos.x - ourPos.x)), (float)(SCALE*(calibrationDevicePos.y - ourPos.y)), (float)(SCALE * zFlip * (calibrationDevicePos.z - ourPos.z)), mUpVector0[0], mUpVector0[1], mUpVector0[2]);
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
                    mPositionMarker.drawAtPosition(mVPMatrix, mInvertedVPMatrix, mInvertedViewMatrix, SCALE * (float) (pos.x - ourPos.x), SCALE * (float) (pos.y - ourPos.y), SCALE * (float)(zFlip * (pos.z - ourPos.z)), "Tag " + e.getKey());
                }
            }
        }
    }

    public void toggleCalibrating() {
        if (mCalibrating) {
            //We're done calibrating now
            //The resulting calibration matrix is equal to the calibration matrix in play * the inverted raw rotation matrix
            float[] invertedRotationMatrixRaw = new float[16];
            Matrix.invertM(invertedRotationMatrixRaw, 0, mRotationWrapper.getRotationMatrixRaw(), 0);
            Matrix.multiplyMM(mRotationCalibrationMatrix, 0, mInvertedViewMatrix, 0, invertedRotationMatrixRaw, 0);
            mCalibrating = false;
        } else {
            Matrix.setIdentityM(mRotationCalibrationMatrix, 0);
            mCalibrating = true;
        }
    }

    public void flipZ() {
        this.zFlip *= -1;
    }
}
