package GPSUtils;


import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.*;

import javax.swing.*;
import java.util.*;

public class GPSTest {
    public static double distance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x() - p2.x(), 2) + Math.pow(p1.y() - p2.y(), 2) + Math.pow(p1.z() - p2.z(), 2));
    }

    public static void displayPointsOnMap(Point[] points) {
        SwingUtilities.invokeLater(() -> {
            // Create a JXMapViewer
            JXMapViewer mapViewer = new JXMapViewer();

            // Create a TileFactoryInfo for OpenStreetMap
            TileFactoryInfo info = new OSMTileFactoryInfo();
            DefaultTileFactory tileFactory = new DefaultTileFactory(info);
            mapViewer.setTileFactory(tileFactory);

            // Use multiple threads in parallel to load the tiles
            tileFactory.setThreadPoolSize(5);
            mapViewer.setZoom(4);


            // Create a list of waypoints from the given points
            Set<Waypoint> waypoints = new HashSet<>();
            for (Point point : points) {
                double[] gpsCoords = CoordinateConverter.cartesianToLLA(point.x(), point.y(), point.z());
                GeoPosition position = new GeoPosition(gpsCoords[0], gpsCoords[1]);
                waypoints.add(new DefaultWaypoint(position));
            }

            // Create a WaypointPainter to display the waypoints on the map
            WaypointPainter<Waypoint> waypointPainter = new WaypointPainter<>();
            waypointPainter.setWaypoints(waypoints);

            // Add the WaypointPainter to the map viewer
            mapViewer.setOverlayPainter(waypointPainter);

            // Set the initial display position
            double[] gpsCoords = CoordinateConverter.cartesianToLLA(points[0].x(), points[0].y(), points[0].z());
            GeoPosition startPosition = new GeoPosition(gpsCoords[0], gpsCoords[1]);
            mapViewer.setAddressLocation(startPosition);

            // Create a JFrame to display the map viewer
            JFrame frame = new JFrame("JXMapViewer Example");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().add(mapViewer);
            frame.setSize(800, 600);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    public static void main(String[] args) {
        // ariel coords - 32.10450072,35.17505654,560.53479318
        Point src = GPSPointFactory.fromGPSCoords(32.1048554, 35.1750541, 565.6083322);
        Point dest = GPSPointFactory.fromGPSCoords(32.107199, 35.1812363, 569.7893899);

        double cx = dest.x() - src.x();
        double cy = dest.y() - src.y();
        double cz = dest.z() - src.z();

        Point test_point1 = GPSPointFactory.fromVelocity(src, 0.5 * cx, 0.5 * cy, 0.5 * cz);

        displayPointsOnMap(new Point[]{
                src,
                dest,
                test_point1
        });
    }
}
