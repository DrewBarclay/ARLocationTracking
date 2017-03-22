package g20capstone.cameratest;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.util.Range;
import android.view.Surface;
import android.view.SurfaceView;

import java.util.Arrays;

/**
 * Created by Drew on 2/16/2017.
 */

public class CameraCallback extends CameraDevice.StateCallback {
    private SurfaceView mSurfaceView;
    private CaptureRequest mRequest;
    public CameraCaptureSession mSession;
    private CameraDevice mCamera;
    private Range<Integer> highestFPS;

    public CameraCallback(SurfaceView sv,  Range<Integer>[] fpsRange) {
        mSurfaceView = sv;
        highestFPS = new Range(0, 0);
        for (Range<Integer> r : fpsRange) {
            if (r.getUpper().compareTo(highestFPS.getUpper()) >= 0) {
                if (r.getLower().compareTo(highestFPS.getLower()) > 0) {
                    highestFPS = r; //Calculate highest FPS we can get out of the camera
                }
            }
        }

        highestFPS = new Range<Integer>(highestFPS.getLower() / 2, highestFPS.getUpper());
    }

    public void startCapturing() {
        try {
            mSession.setRepeatingBurst(Arrays.asList(mRequest), null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onOpened(final CameraDevice camera) {
        mCamera = camera;
        try {
            final Surface surface = mSurfaceView.getHolder().getSurface();
            camera.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        mSession = session;
                        CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        //builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_NIGHT);
                        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, highestFPS);
                        builder.addTarget(surface);
                        mRequest = builder.build();
                        startCapturing();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisconnected(CameraDevice camera) {

    }

    @Override
    public void onError(CameraDevice camera, int error) {

    }

    public void stopCamera() {
        if (mSession != null) {
            mSession.close();
        }

        if (mCamera != null) {
            mCamera.close();
        }
    }
};