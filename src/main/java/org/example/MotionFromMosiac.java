/*
 * Copyright (c) 2021, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.example;

import boofcv.abst.feature.detect.interest.ConfigPointDetector;
import boofcv.abst.feature.detect.interest.PointDetectorTypes;
import boofcv.abst.sfm.d2.ImageMotion2D;
import boofcv.abst.sfm.d2.PlToGrayMotion2D;
import boofcv.abst.tracker.PointTracker;
import boofcv.alg.filter.misc.AverageDownSampleOps;
import boofcv.alg.sfm.d2.StitchingFromMotion2D;
import boofcv.factory.sfm.FactoryMotion2D;
import boofcv.factory.tracker.FactoryPointTracker;
import boofcv.gui.image.ImageGridPanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.shapes.Quadrilateral_F64;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Example of how to create a mosaic from a video sequence using StitchingFromMotion2D. Mosaics work best
 * when the scene being observed is far away or a flat surface. The camera motion should typically be rotational only,
 * but translation can work depending on the scene's geometry. Motion blur and cheap cameras in general will degrade
 * performance significantly with the current algorithm. This example just demonstrates a gray scale image, but
 * with additional work color images can also be processed.
 *
 * @author Peter Abeles
 */
public class MotionFromMosiac {
	private static final Logger logger = Logger.getLogger(MotionFromMosiac.class.getName());
	private static final int SHRINK_VIDEO_FACTOR = 6;

	PointTracker<GrayF32> tracker;
	ImageMotion2D<GrayF32, Homography2D_F64> motion2D;
	ImageMotion2D<Planar<GrayF32>, Homography2D_F64> motion2DColor;
	StitchingFromMotion2D<Planar<GrayF32>, Homography2D_F64> stitch;
	ArrayList<Point2D_F64> pathLocations;
	ImageGridPanel gui;// for debugging
	Planar<GrayF32> lastFrame;
	private int numFrames;

	// instantiates algorithms and some parameters.
	// accepts first frame as input
	public MotionFromMosiac(Planar<GrayF32> frame){
		frame = shrinkImage(frame, SHRINK_VIDEO_FACTOR);

		lastFrame = frame;
		numFrames = 1;

		ConfigPointDetector configDetector = new ConfigPointDetector();
		// Configure the feature detector
		configDetector.type = PointDetectorTypes.SHI_TOMASI;
		configDetector.general.maxFeatures = 300;
		configDetector.general.radius = 3;
		configDetector.general.threshold = 1;

		// Use a KLT tracker
		tracker = FactoryPointTracker.klt(4, configDetector, 3, GrayF32.class, GrayF32.class);
		// This estimates the 2D image motion
		// An Affine2D_F64 model also works quite well.
		motion2D = FactoryMotion2D.createMotion2D(220, 3, 2, 30, 0.6, 0.5, false, tracker, new Homography2D_F64());

		// wrap it so it output color images while estimating motion from gray
		motion2DColor = new PlToGrayMotion2D<>(motion2D, GrayF32.class);
		// This fuses the images together
		stitch = FactoryMotion2D.createVideoStitch(0.5, motion2DColor, ImageType.pl(3, GrayF32.class));

		// shrink the input image and center it
		Homography2D_F64 shrink = new Homography2D_F64(0.5, 0, frame.width/4, 0, 0.5, frame.height/4, 0, 0, 1);
		shrink = shrink.invert(null);
		// The mosaic will be larger in terms of pixels but the image will be scaled down.
		// To change this into stabilization just make it the same size as the input with no shrink.
		stitch.configure(frame.width, frame.height, shrink);

		// process the first frame
		stitch.process(frame);


		// initialize array holding points on path
		pathLocations = new ArrayList<>();
		// get location and log
		Point2D_F64 location = getCenterFromCorners(stitch.getImageCorners(frame.width, frame.height, null));
		pathLocations.add(location);
//		logLocation(location);

		// Create the GUI for displaying the results + input image
		gui = new ImageGridPanel(1, 2);
		// (0,0) for input, (0,1) for mosiac
		gui.setImage(0, 0, new BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB));
		gui.setImage(0, 1, new BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB));
		gui.setPreferredSize(new Dimension(3*frame.width, frame.height*2));

	}

	public Point2D_F64 getLastLocation(){
		return getCenterFromCorners(stitch.getImageCorners(lastFrame.width, lastFrame.height, null));

	}

	// stitch next frame to mosiac
	// returns true upon processing successfuly
	public boolean processFrame(Planar<GrayF32> frame){
		// yoni: change input size druing runtime
		frame = shrinkImage(frame, SHRINK_VIDEO_FACTOR);

		if (!stitch.process(frame)) {
			return false;
		}

		// if the current image is close to the image border recenter the mosaic
		Quadrilateral_F64 corners = stitch.getImageCorners(frame.width, frame.height, null);
		if (nearBorder(corners.a, stitch) || nearBorder(corners.b, stitch) ||
				nearBorder(corners.c, stitch) || nearBorder(corners.d, stitch)) {
			stitch.setOriginToCurrent();

			// yoni: must clear points when change of perspective
			// ideally, transform points to new coordinate system
			pathLocations.clear();

			// Yoni: enlarge as much as needed
			// only enlarge the image once
			//if (!enlarged) {
//			enlarged = true;

			// double the image size and shift it over to keep it centered
			int widthOld = stitch.getStitchedImage().width;
			int heightOld = stitch.getStitchedImage().height;

			int widthNew = widthOld*2;
			int heightNew = heightOld*2;

			int tranX = (widthNew - widthOld)/2;
			int tranY = (heightNew - heightOld)/2;

			// Yoni: just translates image?
			Homography2D_F64 newToOldStitch = new Homography2D_F64(1, 0, -tranX, 0, 1, -tranY, 0, 0, 1);

			stitch.resizeStitchImage(widthNew, heightNew, newToOldStitch);
			gui.setImage(0, 1, new BufferedImage(widthNew, heightNew, BufferedImage.TYPE_INT_RGB));

			//} // end of enlarging image

			// Yoni: save after enalrge
//			savePlanar_F32(stitch.getStitchedImage(),"mosiacVideo " + numFrames + "AfterReset.png" );

			return true;
		}//end of recentering mosiac

		updateGui();

		return true;
	}

	private void updateGui(){
		Quadrilateral_F64 corners = stitch.getImageCorners(lastFrame.width, lastFrame.height, null);
		// display the mosaic
		ConvertBufferedImage.convertTo(lastFrame, gui.getImage(0, 0), true);
		ConvertBufferedImage.convertTo(stitch.getStitchedImage(), gui.getImage(0, 1), true);

		// draw a red quadrilateral around the current frame in the mosaic
		Graphics2D g2 = gui.getImage(0, 1).createGraphics();
		g2.setColor(Color.RED);
		g2.drawLine((int)corners.a.x, (int)corners.a.y, (int)corners.b.x, (int)corners.b.y);
		g2.drawLine((int)corners.b.x, (int)corners.b.y, (int)corners.c.x, (int)corners.c.y);
		g2.drawLine((int)corners.c.x, (int)corners.c.y, (int)corners.d.x, (int)corners.d.y);
		g2.drawLine((int)corners.d.x, (int)corners.d.y, (int)corners.a.x, (int)corners.a.y);

		if(pathLocations.size()!=0){
			Point2D_F64 last = pathLocations.get(0);
			for(int i = 1; i< pathLocations.size(); ++i){
				Point2D_F64 next = pathLocations.get(i);
				g2.drawLine((int)last.x, (int)last.y, (int)next.x, (int)next.y);
				last = next;
			}
		}


		// update gui
		gui.repaint();
	}


	public void displayGui(){
		ShowImages.showWindow(gui, "Example Mosaic", true);
	}

	public void saveMosiacToDisc(String optionalName){
		String filename = "mosiacVideo " + numFrames +" "+ optionalName+".png";
		savePlanar_F32(stitch.getStitchedImage(), filename);
	}
	public static void main( String[] args ) {
		// Load an image sequence
		MediaManager media = DefaultMediaManager.INSTANCE;

		String fileName = "resources/vid_parking_drone.mp4";
		SimpleImageSequence<Planar<GrayF32>> video =
				media.openVideo(fileName, ImageType.pl(3, GrayF32.class));

		// create our location detection objects
		MotionFromMosiac motionFromMosiac = new MotionFromMosiac(video.next());
		motionFromMosiac.displayGui(); // display gui window

		int num_frames = 0;
		while(video.hasNext()){
			if(!motionFromMosiac.processFrame(video.next())){
				throw new RuntimeException("failed to process frame "+ num_frames);
			}
			num_frames ++;
			if(num_frames%300 == 0){
				System.out.println("frame"+num_frames);
			}
//            if(num_frames % SKIPPED_FRAMES != 0){
//                video.next();
//                num_frames ++;
//                continue;
//            }
//
//            if(num_frames %SKIPPED_FRAMES*LOCATION_LOG_FREQ == 0) {
//                //TODO get location
//            }
		}

	}

	private static void logLocation(Point2D_F64 location){
		logger.info("Got location (" + location.x + "," + location.y + ")");
	}

	/*Get corners and return center point*/
	private static Point2D_F64 getCenterFromCorners(Quadrilateral_F64 corners){
		Point2D_F64 a = corners.a;
//		Point2D_F64 b = corners.b;
		Point2D_F64 c = corners.c;
//		Point2D_F64 d = corners.d;
		return new Point2D_F64((a.x+c.x)/2, (a.y+c.y)/2);
	}
	public static void saveImage(BufferedImage image, String outputPath) throws IOException {
		File outputFile = new File(outputPath);

		// Get the file extension from the output path
		String formatName = outputPath.substring(outputPath.lastIndexOf('.') + 1);

		// Save the image to the specified file
		ImageIO.write(image, formatName, outputFile);
	}

	/**
	 * Checks to see if the point is near the image border and tells which border to expand
	 */
//	private static Border nearBorderExact( Point2D_F64 p, StitchingFromMotion2D<?, ?> stitch ) {
//		int r = 10;
//		if (p.x < r || p.y < r)
//			return true;
//		if (p.x >= stitch.getStitchedImage().width - r)
//			return true;
//		if (p.y >= stitch.getStitchedImage().height - r)
//			return true;
//
//		return false;
//	}


	/**
	 * Checks to see if the point is near the image border
	 */
	private static boolean nearBorder( Point2D_F64 p, StitchingFromMotion2D<?, ?> stitch ) {
		int r = 10;
		if (p.x < r || p.y < r)
			return true;
		if (p.x >= stitch.getStitchedImage().width - r)
			return true;
		if (p.y >= stitch.getStitchedImage().height - r)
			return true;

		return false;
	}
	private enum Border{
		TOP,
		BOTTOM,
		LEFT,
		RIGHT,
		TOPLEFT,
		TOPRIGHT,
		BOTLEFT,
		BOTRIGHT
	}
	private static Planar<GrayF32> shrinkImage(Planar<GrayF32> image, int shrinkFactor){
		Planar<GrayF32> shrunkImage = new Planar<>(GrayF32.class, image.width / shrinkFactor, image.height / shrinkFactor, image.getNumBands());
		AverageDownSampleOps.down(image, shrunkImage);
		return shrunkImage;
	}
	private static void savePlanar_F32(Planar<GrayF32> image, String name){
		try {
					BufferedImage imageRegularFormat = ConvertBufferedImage.convertTo_F32(image, null, true);
			// Save the image to disk
			saveImage(imageRegularFormat, "resources/output_mosiac/" + name);
			logger.info("saved image: " + name);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private enum TimesEnlarged{
		NEVER,
		ONCE,
		TWICE
	}
}
