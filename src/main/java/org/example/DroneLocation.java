package org.example;

import boofcv.io.MediaManager;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;

/*
Takes video footage, height and direction.
Uses mosiacing and scene recognition algorithms to estimate GPS location.
@yoni
 */
public class DroneLocation {
    private static final int SKIPPED_FRAMES = 1;
    private static final int SAVE_EVERY_NUM_STITCHES = 50;

    private static final int LOCATE_ON_MAP_EVERY_NUM_FRAMES = 90;
    private static final int LOCATION_LOG_FREQ = 25;

    // main class for testing
    public static void main( String[] args ) {
        // Load an image sequence
        MediaManager media = DefaultMediaManager.INSTANCE;

        String fileName = "resources/drone_foot_trim.mp4";
        SimpleImageSequence<Planar<GrayF32>> video =
                media.openVideo(fileName, ImageType.pl(3, GrayF32.class));

        // create our location detection objects
        MotionFromMosiac motionFromMosiac = new MotionFromMosiac(video.next()); // init on first frame
        // loads a map with a database, make sure corresponds to video
        LocationFromMap locationFromMap = new LocationFromMap();
        // display gui windows for both mathods for debugging
        motionFromMosiac.displayGui();
        locationFromMap.displayGui();

        // traverse whole video
        int num_frames = 0;
        while(video.hasNext()){
            Planar<GrayF32> frame = video.next();
            // update mosiac
            if(!motionFromMosiac.processFrame(frame)){
                throw new RuntimeException("failed to process frame "+ num_frames);
            }
            if(num_frames%300 == 0){
                System.out.println("frame"+num_frames);
            }
            if(num_frames%LOCATE_ON_MAP_EVERY_NUM_FRAMES == 0){
                System.out.println("locating frame "+num_frames + "on map");
                // get matches and draw rectange over first in map
                var matches = locationFromMap.getMatchesArray(frame);
                locationFromMap.updateGui(matches.get(0));
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

            num_frames ++;
        }


    }
}
