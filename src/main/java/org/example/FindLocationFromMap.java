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
import georegression.struct.point.Point2D_F64;
import org.apache.commons.io.FilenameUtils;
import org.ddogleg.struct.DogArray;

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
public class FindLocationFromMap {
	private static final int NUM_TEST_IMAGES = 16;
	public static void main( String[] args ) {
//		String imagePath = "resources/example/recognition/scene"; //UtilIO.pathExample("recognition/scene");
		String imageTrainPath = "resources/for_scene/split_train";
		String imageTestPath = "resources/for_scene/split_test";
		List<String> imagesTrain = UtilIO.listByPrefix(imageTrainPath, null, ".png");
		List<String> imagesTest = UtilIO.listByPrefix(imageTestPath, null, ".png");
		Collections.sort(imagesTrain);
		Collections.sort(imagesTest);

		SceneRecognition<GrayU8> recognizer;

		// Except for real-time applications or when there are more than a few hundred images, you might want to
		// just learn the dictionary from scratch
		var saveDirectory = new File("example_recognition");

		// Tell it to process gray U8 images
		ImageType<GrayU8> imageType = ImageType.SB_U8;

		// Used to look up images one at a time from various sources. In this case a list of images.
		var imageTrainIterator = new ImageFileListIterator<>(imagesTrain, imageType);
		var imageTestIterator = new ImageFileListIterator<>(imagesTest, imageType);

		if (false) {
			// Set the line above to true and it will download a pre-built model. Useful when you have a lot of images
			// or simply want to skip the learning step
			System.out.println("Downloading pre-built model");
			recognizer = RecognitionIO.downloadDefaultSceneRecognition(new File("downloaded_models"), imageType);
			recognizer.setVerbose(System.out, BoofMiscOps.hashSet(BoofVerbose.RECURSIVE));
		}

		else if (saveDirectory.exists()) {
			System.out.println("Loading previously generated model");
			recognizer = RecognitionIO.loadFeatureToScene(saveDirectory, imageType);
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

		// See if the recognition algorithm already has images loaded in to it
		if (recognizer.getImageIds(null).isEmpty()) {
			// Add images to the database
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

		var gui = new ListDisplayPanel();
		var gui2 = new ImageGridPanel(1, 1);// yoni: another gui for drawing image

		// set image of whole map in second column
		BufferedImage wholeMap = UtilImageIO.loadImageNotNull("resources/for_scene/map_scene.jpg");
		Planar<GrayF32> mapFmt =
				ConvertBufferedImage.convertFromPlanar(wholeMap, null, true, GrayF32.class);
		gui2.setImage(0, 0, wholeMap);
		gui2.setPreferredSize(new Dimension(mapFmt.width, mapFmt.height));

		//yoni: sample random test image
		Random random = new Random();

		int queryInd = random.nextInt(imagesTest.size());
		// Specifies which image it will try to look up. In the example, related images are in sets of 3.
//		int queryImage = 9;

		// Add the target which the other images are being matched against
		gui.addImage(UtilImageIO.loadImageNotNull(imagesTest.get(queryInd)), "Query "+ FilenameUtils.getBaseName(imagesTest.get(queryInd)), ScaleOptions.ALL);

		// Look up images
		var matches = new DogArray<>(SceneRecognition.Match::new);
		recognizer.query(imageTestIterator.loadImage(queryInd),/* filter */ ( id ) -> true,/* limit */ 5, matches);
		for (int i = 0; i < matches.size; i++) {
			String file = matches.get(i).id;
			double error = matches.get(i).error;
			BufferedImage image = UtilImageIO.loadImageNotNull(file);
			String name = FilenameUtils.getBaseName(new File(file).getName());
			gui.addImage(image, String.format("%20s Error %6.3f", name, error), ScaleOptions.ALL);
		}

		// get pixel coordinates of best match on whole map
		String bestMatch = matches.get(0).id;
		String name = FilenameUtils.getBaseName(new File(bestMatch).getName());
		String[] coordStr = name.split("_")[1].split("-");
		int[] coordinates = Arrays.stream(coordStr).mapToInt(Integer::parseInt).toArray();
		System.out.println("got pixel coordinates: " +Arrays.toString(coordinates));
		int tlx = coordinates[0]; int tly = coordinates[1];
		int brx = coordinates[2]; int bry = coordinates[3];
		// draw square over map
		// draw a red quadrilateral around the current frame in the mosaic
		Graphics2D g2 = gui2.getImage(0, 0).createGraphics();
		g2.setColor(Color.RED);
		g2.drawLine(tlx, tly, brx, tly); // top line
		g2.drawLine(tlx,bry,brx,bry); // bottom line
		g2.drawLine(tlx,tly, tlx, bry); // left line
		g2.drawLine(brx,tly,brx,bry); // right line
//		gui.repaint();
		ShowImages.showWindow(gui2, "match on map", true);

		System.out.println("Train Images Num = " + imagesTrain.size());
		System.out.println(imagesTest.get(queryInd) + " -> " + matches.get(0).id + " matches.size=" + matches.size);

		ShowImages.showWindow(gui, "Similar Images by Features", true);
	}
}
