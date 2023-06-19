package GPSUtils;

import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.PixelTransformHomography_F32;
import boofcv.alg.distort.impl.DistortSupport;
import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.factory.feature.associate.ConfigAssociateGreedy;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.geo.ConfigRansac;
import boofcv.factory.geo.FactoryMultiViewRobust;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.gui.image.ImageGridPanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.border.BorderType;
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
import georegression.struct.point.Point2D_I32;
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
        int skipFrames = 10;
        int maxFrames = 10000;
        int frameCounter = 0;
        double distanceThreshold = 1.5;
        Point startingPoint = GPSPointFactory.fromGPSCoords(32.09237848, 35.17513055, 564.05338779);
        List<Point> gpsPoints = new LinkedList<>();
        gpsPoints.add(startingPoint);
        double degree = 0.0;

        double xDistance = 500;
        double yDistance = 500;

        // Load an image sequence
        MediaManager media = DefaultMediaManager.INSTANCE;
//		String fileName = UtilIO.pathExample("mosaic/airplane01.mjpeg");
//        String fileName = "resources/ariel.mp4";
        String fileName = "resources/DJI_0520.MP4";
        SimpleImageSequence<Planar<GrayF32>> video = media.openVideo(fileName, ImageType.pl(3, GrayF32.class));

        Planar<GrayF32> frame = video.next();
        Planar<GrayF32> previousFrame = frame;

        // compute distance (in meters) per pixel
        double xDistancePerPixel = xDistance / frame.getWidth();
        double yDistancePerPixel = yDistance / frame.getHeight();

        // Create the GUI for displaying the results + input image
        ImageGridPanel gui = new ImageGridPanel(1, 2);
        gui.setImage(0, 0, new BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB));
        gui.setImage(0, 1, new BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB));
        gui.setPreferredSize(new Dimension(2 * frame.width, 2 * frame.height));
        ShowImages.showWindow(gui, "Example Mosaic", true);

        while (video.hasNext() && frameCounter < maxFrames) {
            frame = video.next();
            BufferedImage bufferedCurrentFrame = ConvertBufferedImage.convertTo_F32(frame, null, true);

            if (frameCounter % skipFrames == 0) {
                BufferedImage bufferedPreviousFrame = ConvertBufferedImage.convertTo_F32(previousFrame, null, true);
                Homography2D_F64 transform = stitch(bufferedPreviousFrame, bufferedCurrentFrame, GrayF32.class, gui);

                // use the homography to transform the center of the previous image
                Point2D_F64 startImageCenter = new Point2D_F64(bufferedPreviousFrame.getWidth() / 2.0, bufferedPreviousFrame.getHeight() / 2.0);
                Point2D_F64 startImageTop = new Point2D_F64(bufferedPreviousFrame.getWidth() / 2.0, 0.0);
                Point2D_F64 transformedCenterPoint = new Point2D_F64();
                Point2D_F64 transformedTopPoint = new Point2D_F64();
                HomographyPointOps_F64.transform(transform, startImageCenter, transformedCenterPoint);
                HomographyPointOps_F64.transform(transform, startImageTop, transformedTopPoint);
                double turnDegrees = calculateDegree(
                        startImageTop.x - startImageCenter.x,
                        startImageTop.y - startImageCenter.y,
                        transformedTopPoint.x - transformedCenterPoint.x,
                        transformedTopPoint.y - transformedCenterPoint.y);
                degree += turnDegrees;
                System.out.println("Change in Degrees: " + turnDegrees);

//                System.out.println(startImageCenter);
//                System.out.println(transformedCenterPoint);

                // compute distances in meters and get new point from result
                double xDistanceMeters = (startImageCenter.x - transformedCenterPoint.x) * xDistancePerPixel;
                double yDistanceMeters = (startImageCenter.y - transformedCenterPoint.y) * yDistancePerPixel;
                double[] rotatedVector = rotateVector(xDistanceMeters, yDistanceMeters, degree);
                Point lastPoint = gpsPoints.get(gpsPoints.size() - 1);
                Point newPoint = GPSPointFactory.fromVelocity(lastPoint, rotatedVector[0], rotatedVector[1], 0);
                double distance = PointAlgo.distance(lastPoint, newPoint);

                if (distance > distanceThreshold) {
                    gpsPoints.add(newPoint);
//                    System.out.println("Moving! (Distance: " + distance + ")");
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
    public static <T extends ImageGray<T>> Homography2D_F64 stitch(BufferedImage imageA, BufferedImage imageB, Class<T> imageType, ImageGridPanel gui) {
        T inputA = ConvertBufferedImage.convertFromSingle(imageA, null, imageType);
        T inputB = ConvertBufferedImage.convertFromSingle(imageB, null, imageType);

        // Detect using the standard SURF feature descriptor and describer
        DetectDescribePoint detDesc = FactoryDetectDescribe.surfStable(new ConfigFastHessian(1, 2, 200, 1, 9, 4, 4), null, null, imageType);
        ScoreAssociation<TupleDesc_F64> scorer = FactoryAssociation.scoreEuclidean(TupleDesc_F64.class, true);
        AssociateDescription<TupleDesc_F64> associate = FactoryAssociation.greedy(new ConfigAssociateGreedy(true, 2), scorer);

        // fit the images using a homography. This works well for rotations and distant objects.
        ModelMatcher<Homography2D_F64, AssociatedPair> modelMatcher = FactoryMultiViewRobust.homographyRansac(null, new ConfigRansac(60, 3));

        Homography2D_F64 H = computeTransform(inputA, inputB, detDesc, associate, modelMatcher);
        renderStitching(imageA, imageB, H, gui);
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
            mapViewer.setZoom(4);


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

    public static void renderStitching(BufferedImage imageA, BufferedImage imageB,
                                       Homography2D_F64 fromAtoB, ImageGridPanel gui) {
        // specify size of output image
        double scale = 0.5;

        // Convert into a BoofCV color format
        Planar<GrayF32> colorA =
                ConvertBufferedImage.convertFromPlanar(imageA, null, true, GrayF32.class);
        Planar<GrayF32> colorB =
                ConvertBufferedImage.convertFromPlanar(imageB, null, true, GrayF32.class);

        // Where the output images are rendered into
        Planar<GrayF32> work = colorA.createSameShape();

        // Adjust the transform so that the whole image can appear inside of it
        Homography2D_F64 fromAToWork = new Homography2D_F64(scale, 0, colorA.width / 4, 0, scale, colorA.height / 4, 0, 0, 1);
        Homography2D_F64 fromWorkToA = fromAToWork.invert(null);

        // Used to render the results onto an image
        PixelTransformHomography_F32 model = new PixelTransformHomography_F32();
        InterpolatePixelS<GrayF32> interp = FactoryInterpolation.bilinearPixelS(GrayF32.class, BorderType.ZERO);
        ImageDistort<Planar<GrayF32>, Planar<GrayF32>> distort =
                DistortSupport.createDistortPL(GrayF32.class, model, interp, false);
        distort.setRenderAll(false);

        // Render first image
        model.setTo(fromWorkToA);
        distort.apply(colorA, work);

        // Render second image
        Homography2D_F64 fromWorkToB = fromWorkToA.concat(fromAtoB, null);
        model.setTo(fromWorkToB);
        distort.apply(colorB, work);

        // Convert the rendered image into a BufferedImage
        BufferedImage output = new BufferedImage(work.width, work.height, imageA.getType());
        ConvertBufferedImage.convertTo(work, output, true);

        Graphics2D g2 = output.createGraphics();

        // draw lines around the distorted image to make it easier to see
        Homography2D_F64 fromBtoWork = fromWorkToB.invert(null);
        Point2D_I32 corners[] = new Point2D_I32[4];
        corners[0] = renderPoint(0, 0, fromBtoWork);
        corners[1] = renderPoint(colorB.width, 0, fromBtoWork);
        corners[2] = renderPoint(colorB.width, colorB.height, fromBtoWork);
        corners[3] = renderPoint(0, colorB.height, fromBtoWork);

        g2.setColor(Color.ORANGE);
        g2.setStroke(new BasicStroke(4));
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawLine(corners[0].x, corners[0].y, corners[1].x, corners[1].y);
        g2.drawLine(corners[1].x, corners[1].y, corners[2].x, corners[2].y);
        g2.drawLine(corners[2].x, corners[2].y, corners[3].x, corners[3].y);
        g2.drawLine(corners[3].x, corners[3].y, corners[0].x, corners[0].y);

        gui.setImage(0, 1, output);
    }

    private static Point2D_I32 renderPoint(int x0, int y0, Homography2D_F64 fromBtoWork) {
        Point2D_F64 result = new Point2D_F64();
        HomographyPointOps_F64.transform(fromBtoWork, new Point2D_F64(x0, y0), result);
        return new Point2D_I32((int) result.x, (int) result.y);
    }

    public static double calculateDegree(double x1, double y1, double x2, double y2) {
        double angle1 = Math.atan2(y1, x1);
        double angle2 = Math.atan2(y2, x2);
        double radians = angle2 - angle1;

        // Convert the angle to the range of -pi to pi
        if (radians > Math.PI) {
            radians -= 2 * Math.PI;
        } else if (radians < -Math.PI) {
            radians += 2 * Math.PI;
        }

        // Convert radians to degrees

        return Math.toDegrees(radians);
    }

    public static double[] rotateVector(double x, double y, double degrees) {
        // Convert the angle from degrees to radians
        double radians = Math.toRadians(degrees);

        // Calculate the cosine and sine of the angle
        double cosTheta = Math.cos(radians);
        double sinTheta = Math.sin(radians);

        // Perform the rotation using the rotation matrix
        double newX = x * cosTheta - y * sinTheta;
        double newY = x * sinTheta + y * cosTheta;

        // Return the new rotated vector as an array
        return new double[]{newX, newY};
    }
}
