package GPSUtils;

public class GPSPointFactory {
    public static Point fromGPSCoords(double lat, double lon, double alt) {
        double[] coords = CoordinateConverter.getXYZfromLatLonDegrees(lat, lon, alt);
        return new Point(coords[0], coords[1], coords[2]);
    }

    public static Point fromVelocity(Point point, double xVelocity, double yVelocity, double zVelocity) {
        double x = point.x() + xVelocity;
        double y = point.y() + yVelocity;
        double z = point.z() + zVelocity;

        return new Point(x, y, z);
    }
}
