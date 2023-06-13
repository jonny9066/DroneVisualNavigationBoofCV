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
import boofcv.gui.image.ShowImages;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.misc.BoofLambdas;
import boofcv.struct.border.BorderType;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
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
import javax.xml.crypto.dsig.Transform;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GPSStitchingTest {
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

        renderStitching(imageA, imageB, H);
        return H;
    }

    /**
     * Renders and displays the stitched together images
     */
    public static void renderStitching(BufferedImage imageA, BufferedImage imageB, Homography2D_F64 fromAtoB) {
        // specify size of output image
        double scale = 0.5;

        // Convert into a BoofCV color format
        Planar<GrayF32> colorA = ConvertBufferedImage.convertFromPlanar(imageA, null, true, GrayF32.class);
        Planar<GrayF32> colorB = ConvertBufferedImage.convertFromPlanar(imageB, null, true, GrayF32.class);

        // Where the output images are rendered into
        Planar<GrayF32> work = colorA.createSameShape();

        // Adjust the transform so that the whole image can appear inside of it
        Homography2D_F64 fromAToWork = new Homography2D_F64(scale, 0, colorA.width / 4, 0, scale, colorA.height / 4, 0, 0, 1);
        Homography2D_F64 fromWorkToA = fromAToWork.invert(null);

        // Used to render the results onto an image
        PixelTransformHomography_F32 model = new PixelTransformHomography_F32();
        InterpolatePixelS<GrayF32> interp = FactoryInterpolation.bilinearPixelS(GrayF32.class, BorderType.ZERO);
        ImageDistort<Planar<GrayF32>, Planar<GrayF32>> distort = DistortSupport.createDistortPL(GrayF32.class, model, interp, false);
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

        ShowImages.showWindow(output, "Stitched Images", true);
    }

    private static Point2D_I32 renderPoint(int x0, int y0, Homography2D_F64 fromBtoWork) {
        Point2D_F64 result = new Point2D_F64();
        HomographyPointOps_F64.transform(fromBtoWork, new Point2D_F64(x0, y0), result);
        return new Point2D_I32((int) result.x, (int) result.y);
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
            mapViewer.setZoom(3);


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


    public static void main(String[] args) {
        Point src = GPSPointFactory.fromGPSCoords(30.97553, 34.94611, 0);
        Point dest = GPSPointFactory.fromGPSCoords(30.9730852, 34.94834504, 0);

        double xDistance = 746.16;
        double yDistance = 1231.67 - xDistance;
        double heightChange = 542.16536068 - 546.40294244;

        // load images
        BufferedImage imageA, imageB;
        imageA = UtilImageIO.loadImageNotNull("resources/gps_stitching/source.png");
        imageB = UtilImageIO.loadImageNotNull("resources/gps_stitching/shift.png");

        // compute distance (in meters) per pixel
        double xDistancePerPixel = xDistance / imageA.getWidth();
        double yDistancePerPixel = yDistance / imageA.getHeight();

        // compute homography and show stitching
        Homography2D_F64 transform = stitch(imageA, imageB, GrayF32.class);

        // use the homography to transform the center of the previous image
        Point2D_F64 startImageCenter = new Point2D_F64(imageA.getWidth() / 2.0, imageA.getHeight() / 2.0);
        Point2D_F64 transformedPoint = new Point2D_F64();
        HomographyPointOps_F64.transform(transform, startImageCenter, transformedPoint);

        System.out.println(startImageCenter);
        System.out.println(transformedPoint);

        // compute distances in meters and get new point from result
        double xDistanceMeters = (startImageCenter.x - transformedPoint.x) * xDistancePerPixel;
        double yDistanceMeters = (startImageCenter.y + transformedPoint.y) * yDistancePerPixel;
        Point newPoint = GPSPointFactory.fromVelocity(src, xDistanceMeters, yDistanceMeters, 0);

        // visualize points on map
        displayPointsOnMap(
                new Point[]{
                        src,
//                        dest,
                        newPoint
                }
        );

        // interesting to try, compute distance from stitching
        double xDifference = Math.pow(xDistanceMeters, 2);
        double yDifference = Math.pow(yDistanceMeters, 2);
        double estimatedDistance = Math.sqrt(xDifference + yDifference);
//        System.out.println("Estimated Distance To Destination: " + PointAlgo.distance(src, dest));
        System.out.println("Estimated Distance To New Point: " + PointAlgo.distance(src, newPoint));
        System.out.println("Stitching Estimated Distance To New Point: " + estimatedDistance);
    }
}
