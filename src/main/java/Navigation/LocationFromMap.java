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

package Navigation;

import boofcv.BoofVerbose;
import boofcv.abst.scene.ConfigFeatureToSceneRecognition;
import boofcv.abst.scene.SceneRecognition;
import boofcv.abst.scene.WrapFeatureToSceneRecognition;
import boofcv.factory.scene.FactorySceneRecognition;
import boofcv.gui.ListDisplayPanel;
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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

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
//	private File saveDirectory;
	BufferedImage lastQueryImage;
	String mapLocation;
	BufferedImage wholeMapImage;
	final static String imagesTrainSuffix = ".png";

	// two directories: for split images, for model.
	// Different models and split images have different index in end
	final static String MODEL_SAVE_DIRECTORY_GENERIC = "resources/for_scene/pretrained";// should append corresponding number
	final static String IMAGE_TRAIN_PATH_GENERIC = "resources/for_scene/trainingImages";



//	public getLocationFromImage(){
//
//	}

	/*

	 */
	private void trainAndLoadNewModel(int numSquares){


		// check if we have a database to train from
		Path saveDir = Paths.get(MODEL_SAVE_DIRECTORY_GENERIC + numSquares);
		if (!Files.exists(saveDir)) {
			// if not, use map to generate new one
			splitMapAndSave(numSquares);
		}

		// datasets differentiated by number of squares
		List<String> imagesTrain = UtilIO.listByPrefix(IMAGE_TRAIN_PATH_GENERIC+numSquares, null, imagesTrainSuffix);
		Collections.sort(imagesTrain);

		// Tell it to process gray U8 images
		ImageType<GrayU8> imageType = ImageType.SB_U8;

		// Used to look up images one at a time from various sources. In this case a list of images.
		var imageTrainIterator =
				new ImageFileListIterator<>(imagesTrain, imageType);

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
				(WrapFeatureToSceneRecognition<GrayU8, ?>)recognizer, new File( saveDir.toString())), "");
	}

	public LocationFromMap(int dataset, String mapLocation){
		this.mapLocation = mapLocation;
		// Except for real-time applications or when there are more than a few hundred images, you might want to
		// just learn the dictionary from scratch
		var modelSaveDirectory = new File(MODEL_SAVE_DIRECTORY_GENERIC + dataset);

		// load pre-trained model if exists
		if (modelSaveDirectory.exists()) {
			System.out.println("Loading previously generated model");
			recognizer = RecognitionIO.loadFeatureToScene(modelSaveDirectory, imageType);
			recognizer.setVerbose(System.out, BoofMiscOps.hashSet(BoofVerbose.RECURSIVE));
		}
		// then train a new model based on map
		else {
			trainAndLoadNewModel(dataset);
		}



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


	/*
	Uses labels of dataset to find pixel location in map. Then draws red recatnge around it.
	@match some match inside database of this that corresponds to map.

	 */

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


	public void splitMapAndSave(int numTiles){
		BufferedImage bufferedImage = UtilImageIO.loadImageNotNull(mapLocation);/* Load or obtain the image */;

		// Convert the image to BoofCV GrayU8 format
		GrayU8 image = ConvertBufferedImage.convertFrom(bufferedImage, (GrayU8) null);

		// tile size determined by number of squares that fit in minimum of width/hights
		int minWidthHeight = Math.min(bufferedImage.getWidth(),bufferedImage.getHeight());
		// Specify the desired size of each square tile
		int tileSize = minWidthHeight/numTiles; // Adjust this value according to your requirements

		// Calculate the number of tiles horizontally and vertically
		int numTilesX = image.getWidth() / tileSize;
		int numTilesY = image.getHeight() / tileSize;

		// create save folder if doesn't exist
		Path saveDir = Paths.get(IMAGE_TRAIN_PATH_GENERIC + numTiles);
		if (!Files.exists(saveDir)) {
			try {
				Files.createDirectories(saveDir);
				System.out.println("Directory created: " + saveDir);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// Extract each square tile from the image
		for (int y = 0; y < numTilesY; y++) {
			for (int x = 0; x < numTilesX; x++) {
				// Define the region of interest (ROI) for the current tile
				int startX = x * tileSize;
				int startY = y * tileSize;
				int endX = startX + tileSize;
				int endY = startY + tileSize;

				// Extract the tile from the image using the ROI
				GrayU8 tile = image.subimage(startX, startY, endX, endY, null);
//				String tileName = "square_{left}-{upper}-{right}-{lower}" + imagesTrainSuffix;
				// important ot have _ and - in right places. name unimportant
				String tileName = "tile_" + startX + "-" + startY + "-" + endX + "-" + endY + imagesTrainSuffix;

				// save image in path
				try {
					BufferedImage tileImage = ConvertBufferedImage.convertTo(tile, null);
					// append image name to base path
					File outputFile = new File(saveDir.resolve(tileName).toString());
					ImageIO.write(tileImage, "PNG", outputFile);
				} catch (IOException e) {
					e.printStackTrace();
					}
				}
			}

	}



//	private enum NumTiles{
//		5,6,7
//	}

	// omly for test
	public static void main( String[] args ) {
//		splitMapAndSave("resources/for_scene/frame_5104.jpg", 4);
//		splitMapAndSave(6);
//		splitMapAndSave("resources/for_scene/frame_5104.jpg", 7);


		int numTiles = 7;
		String mapLocation = "resources/for_scene/frame_5104.jpg";
		// create a scene matching object
		LocationFromMap locationFromMap = new LocationFromMap(numTiles, mapLocation);
//		locationFromMap.displayGui();

		// take an image from some test directory
		List<String> imagesTest = UtilIO.listByPrefix(IMAGE_TRAIN_PATH_GENERIC +numTiles, null, ".png");
		Collections.sort(imagesTest);
		// sample random test image
		Random random = new Random();
		int queryInd = random.nextInt(imagesTest.size());

		// test on some random image from training set
		String queryImageDir = imagesTest.get(queryInd);
		BufferedImage queryImage = UtilImageIO.loadImageNotNull(queryImageDir);
		var matches = locationFromMap.getMatchesArray(queryImage);

		MapGui mapGui = new MapGui(mapLocation);
//		locationFromMap.displayClosestMatches(matches ,FilenameUtils.getBaseName(queryImageDir));
		mapGui.updateGui(matches.get(0), Color.RED);
		mapGui.display();
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
		mapGui.updateGui(matches.get(0), Color.BLUE);



//		System.out.println("Train Images Num = " + imagesTrain.size());
//		System.out.println(imagesTest.get(queryInd) + " -> " + matches.get(0).id + " matches.size=" + matches.size);



		// Specifies which image it will try to look up. In the example, related images are in sets of 3.
//		int queryImage = 9;



	}
}
