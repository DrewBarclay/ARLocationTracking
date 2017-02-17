package g20capstone.cameratest;

/**
 * Created by Drew on 2/17/2017.
 */

public class Point3D {
    public double x;
    public double y;
    public double z;

    public Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double distanceTo(Point3D that) {
        double deltaX = this.x - that.x;
        double deltaY = this.y - that.y;
        double deltaZ = this.z - that.z;
        return Math.sqrt(deltaX*deltaX + deltaY*deltaY + deltaZ*deltaZ);
    }

    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}