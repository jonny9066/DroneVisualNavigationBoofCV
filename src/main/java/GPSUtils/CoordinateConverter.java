package GPSUtils;

public class CoordinateConverter {
    // Convert between GPS and cartesian coordinates
    // x -> east - west, y -> north-south, z -> vertical movement

    public static double[] llaToCartesian(double latitude, double longitude, double altitude) {
        double[] cartesianCoordinates = new double[3];

        double latRad = Math.toRadians(latitude);
        double lonRad = Math.toRadians(longitude);

        double cosLat = Math.cos(latRad);
        double sinLat = Math.sin(latRad);
        double cosLon = Math.cos(lonRad);
        double sinLon = Math.sin(lonRad);

        double radius = calculateRadius(latitude);

        cartesianCoordinates[0] = (radius + altitude) * cosLat * cosLon; // x
        cartesianCoordinates[1] = (radius + altitude) * cosLat * sinLon; // y
        cartesianCoordinates[2] = (radius + altitude) * sinLat; // z

        return cartesianCoordinates;
    }

    public static double[] cartesianToLLA(double x, double y, double z) {
        double[] llaCoordinates = new double[3];

        double radius = Math.sqrt(x * x + y * y + z * z);
        double latitude = Math.asin(z / radius);
        double longitude = Math.atan2(y, x);

        llaCoordinates[0] = Math.toDegrees(latitude); // latitude
        llaCoordinates[1] = Math.toDegrees(longitude); // longitude
        llaCoordinates[2] = radius - calculateRadius(llaCoordinates[0]); // altitude

        return llaCoordinates;
    }

    private static double calculateRadius(double latitude) {
        double equatorialRadius = 6378137; // Earth's equatorial radius in meters
        double polarRadius = 6356752; // Earth's polar radius in meters

        double latRad = Math.toRadians(latitude);
        double numerator = Math.pow(equatorialRadius * equatorialRadius * Math.cos(latRad), 2) + Math.pow(polarRadius * polarRadius * Math.sin(latRad), 2);
        double denominator = Math.pow(equatorialRadius * Math.cos(latRad), 2) + Math.pow(polarRadius * Math.sin(latRad), 2);

        return Math.sqrt(numerator / denominator);
    }

    public static void main(String[] args) {
        double latitude = 40.7128; // Latitude of point (New York)
        double longitude = -74.0060; // Longitude of point (New York)
        double altitude = 10.0; // Altitude of point (New York)

        double[] cartesianCoordinates = llaToCartesian(latitude, longitude, altitude);
        System.out.println("Cartesian coordinates (x, y, z): " + cartesianCoordinates[0] + ", " + cartesianCoordinates[1] + ", " + cartesianCoordinates[2]);

        double[] llaCoordinates = cartesianToLLA(cartesianCoordinates[0], cartesianCoordinates[1], cartesianCoordinates[2]);
        System.out.println("LLA coordinates (latitude, longitude, altitude): " + llaCoordinates[0] + ", " + llaCoordinates[1] + ", " + llaCoordinates[2]);
    }
}

