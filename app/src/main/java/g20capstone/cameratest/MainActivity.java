package g20capstone.cameratest;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.ftdi.j2xx.FT_Device;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity {

    public static final int PERMISSIONS_REQUEST_CAMERA = 42;

    private GLSurfaceView mGLSurfaceView;
    private SurfaceView mSurfaceView;
    private ARRenderer mARRenderer;
    private CameraCallback mCameraCallback;

    private TagManager mTagManager;
    private final TagParser mTagParser = new TagParser();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Stuff for USB...
        mTagManager = new TagManager(this);

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
        final Activity thisActivity = this;
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (ContextCompat.checkSelfPermission(thisActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    setupCamera();
                } else {
                    ActivityCompat.requestPermissions(thisActivity, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });

        final Activity activity = this;

        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    FT_Device ftd = mTagManager.getFtDevice();

                    if (ftd != null && ftd.isOpen()) {
                        byte[] buffer = new byte[1024];
                        int len;
                        try {
                            len = ftd.read(buffer);
                        } catch (Exception e) {
                            return; //device closed or something
                        }
                        if (len > 0) {
                            final String text = new String(buffer, 0, len);
                            Log.d("Runnable", "Read bytes: " + text);

                            mTagParser.addString(text);
                            String s = "";
                        }
                    }

                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mTagManager.onStop();

        mARRenderer.onPause();
        mGLSurfaceView.onPause();

        if (mCameraCallback != null) {
            mCameraCallback.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mTagManager.onStart();

        mARRenderer.onResume();
        mGLSurfaceView.onResume();

        if (mCameraCallback != null) {
            mCameraCallback.onResume();
        }

        // Hide both the navigation bar and the status bar.
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
    }

    protected void setupCamera() {
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String camId = manager.getCameraIdList()[0];

            try {
                mCameraCallback = new CameraCallback(mSurfaceView);
                manager.openCamera(camId, mCameraCallback, null);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setupCamera();
                }
            }
        }
    }
}
