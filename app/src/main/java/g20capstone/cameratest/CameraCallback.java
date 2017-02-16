package g20capstone.cameratest;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.view.Surface;
import android.view.SurfaceView;

import java.util.Arrays;

/**
 * Created by Drew on 2/16/2017.
 */

public class CameraCallback extends CameraDevice.StateCallback {
    private SurfaceView mSurfaceView;
    private CaptureRequest mRequest;
    private CameraCaptureSession mSession;

    public CameraCallback(SurfaceView sv) {
        mSurfaceView = sv;
    }

    public void startCapturing() {
        try {
            mSession.setRepeatingBurst(Arrays.asList(mRequest), null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopCapturing() {
        try {
            mSession.stopRepeating();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onOpened(final CameraDevice camera) {
        try {
            final Surface surface = mSurfaceView.getHolder().getSurface();
            camera.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        mSession = session;
                        CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
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

    public void onPause() {
        if (mSession != null && mRequest != null) {
            stopCapturing();
        }
    }

    public void onResume() {
        if (mSession != null && mRequest != null) {
            startCapturing();
        }
    }
};