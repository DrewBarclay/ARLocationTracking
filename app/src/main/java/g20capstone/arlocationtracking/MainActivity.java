package g20capstone.arlocationtracking;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

public class MainActivity extends AppCompatActivity {

    public static final int PERMISSIONS_REQUEST_CAMERA = 42;

    private GLSurfaceView mGLSurfaceView;
    private SurfaceView mSurfaceView;
    private ARRenderer mARRenderer;

    private TagManager mTagManager;
    private final TagParser mTagParser = new TagParser();

    private CameraWrapper mCameraWrapper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide both the navigation bar and the status bar.
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        //Stuff for USB...
        mTagManager = new TagManager(this, mTagParser);

        //Set up openGL rendering
        mGLSurfaceView = new GLSurfaceView(this);
        addContentView(mGLSurfaceView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mGLSurfaceView.setEGLContextClientVersion(2);
        mGLSurfaceView.setEGLConfigChooser(8,8,8,8,16,0);
        mGLSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        mARRenderer = new ARRenderer(this, (SensorManager) getSystemService(Context.SENSOR_SERVICE), getWindowManager().getDefaultDisplay(), mTagParser);
        mGLSurfaceView.setRenderer(mARRenderer);

        //Set up camera
        mSurfaceView = new SurfaceView(this);
        addContentView(mSurfaceView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mCameraWrapper = new CameraWrapper(this, mSurfaceView);
    }

    @Override
    protected void onPause() {
        mTagManager.onPause();

        mARRenderer.onPause();

        mGLSurfaceView.onPause();

        mCameraWrapper.onPause();

        super.onPause();
    }

    @Override
    protected void onResume() {
        mTagManager.pollConnectedDevices(); //Start looking for USB; TODO check if this causes an error

        mTagManager.onResume();

        mARRenderer.onResume();

        mGLSurfaceView.onResume();

        mCameraWrapper.onResume();

        // Hide both the navigation bar and the status bar.
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        super.onResume();
    }

    final GestureDetector gestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            if (mARRenderer != null) {
                mARRenderer.flipZ();
            }
            return true;
        }

        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (mARRenderer != null) {
                mARRenderer.toggleCalibrating();
            }
            return true;
        }
    });

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mCameraWrapper.onPermissionGranted();
                }
            }
        }
    }
}
