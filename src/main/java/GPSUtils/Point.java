package GPSUtils;

public class Point {
    private final double x;
    private final double y;
    private final double z;

    public Point(double x, double y, double z) {
        // This class takes GPS coordinates and converts them to Cartesian coordinates
        // look at the CoordinatesConverter for the cartesian space explanation
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    @Override
    public String toString() {
        return "[X: " + x + ", Y: " + y + ", Z: " + z + "]";
    }
}
