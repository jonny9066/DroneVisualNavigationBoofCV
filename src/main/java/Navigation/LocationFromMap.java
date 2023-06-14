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
import org.bridj.util.Pair;
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
import java.util.stream.Collectors;

import static Navigation.FinalVariables.*;
import static Navigation.MapToData.splitMapAndSave;

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
	private ImageType<GrayU8> imageType = ImageType.SB_U8;

	SceneRecognition<GrayU8> recognizer;// the scene recognition object
	BufferedImage lastQueryImage;



	private void trainAndLoadNewModel(int numSquares){


		// check if we have a database to train from
		Path saveDirModel = Paths.get(MODEL_SAVE_DIRECTORY_GENERIC + numSquares);
		Path saveDirImages = Paths.get(IMAGE_TRAIN_PATH_GENERIC + numSquares);
		if (!Files.exists(saveDirImages)) {
			// if not, use map to generate new one
			splitMapAndSave(numSquares);
		}

		// datasets differentiated by number of squares
		List<String> imagesTrain = UtilIO.listByPrefix(saveDirImages.toString(), null, TYPE_IMAGE_TRAIN);
		Collections.sort(imagesTrain);

		// Tell it to process gray U8 images
		ImageType<GrayU8> imageType = ImageType.SB_U8;

		// Used to look up images one at a time from various sources. In this case a list of images.
		var imageTrainIterator =
				new ImageFileListIterator<>(imagesTrain, imageType);
		if(false){
			// Set the line above to true and it will download a pre-built model. Useful when you have a lot of images
			// or simply want to skip the learning step
			// make sure to delete saved model from disc before!
			System.out.println("Downloading pre-built model");
			recognizer = RecognitionIO.downloadDefaultSceneRecognition(new File("downloaded_models"), imageType);
			recognizer.setVerbose(System.out, BoofMiscOps.hashSet(BoofVerbose.RECURSIVE));
		}
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
		}
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
				(WrapFeatureToSceneRecognition<GrayU8, ?>)recognizer, new File( saveDirModel.toString())), "");
	}

	public LocationFromMap(int dataset){
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


	}

	public DogArray<SceneRecognition.Match> getMatchesArray(Planar<GrayF32> queryF32, Pair<Integer,Integer> nearLocation){
		BufferedImage query = ConvertBufferedImage.convertTo(queryF32, null, true);
		return getMatchesArray(query, nearLocation);
	}

	public DogArray<SceneRecognition.Match> getMatchesArray(BufferedImage queryBufferedImage, Pair<Integer,Integer> nearLocation){
		lastQueryImage = queryBufferedImage;
		GrayU8 queryImage = ConvertBufferedImage.convertFrom(queryBufferedImage, (GrayU8) null);

		// find name of square that contains given location
		String containingSquareName = recognizer.getImageIds(null).stream().
				filter((name) -> MapToData.tileContainsPoint(name, nearLocation)).collect(Collectors.toList()).get(0);
		// Look up images
		DogArray<SceneRecognition.Match> matches = new DogArray<>(SceneRecognition.Match::new);

//		recognizer.query(imageTestIterator.loadImage(queryInd),/* filter */ ( id ) -> true,/* limit */ 5, matches);

		// query only on the squares that are near containingSquare
		recognizer.query(queryImage, /* filter */
				( name ) -> MapToData.tileIsEqualOrNear(containingSquareName, name),/* limit */ 5, matches);

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





	// only for test
	public static void main( String[] args ) {
//		splitMapAndSave("resources/for_scene/frame_5104.jpg", 4);
//		splitMapAndSave(6);
//		splitMapAndSave("resources/for_scene/frame_5104.jpg", 7);

//
//		int numTiles = 7;
//		String mapLocation = "resources/for_scene/frame_5104.jpg";
//		// create a scene matching object
//		LocationFromMap locationFromMap = new LocationFromMap(numTiles, mapLocation);
////		locationFromMap.displayGui();
//
//		// take an image from some test directory
//		List<String> imagesTest = UtilIO.listByPrefix(IMAGE_TRAIN_PATH_GENERIC +numTiles, null, ".png");
//		Collections.sort(imagesTest);
//		// sample random test image
//		Random random = new Random();
//		int queryInd = random.nextInt(imagesTest.size());
//
//		// test on some random image from training set
//		String queryImageDir = imagesTest.get(queryInd);
//		BufferedImage queryImage = UtilImageIO.loadImageNotNull(queryImageDir);
//		var matches = locationFromMap.getMatchesArray(queryImage);
//
//		MapGui mapGui = new MapGui(mapLocation);
////		locationFromMap.displayClosestMatches(matches ,FilenameUtils.getBaseName(queryImageDir));
//		mapGui.updateGui(matches.get(0), Color.RED);
//		mapGui.display();
//		//display second closest match
////		locationFromMap.displayMatchOnMap(matches.get(1));
//
//		try {
//			// Pause the program for 2 seconds (2000 milliseconds)
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
//
//		// try another random image from train
//		queryInd = random.nextInt(imagesTest.size());
//		queryImageDir = imagesTest.get(queryInd);
//		queryImage = UtilImageIO.loadImageNotNull(queryImageDir);
//		matches = locationFromMap.getMatchesArray(queryImage);
//
//		locationFromMap.displayClosestMatches(matches ,FilenameUtils.getBaseName(queryImageDir));
//		mapGui.updateGui(matches.get(0), Color.BLUE);
//


//		System.out.println("Train Images Num = " + imagesTrain.size());
//		System.out.println(imagesTest.get(queryInd) + " -> " + matches.get(0).id + " matches.size=" + matches.size);



		// Specifies which image it will try to look up. In the example, related images are in sets of 3.
//		int queryImage = 9;



	}
}
