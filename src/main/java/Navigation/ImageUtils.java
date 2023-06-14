package Navigation;

import boofcv.alg.filter.misc.AverageDownSampleOps;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.Planar;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageUtils {

    public static Planar<GrayF32> shrinkImage(Planar<GrayF32> image, int shrinkFactor){
        Planar<GrayF32> shrunkImage = new Planar<>(GrayF32.class, image.width / shrinkFactor, image.height / shrinkFactor, image.getNumBands());
        AverageDownSampleOps.down(image, shrunkImage);
        return shrunkImage;
    }
    public static Planar<GrayF32> squareImage(Planar<GrayF32> image){
        // take smallest of height/width
        int height = image.height; int width = image.width;
        // coordinates of two points that represent square
        int startX = 0; int startY = 0;
        int endX = 0; int endY = 0;
//        int smaller = Math.min(image.height, image.width);

        // if width smaller, then square will have size width
        if(height>width){
            endX += width; endY += width;
            // translate the Y coordinate to move to center
            startY += width/2; endY += width/2;
        }
        else {
            endX += height;
            endY += height;
            // translate the Y coordinate to move to center
            startX += width / 2;
            endX += width / 2;
        }
        return image.subimage(startX, startY, endX, endY, null);
    }
    public static void savePlanar_F32(Planar<GrayF32> image, String name){
        try {
            BufferedImage imageRegularFormat = ConvertBufferedImage.convertTo_F32(image, null, true);
            // Save the image to disk
            saveImage(imageRegularFormat, "resources/output_mosiac/" + name);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void saveImage(BufferedImage image, String outputPath) throws IOException {
        File outputFile = new File(outputPath);

        // Get the file extension from the output path
        String formatName = outputPath.substring(outputPath.lastIndexOf('.') + 1);

        // Save the image to the specified file
        ImageIO.write(image, formatName, outputFile);
    }


}
