package g20capstone.arlocationtracking;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Range;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

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
                setupCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (mCameraCallback != null) {
                    mCameraCallback.stopCamera();
                    mCameraCallback = null;
                }
            }
        });
    }

    protected void setupCamera() {
        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
                String[] cameras = manager.getCameraIdList();

                String camId = cameras[0];
                CameraCharacteristics cc = manager.getCameraCharacteristics(cameras[0]);
                Range<Integer>[] fpsRange = cc.get(cc.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                try {
                    mCameraCallback = new CameraCallback(mSurfaceView, fpsRange, mActivity);
                    manager.openCamera(camId, mCameraCallback, null);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            ActivityCompat.requestPermissions(mActivity, new String[]{Manifest.permission.CAMERA}, MainActivity.PERMISSIONS_REQUEST_CAMERA);
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
