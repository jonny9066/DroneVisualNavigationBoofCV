package GPSUtils;

import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.factory.feature.associate.ConfigAssociateGreedy;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.geo.ConfigRansac;
import boofcv.factory.geo.FactoryMultiViewRobust;
import boofcv.gui.image.ImageGridPanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.transform.homography.HomographyPointOps_F64;
import org.ddogleg.fitting.modelset.ModelMatcher;
import org.ddogleg.struct.DogArray;
import org.ddogleg.struct.FastAccess;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class GPSVideoTest {
    public static void main(String[] args) {
        int skipFrames = 24 * 3;
        int maxFrames = 10000;
        int frameCounter = 0;
        double distanceThreshold = 1.5;
        Point startingPoint = GPSPointFactory.fromGPSCoords(32.09237848, 35.17513055, 564.05338779);
        List<Point> gpsPoints = new LinkedList<>();
        gpsPoints.add(startingPoint);

        double xDistance = 500;
        double yDistance = 500;

        // Load an image sequence
        MediaManager media = DefaultMediaManager.INSTANCE;
//		String fileName = UtilIO.pathExample("mosaic/airplane01.mjpeg");
//        String fileName = "resources/ariel.mp4";
        String fileName = "resources/drone_foot_trim.mp4";
        SimpleImageSequence<Planar<GrayF32>> video = media.openVideo(fileName, ImageType.pl(3, GrayF32.class));

        Planar<GrayF32> frame = video.next();
        Planar<GrayF32> previousFrame = frame;

        // compute distance (in meters) per pixel
        double xDistancePerPixel = xDistance / frame.getWidth();
        double yDistancePerPixel = yDistance / frame.getHeight();


        // Create the GUI for displaying the results + input image
        ImageGridPanel gui = new ImageGridPanel(1, 1);
        gui.setImage(0, 0, new BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB));
        gui.setPreferredSize(new Dimension(frame.width, frame.height));
        ShowImages.showWindow(gui, "Example Mosaic", true);

        while (video.hasNext() && frameCounter < maxFrames) {
            frame = video.next();
            BufferedImage bufferedCurrentFrame = ConvertBufferedImage.convertTo_F32(frame, null, true);

            if (frameCounter % skipFrames == 0) {
                BufferedImage bufferedPreviousFrame = ConvertBufferedImage.convertTo_F32(previousFrame, null, true);
                Homography2D_F64 transform = stitch(bufferedPreviousFrame, bufferedCurrentFrame, GrayF32.class);

                // use the homography to transform the center of the previous image
                Point2D_F64 startImageCenter = new Point2D_F64(bufferedPreviousFrame.getWidth() / 2.0, bufferedPreviousFrame.getHeight() / 2.0);
                Point2D_F64 transformedPoint = new Point2D_F64();
                HomographyPointOps_F64.transform(transform, startImageCenter, transformedPoint);

//                System.out.println(startImageCenter);
//                System.out.println(transformedPoint);

                // compute distances in meters and get new point from result
                double xDistanceMeters = (startImageCenter.x - transformedPoint.x) * xDistancePerPixel;
                double yDistanceMeters = (startImageCenter.y - transformedPoint.y) * yDistancePerPixel;
                Point lastPoint = gpsPoints.get(gpsPoints.size() - 1);
                Point newPoint = GPSPointFactory.fromVelocity(lastPoint, xDistanceMeters, yDistanceMeters, 0);
                double distance = PointAlgo.distance(lastPoint, newPoint);

                if (distance > distanceThreshold) {
                    gpsPoints.add(newPoint);
                    System.out.println("Moving! (Distance: " + distance + ")");
                }

                previousFrame = frame.clone();
            }
            frameCounter++;

            gui.setImage(0, 0, bufferedCurrentFrame);
            gui.repaint();
        }

        displayPointsOnMap(gpsPoints.toArray(new Point[0]));
    }

    public static <T extends ImageGray<T>, TD extends TupleDesc<TD>> Homography2D_F64 computeTransform(T imageA, T imageB, DetectDescribePoint<T, TD> detDesc, AssociateDescription<TD> associate, ModelMatcher<Homography2D_F64, AssociatedPair> modelMatcher) {
        // get the length of the description
        List<Point2D_F64> pointsA = new ArrayList<>();
        DogArray<TD> descA = UtilFeature.createArray(detDesc, 100);
        List<Point2D_F64> pointsB = new ArrayList<>();
        DogArray<TD> descB = UtilFeature.createArray(detDesc, 100);

        // extract feature locations and descriptions from each image
        describeImage(imageA, detDesc, pointsA, descA);
        describeImage(imageB, detDesc, pointsB, descB);

        // Associate features between the two images
        associate.setSource(descA);
        associate.setDestination(descB);
        associate.associate();

        // create a list of AssociatedPairs that tell the model matcher how a feature moved
        FastAccess<AssociatedIndex> matches = associate.getMatches();
        List<AssociatedPair> pairs = new ArrayList<>();

        for (int i = 0; i < matches.size(); i++) {
            AssociatedIndex match = matches.get(i);

            Point2D_F64 a = pointsA.get(match.src);
            Point2D_F64 b = pointsB.get(match.dst);

            pairs.add(new AssociatedPair(a, b, false));
        }

        // find the best fit model to describe the change between these images
        if (!modelMatcher.process(pairs)) throw new RuntimeException("Model Matcher failed!");

        // return the found image transform
        return modelMatcher.getModelParameters().copy();
    }

    /**
     * Detects features inside the two images and computes descriptions at those points.
     */
    private static <T extends ImageGray<T>, TD extends TupleDesc<TD>> void describeImage(T image, DetectDescribePoint<T, TD> detDesc, List<Point2D_F64> points, DogArray<TD> listDescs) {
        detDesc.detect(image);

        listDescs.reset();
        for (int i = 0; i < detDesc.getNumberOfFeatures(); i++) {
            points.add(detDesc.getLocation(i).copy());
            listDescs.grow().setTo(detDesc.getDescription(i));
        }
    }

    /**
     * Given two input images create and display an image where the two have been overlayed on top of each other.
     */
    public static <T extends ImageGray<T>> Homography2D_F64 stitch(BufferedImage imageA, BufferedImage imageB, Class<T> imageType) {
        T inputA = ConvertBufferedImage.convertFromSingle(imageA, null, imageType);
        T inputB = ConvertBufferedImage.convertFromSingle(imageB, null, imageType);

        // Detect using the standard SURF feature descriptor and describer
        DetectDescribePoint detDesc = FactoryDetectDescribe.surfStable(new ConfigFastHessian(1, 2, 200, 1, 9, 4, 4), null, null, imageType);
        ScoreAssociation<TupleDesc_F64> scorer = FactoryAssociation.scoreEuclidean(TupleDesc_F64.class, true);
        AssociateDescription<TupleDesc_F64> associate = FactoryAssociation.greedy(new ConfigAssociateGreedy(true, 2), scorer);

        // fit the images using a homography. This works well for rotations and distant objects.
        ModelMatcher<Homography2D_F64, AssociatedPair> modelMatcher = FactoryMultiViewRobust.homographyRansac(null, new ConfigRansac(60, 3));

        Homography2D_F64 H = computeTransform(inputA, inputB, detDesc, associate, modelMatcher);

        return H;
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
            mapViewer.setZoom(6);


            // Create a list of waypoints from the given points
            Set<Waypoint> waypoints = new HashSet<>();
            for (Point point : points) {
                double[] gpsCoords = CoordinateConverter.xyzToLatLonDegrees(new double[]{point.x(), point.y(), point.z()});
                GeoPosition position = new GeoPosition(gpsCoords[0], gpsCoords[1]);
                waypoints.add(new DefaultWaypoint(position));
            }

            // Create a WaypointPainter to display the waypoints on the map
            WaypointPainter<Waypoint> waypointPainter = new WaypointPainter<>();
            waypointPainter.setWaypoints(waypoints);

            // Add the WaypointPainter to the map viewer
            mapViewer.setOverlayPainter(waypointPainter);

            // Set the initial display position
            double[] gpsCoords = CoordinateConverter.xyzToLatLonDegrees(new double[]{points[0].x(), points[0].y(), points[0].z()});
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


}
