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
    private static final int LOCATION_LOG_FREQ = 25;

    // main class for testing
    public static void main( String[] args ) {
        // Load an image sequence
        MediaManager media = DefaultMediaManager.INSTANCE;

        String fileName = "resources/drone_foot_trim.mp4";
        SimpleImageSequence<Planar<GrayF32>> video =
                media.openVideo(fileName, ImageType.pl(3, GrayF32.class));

        MotionFromMosiac motionFromMosiac = new MotionFromMosiac(video.next());
        motionFromMosiac.displayMosiac(); // display gui window

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
}
