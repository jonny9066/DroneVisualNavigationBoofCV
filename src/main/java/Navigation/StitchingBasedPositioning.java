package Navigation;

import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.detect.interest.ConfigPointDetector;
import boofcv.abst.feature.detect.interest.PointDetectorTypes;
import boofcv.abst.sfm.d2.ImageMotion2D;
import boofcv.abst.sfm.d2.PlToGrayMotion2D;
import boofcv.abst.tracker.ConfigTrackerHybrid;
import boofcv.abst.tracker.PointTracker;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.PixelTransformHomography_F32;
import boofcv.alg.distort.impl.DistortSupport;
import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.alg.sfm.d2.StitchingFromMotion2D;
import boofcv.alg.tracker.klt.ConfigPKlt;
import boofcv.factory.feature.associate.ConfigAssociateGreedy;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.geo.ConfigRansac;
import boofcv.factory.geo.FactoryMultiViewRobust;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.factory.sfm.FactoryMotion2D;
import boofcv.factory.tracker.FactoryPointTracker;
import boofcv.gui.image.ImageGridPanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.image.UtilImageIO;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.ConfigLength;
import boofcv.struct.border.BorderType;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;
import boofcv.struct.pyramid.ConfigDiscreteLevels;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.transform.homography.HomographyPointOps_F64;
import org.ddogleg.fitting.modelset.ModelMatcher;
import org.ddogleg.struct.DogArray;
import org.ddogleg.struct.FastAccess;
import org.yaml.snakeyaml.external.com.google.gdata.util.common.base.Escaper;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.List;

public class StitchingBasedPositioning {
    private static final int SKIP_FRAMES = 2;
    private static int frameCounter = 0;

    public static void main(String[] args) {
        ConfigPKlt kltConfig = new ConfigPKlt();
        ConfigPointDetector pdConfig = new ConfigPointDetector();

        PointTracker<GrayF32> tracker = FactoryPointTracker.klt(kltConfig, pdConfig, GrayF32.class, GrayF32.class);
        // This estimates the 2D image motion
        // An Affine2D_F64 model also works quite well.
        ImageMotion2D<GrayF32, Homography2D_F64> motion2D = FactoryMotion2D.createMotion2D(220, 3, 2, 30, 0.6, 0.5, false, tracker, new Homography2D_F64());

        // wrap it so it output color images while estimating motion from gray
        ImageMotion2D<Planar<GrayF32>, Homography2D_F64> motion2DColor = new PlToGrayMotion2D<>(motion2D, GrayF32.class);

        // This fuses the images together
        StitchingFromMotion2D<Planar<GrayF32>, Homography2D_F64> stitch = FactoryMotion2D.createVideoStitch(0.5, motion2DColor, ImageType.pl(3, GrayF32.class));

        String mapPath = "resources/1_map.JPG";
        BufferedImage map = UtilImageIO.loadImage(mapPath);
        Planar<GrayF32> boofMap = ConvertBufferedImage.convertFromPlanar(map, null, true, GrayF32.class);

        if (map == null) {
            throw new RuntimeException("Image file: '" + mapPath + "' not found!");
        }

        BasicStroke stroke = new BasicStroke(15);
        Font font = new Font("Arial", Font.BOLD, 16);

        MediaManager media = DefaultMediaManager.INSTANCE;
        String fileName = "resources/1 (trimmed).mp4";
        SimpleImageSequence<Planar<GrayF32>> video = media.openVideo(fileName, ImageType.pl(3, GrayF32.class));

        if (video == null) {
            throw new RuntimeException("Video file: '" + fileName + "' not found!");
        }

        Planar<GrayF32> frame = video.next();
        Planar<GrayF32> previous_frame = frame.clone();

        Homography2D_F64 shrink = new Homography2D_F64(0.5, 0, frame.width / 4, 0, 0.5, frame.height / 4, 0, 0, 1);
        shrink = shrink.invert(null);

        // The mosaic will be larger in terms of pixels but the image will be scaled down.
        // To change this into stabilization just make it the same size as the input with no shrink.
        stitch.configure(frame.width, frame.height, shrink);


        ImageGridPanel gui = new ImageGridPanel(1, 1);
        ImageGridPanel mapPanel = new ImageGridPanel(1, 1);
        ImageGridPanel stitchingUI = new ImageGridPanel(1, 1);

        gui.setImage(0, 0, new BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB));
        stitchingUI.setImage(0, 0, new BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB));
        mapPanel.setImage(0, 0, map);

        gui.setPreferredSize(new Dimension(frame.width, frame.height));
        stitchingUI.setPreferredSize(new Dimension(frame.width, frame.height));
        mapPanel.setPreferredSize(new Dimension(map.getWidth(), map.getHeight()));

        ShowImages.showWindow(gui, "Live Video", true);
        ShowImages.showWindow(mapPanel, "Map", true);
        ShowImages.showWindow(stitchingUI, "Stitching", true);

        int x = -1;
        int y = -1;
        while (video.hasNext()) {
            frame = video.next();
//            frame = adjustSaturation(frame, 0.72f);
            BufferedImage bufferedCurrentFrame = ConvertBufferedImage.convertTo_F32(frame, null, true);

            if (frameCounter % SKIP_FRAMES == 0) {
                BufferedImage mapCopy = deepCopy(map);
                Graphics2D mapGraphics = mapCopy.createGraphics();
                mapGraphics.setColor(Color.RED);
                mapGraphics.setStroke(stroke);
                mapGraphics.setFont(font);

                stitch.reset();

                boolean mapSuccess = stitch.process(boofMap);
                boolean frameSuccess = stitch.process(frame);

//                previous_frame = frame.clone();
                stitch.setOriginToCurrent();
                if (frameSuccess && mapSuccess) {
                    BufferedImage bufferedStitchedImage = ConvertBufferedImage.convertTo_F32(stitch.getStitchedImage(), null, true);
                    stitchingUI.setImage(0, 0, bufferedStitchedImage);
                    Homography2D_F64 currentToWorld = stitch.getWorldToCurr().invert(null);
                    Point2D_F64 center = new Point2D_F64(frame.width / 2.0, frame.height / 2.0);
                    Point2D_F64 imageCoords = HomographyPointOps_F64.transform(currentToWorld, center, null);

                    x = (int) imageCoords.x;
                    y = (int) imageCoords.y;

                    mapGraphics.setColor(Color.GREEN);
                    mapGraphics.drawString("Stitching Success!", 10, 20);
                } else {
                    mapGraphics.drawString("Stitching Failed!", 10, 20);
                }

                if (x > 0 && y > 0) {
                    mapGraphics.setColor(Color.RED);
                    mapGraphics.drawLine(x, y, x, y);
                }

                mapPanel.setImage(0, 0, mapCopy);
                mapPanel.repaint();
                stitchingUI.repaint();
            }

            gui.setImage(0, 0, bufferedCurrentFrame);
            gui.repaint();
            frameCounter++;
        }
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
        DetectDescribePoint detDesc = FactoryDetectDescribe.surfStable(new ConfigFastHessian(1, 2, 700, 2, 9, 8, 8), null, null, imageType);
        ScoreAssociation<TupleDesc_F64> scorer = FactoryAssociation.scoreEuclidean(TupleDesc_F64.class, true);
        AssociateDescription<TupleDesc_F64> associate = FactoryAssociation.greedy(new ConfigAssociateGreedy(true, 2), scorer);

        // fit the images using a homography. This works well for rotations and distant objects.
        ModelMatcher<Homography2D_F64, AssociatedPair> modelMatcher = FactoryMultiViewRobust.homographyRansac(null, new ConfigRansac(220, 3));

        Homography2D_F64 H = computeTransform(inputA, inputB, detDesc, associate, modelMatcher);
        renderStitching(imageA, imageB, H, gui);
        return H;
    }

    /**
     * Renders and displays the stitched together images
     */
    public static void renderStitching(BufferedImage imageA, BufferedImage imageB, Homography2D_F64 fromAtoB, ImageGridPanel gui) {
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

        gui.setImage(0, 1, output);
    }

    private static Point2D_I32 renderPoint(int x0, int y0, Homography2D_F64 fromBtoWork) {
        Point2D_F64 result = new Point2D_F64();
        HomographyPointOps_F64.transform(fromBtoWork, new Point2D_F64(x0, y0), result);
        return new Point2D_I32((int) result.x, (int) result.y);
    }

    static BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    public static Planar<GrayF32> adjustSaturation(Planar<GrayF32> image, float saturationFactor) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Create a new Planar<GrayF32> image to store the modified image
        Planar<GrayF32> saturatedImage = new Planar<>(GrayF32.class, width, height, image.getNumBands());

        // Iterate over each pixel in the image
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Get the pixel value
                float[] pixel = new float[]{
                        image.getBand(0).get(x, y),
                        image.getBand(1).get(x, y),
                        image.getBand(2).get(x, y)
                };

                // Adjust the saturation component of each band
                for (int band = 0; band < image.getNumBands(); band++) {
                    pixel[band] *= saturationFactor;
                }

                // Set the modified pixel value in the new image
                saturatedImage.getBand(0).set(x, y, pixel[0]);
                saturatedImage.getBand(1).set(x, y, pixel[1]);
                saturatedImage.getBand(2).set(x, y, pixel[2]);
            }
        }

        return saturatedImage;
    }

}
