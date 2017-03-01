package g20capstone.cameratest;

import android.opengl.Matrix;

/**
 * Created by Drew on 2/28/2017.
 */

public class Math3D {
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
