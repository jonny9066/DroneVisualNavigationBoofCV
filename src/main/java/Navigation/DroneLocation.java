package Navigation;

import boofcv.gui.image.ImageGridPanel;
import boofcv.io.MediaManager;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;


import java.awt.*;
import java.awt.image.BufferedImage;

import static Navigation.ImageUtils.shrinkImage;

/*
Takes video footage, height and direction.
Uses mosiacing and scene recognition algorithms to estimate GPS location.
@yoni
 */
public class DroneLocation {
    private static final int SKIPPED_FRAMES = 1;
    private static final int SAVE_EVERY_NUM_STITCHES = 50;

    private static final int LOCATE_ON_MAP_EVERY_NUM_FRAMES = 90;
    final static String WHOLE_MAP_LOCATION =  "resources/for_scene/frame_5104.jpg";
    private static final int LOCATION_LOG_FREQ = 25;

    // main class for testing
    public static void main( String[] args ) {
        // Load an image sequence
        MediaManager media = DefaultMediaManager.INSTANCE;

        String fileName = "resources/vid_parking_drone.mp4";
        SimpleImageSequence<Planar<GrayF32>> video =
                media.openVideo(fileName, ImageType.pl(3, GrayF32.class));


        Planar<GrayF32> firstframe = shrinkImage(video.next(), 2);
        // create our location detection objects
//        MotionFromMosiac motionFromMosiac = new MotionFromMosiac(firstframe); // init on first frame
        // loads a map with a database, make sure corresponds to video
        // load several databases with different scales
        LocationFromMap locationFromMap1 = new LocationFromMap(5, WHOLE_MAP_LOCATION);
        LocationFromMap locationFromMap2 = new LocationFromMap(6, WHOLE_MAP_LOCATION);
        LocationFromMap locationFromMap3 = new LocationFromMap(7, WHOLE_MAP_LOCATION);
        LocationFromMap locationFromMap4 = new LocationFromMap(8, WHOLE_MAP_LOCATION);
        // display gui windows for both mathods for debugging
        MapGui mapGui = new MapGui(WHOLE_MAP_LOCATION);// gui for displaying map with red square for match
        VideoGui videoGui = new VideoGui(firstframe);
        videoGui.display();
        mapGui.display();
//        motionFromMosiac.displayGui();

        // traverse whole video
        int num_frames = 0;
        while(video.hasNext()){
            Planar<GrayF32> frame = shrinkImage(video.next(), 2);
            videoGui.update(ConvertBufferedImage.convertTo(frame,null, true));
            // update mosiac
//            if(!motionFromMosiac.processFrame(frame)){
//                throw new RuntimeException("failed to process frame "+ num_frames);
//            }
            if(num_frames%300 == 0){
                System.out.println("frame"+num_frames);
            }
            if(num_frames%LOCATE_ON_MAP_EVERY_NUM_FRAMES == 0){
                System.out.println("locating frame "+num_frames + "on map");
                // get matches and draw rectange over first in map
                var matches1 = locationFromMap1.getMatchesArray(frame);
                var matches2 = locationFromMap2.getMatchesArray(frame);
                var matches3 = locationFromMap3.getMatchesArray(frame);
                var matches4 = locationFromMap4.getMatchesArray(frame);
                mapGui.clearGui();
                mapGui.updateGui(matches1.get(0), Color.BLUE);
                mapGui.updateGui(matches2.get(0), Color.RED);
                mapGui.updateGui(matches3.get(0), Color.GREEN);
                mapGui.updateGui(matches4.get(0), Color.yellow);
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
