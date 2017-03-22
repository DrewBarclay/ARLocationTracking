package g20capstone.cameratest;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.Arrays;

/**
 * Created by Drew on 3/21/2017.
 */

public class CameraWrapper {
    Activity mActivity;
    private SurfaceView mSurfaceView;
    private CameraCallback mCameraCallback;

    public CameraWrapper(Activity activity, SurfaceView surfaceView) {
        mActivity = activity;
        mSurfaceView = surfaceView;

        final Activity thisActivity = activity;
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (ContextCompat.checkSelfPermission(thisActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    setupCamera();
                } else {
                    ActivityCompat.requestPermissions(thisActivity, new String[]{Manifest.permission.CAMERA}, MainActivity.PERMISSIONS_REQUEST_CAMERA);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }

    protected void setupCamera() {
        try {
            CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
            String[] cameras = manager.getCameraIdList();

            String camId = cameras[0];
            CameraCharacteristics cc = manager.getCameraCharacteristics(cameras[0]);
            Range<Integer>[] fpsRange = cc.get(cc.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            try {
                mCameraCallback = new CameraCallback(mSurfaceView, fpsRange);
                manager.openCamera(camId, mCameraCallback, null);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onPermissionGranted() {
        //Our request was granted!
        setupCamera();
    }

    public void onPause() {
        if (mCameraCallback != null) {
            mCameraCallback.stopCamera();
            mCameraCallback = null;
        }
    }

    public void onResume() {
        setupCamera();
    }
}
