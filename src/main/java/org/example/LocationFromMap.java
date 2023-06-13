/*
 * Copyright (c) 2022, Peter Abeles. All Rights Reserved.
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

import boofcv.BoofVerbose;
import boofcv.abst.scene.ConfigFeatureToSceneRecognition;
import boofcv.abst.scene.SceneRecognition;
import boofcv.abst.scene.WrapFeatureToSceneRecognition;
import boofcv.factory.scene.FactorySceneRecognition;
import boofcv.gui.ListDisplayPanel;
import boofcv.gui.image.ImageGridPanel;
import boofcv.gui.image.ScaleOptions;
import boofcv.gui.image.ShowImages;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.ImageFileListIterator;
import boofcv.io.image.UtilImageIO;
import boofcv.io.recognition.RecognitionIO;
import boofcv.misc.BoofMiscOps;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;
import org.apache.commons.io.FilenameUtils;
import org.ddogleg.struct.DogArray;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * In BoofCV, scene recognition [1] refers to the problem of trying to identify photos of the same scene (not a single
 * object in the image) from different perspectives. This is of interest if you want to organize your photos, find
 * images to create a mosaic from, or cluster photos for 3D reconstruction. Solutions to this problem tend to
 * emphasise fast accurate retrieval from large databases.
 *
 * [1] As far as I can tell there is no universal terminology for this specific sub problem. It is sometimes lumped
 * under Content Based Image Retrieval (CBIR), which is a very generic term.
 *
 * @author Peter Abeles
 */
public class LocationFromMap {
	private static final int NUM_TEST_IMAGES = 16;
	private ImageType<GrayU8> imageType = ImageType.SB_U8;

	SceneRecognition<GrayU8> recognizer;// the scene recognition object
	private File saveDirectory;
	ImageGridPanel guiMap;// gui for displaying map with red square for match
	BufferedImage lastQueryImage;
	final String wholeMapLocation =  "frame_5104.jpg";
	BufferedImage wholeMapImage;
	JFrame jFrameContainer;


//	public getLocationFromImage(){
//
//	}


	public LocationFromMap(){
		String imageTrainPath = "resources/for_scene/split_train";
		List<String> imagesTrain = UtilIO.listByPrefix(imageTrainPath, null, ".png");
		Collections.sort(imagesTrain);


		// Except for real-time applications or when there are more than a few hundred images, you might want to
		// just learn the dictionary from scratch
		File saveDirectory = new File("location_from_map_database");

		// Tell it to process gray U8 images
		ImageType<GrayU8> imageType = ImageType.SB_U8;

		// Used to look up images one at a time from various sources. In this case a list of images.
		var imageTrainIterator =
				new ImageFileListIterator<>(imagesTrain, imageType);


		// load pre-trained model if exists
		if (saveDirectory.exists()) {
			System.out.println("Loading previously generated model");
			recognizer = RecognitionIO.loadFeatureToScene(saveDirectory, imageType);
			recognizer.setVerbose(System.out, BoofMiscOps.hashSet(BoofVerbose.RECURSIVE));
		}
		// else, train a new model based on provided images
		else {
			// If many applications, learning a new model is a small fraction of the compute time and since its
			// fit to the images it will be more accurate than a generic pre-built model
			System.out.println("Creating a new model");
			var config = new ConfigFeatureToSceneRecognition();
			// Use a hierarchical vocabulary tree, which is very fast and also one of the more accurate approaches
			config.typeRecognize = ConfigFeatureToSceneRecognition.Type.NISTER_2006;
			config.recognizeNister2006.learningMinimumPointsForChildren.setFixed(20);

			recognizer = FactorySceneRecognition.createFeatureToScene(config, imageType);
			// This will print out a lot of debugging information to stdout
			recognizer.setVerbose(System.out, BoofMiscOps.hashSet(BoofVerbose.RECURSIVE));

			// Learn the model from the initial set of images
			recognizer.learnModel(imageTrainIterator);

			// the model also requires images to be in the database to match against them

			// Add images to the database
			// new model should be empty
			if(!recognizer.getImageIds(null).isEmpty()) throw new RuntimeException("expected new model to be empty");
			System.out.println("Adding images to the database");
			imageTrainIterator.reset();
			while (imageTrainIterator.hasNext()) {
				GrayU8 image = imageTrainIterator.next();
				recognizer.addImage(imagesTrain.get(imageTrainIterator.getIndex()), image);
			}

			// This saves the model with the image database to disk
			System.out.println("Saving model");
			BoofMiscOps.profile(() -> RecognitionIO.saveFeatureToScene(
					(WrapFeatureToSceneRecognition<GrayU8, ?>)recognizer, saveDirectory), "");
		}

		// create gui to display map and later squares of matches on it
		guiMap = new ImageGridPanel(1, 1);
		wholeMapImage = UtilImageIO.loadImageNotNull("resources/for_scene/" + wholeMapLocation);
		guiMap.setImage(0, 0, wholeMapImage);
		guiMap.setPreferredSize(new Dimension(wholeMapImage.getWidth(), wholeMapImage.getHeight()));

//		// Create a JFrame to display the ImageGridPanel
//		jFrameContainer = new JFrame("Image Grid Panel Example");
//		jFrameContainer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//		jFrameContainer.getContentPane().setLayout(new BorderLayout());
//		jFrameContainer.getContentPane().add(guiMap, BorderLayout.CENTER);
//		jFrameContainer.pack();
//		jFrameContainer.setVisible(true);

	}

	public DogArray<SceneRecognition.Match> getMatchesArray(Planar<GrayF32> queryF32){
		BufferedImage query = ConvertBufferedImage.convertTo(queryF32, null, true);
		return getMatchesArray(query);
	}
	public DogArray<SceneRecognition.Match> getMatchesArray(BufferedImage queryBufferedImage){
		lastQueryImage = queryBufferedImage;
		GrayU8 queryImage = ConvertBufferedImage.convertFrom(queryBufferedImage, (GrayU8) null);

		// Look up images
		DogArray<SceneRecognition.Match> matches = new DogArray<>(SceneRecognition.Match::new);

//		recognizer.query(imageTestIterator.loadImage(queryInd),/* filter */ ( id ) -> true,/* limit */ 5, matches);

		recognizer.query(queryImage, /* filter */ ( id ) -> true,/* limit */ 5, matches);

		return  matches;
	}
	/*
	Displays GUI window. Should be run once.
	 */
	public void displayGui(){
		ShowImages.showWindow(guiMap, "match on map", true);
	}

	/*
	Uses labels of dataset to find pixel location in map. Then draws red recatnge around it.
	@match some match inside database of this that corresponds to map.

	 */
	public void updateGui(SceneRecognition.Match match){
		// get map pixel coordinates from filename of match
		String bestMatchDir = match.id;
		String name = FilenameUtils.getBaseName(new File(bestMatchDir).getName());
		String[] coordStr = name.split("_")[1].split("-");
		int[] coordinates = Arrays.stream(coordStr).mapToInt(Integer::parseInt).toArray();
		System.out.println("got pixel coordinates: " +Arrays.toString(coordinates));
		int tlx = coordinates[0]; int tly = coordinates[1];
		int brx = coordinates[2]; int bry = coordinates[3];

		// clear map from previous drawings by replacing image with drawings with fresh image
		guiMap.setImage(0,0, new BufferedImage(wholeMapImage.getColorModel(), wholeMapImage.copyData(null),
																	wholeMapImage.isAlphaPremultiplied(), null));
		// draw square over map
		// draw a red quadrilateral around the current frame in the mosaic
		Graphics2D g2 = guiMap.getImage(0, 0).createGraphics();
		g2.setColor(Color.RED);
		g2.drawLine(tlx, tly, brx, tly); // top line
		g2.drawLine(tlx,bry,brx,bry); // bottom line
		g2.drawLine(tlx,tly, tlx, bry); // left line
		g2.drawLine(brx,tly,brx,bry); // right line
		guiMap.repaint();

	}
	public void displayClosestMatches(DogArray<SceneRecognition.Match> matches, String queryName){
		ListDisplayPanel gui = new ListDisplayPanel();
		// Add the target which the other images are being matched against
		gui.addImage(lastQueryImage, "Query "+ queryName, ScaleOptions.ALL);

		for (int i = 0; i < matches.size; i++) {
			String file = matches.get(i).id;
			double error = matches.get(i).error;
			BufferedImage image = UtilImageIO.loadImageNotNull(file);
			String name = FilenameUtils.getBaseName(new File(file).getName());
			gui.addImage(image, String.format("%20s Error %6.3f", name, error), ScaleOptions.ALL);
		}

		ShowImages.showWindow(gui, "Similar Images by Features", true);
	}



	// omly for test
	public static void main( String[] args ) {
		// create a scene matching object
		LocationFromMap locationFromMap = new LocationFromMap();

		// take an image from some test directory
		String imageTestPath = "resources/for_scene/split_test";
		List<String> imagesTest = UtilIO.listByPrefix(imageTestPath, null, ".png");
		Collections.sort(imagesTest);
		// sample random test image
		Random random = new Random();
		int queryInd = random.nextInt(imagesTest.size());

		// test on some random image from training set
		String queryImageDir = imagesTest.get(queryInd);
		BufferedImage queryImage = UtilImageIO.loadImageNotNull(queryImageDir);
		var matches = locationFromMap.getMatchesArray(queryImage);

		locationFromMap.displayGui();// run once

//		locationFromMap.displayClosestMatches(matches ,FilenameUtils.getBaseName(queryImageDir));
		locationFromMap.updateGui(matches.get(0));
		//display second closest match
//		locationFromMap.displayMatchOnMap(matches.get(1));

		try {
			// Pause the program for 2 seconds (2000 milliseconds)
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// try another random image from train
		queryInd = random.nextInt(imagesTest.size());
		queryImageDir = imagesTest.get(queryInd);
		queryImage = UtilImageIO.loadImageNotNull(queryImageDir);
		matches = locationFromMap.getMatchesArray(queryImage);

		locationFromMap.displayClosestMatches(matches ,FilenameUtils.getBaseName(queryImageDir));
		locationFromMap.updateGui(matches.get(0));

//		System.out.println("Train Images Num = " + imagesTrain.size());
//		System.out.println(imagesTest.get(queryInd) + " -> " + matches.get(0).id + " matches.size=" + matches.size);



		// Specifies which image it will try to look up. In the example, related images are in sets of 3.
//		int queryImage = 9;



	}
}
