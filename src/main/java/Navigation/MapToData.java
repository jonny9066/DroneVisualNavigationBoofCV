package Navigation;

import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.GrayU8;
import org.bridj.util.Pair;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static Navigation.FinalVariables.*;

/*
interface that allows to translate between map slices and their location on the map.
as well as other useful things
 */
public class MapToData {

    public static Pair<Pair<Integer,Integer>,Pair<Integer,Integer>> tileNameToCoordinate(String name){
        String[] coordStr = name.split("_")[2].split("-");
        int[] coordinates = Arrays.stream(coordStr).mapToInt(Integer::parseInt).toArray();
        return new Pair<>(new Pair<>(coordinates[0], coordinates[1]), new Pair<>(coordinates[2], coordinates[3]));
    }
    public static Pair<Integer,Integer> tileNameToRowCol(String name){
        String[] coordStr = name.split("_")[1].split("-");
        int[] rowCol = Arrays.stream(coordStr).mapToInt(Integer::parseInt).toArray();
        return new Pair<>(rowCol[0], rowCol[1]);
    }

    public static boolean tileContainsPoint(String name, Pair<Integer,Integer> point){
        Pair<Pair<Integer,Integer>,Pair<Integer,Integer>> coordinates = tileNameToCoordinate(name);
        return isPointContained(point, coordinates.getFirst(), coordinates.getSecond());
    }
    public static boolean tileIsEqualOrNear(String queryTile, String otherTile){
        if(queryTile == otherTile) return true;

        Pair<Integer,Integer> rowCol = tileNameToRowCol(otherTile);
        int row1 = rowCol.getFirst();
        int col1 = rowCol.getSecond();
        rowCol = tileNameToRowCol(queryTile);
        int row2 = rowCol.getFirst();
        int col2 = rowCol.getSecond();
        // square is close if it's one of 8 squares for which one of the following holds
        // going clockwise from top left
        return ((row1 + 1 == row2) && (col1 + 1 == col2)) || ((row1  == row2) && (col1 + 1  == col2)) ||
                ((row1 - 1 == row2) && (col1 + 1 == col2)) ||((row1  == row2) && (col1 + 1 == col2)) ||
                ((row1  == row2) && (col1 - 1 == col2)) ||((row1 + 1 == row2) && (col1 - 1 == col2)) ||
                ((row1 == row2) && (col1 - 1 == col2)) ||((row1 - 1 == row2) && (col1 - 1 == col2));
    }

    private static boolean isPointContained(Pair<Integer, Integer> point, Pair<Integer, Integer> a, Pair<Integer, Integer> b) {
        int minX = Math.min(a.getKey(), b.getKey());
        int maxX = Math.max(a.getKey(), b.getKey());
        int minY = Math.min(a.getValue(), b.getValue());
        int maxY = Math.max(a.getValue(), b.getValue());

        int pointX = point.getKey();
        int pointY = point.getValue();

        return pointX >= minX && pointX <= maxX && pointY >= minY && pointY <= maxY;
    }
    /*
    map is split into n*k tiles. row, column correspond to n,k.
    two points represent square's top left and bottom right corners.
     */
    public static String tileCoordinatesToName(int row, int col, int startX, int startY, int endX, int endY, String fileType){
        return "tile_"+ row + "-"+ col + "_" + startX + "-" + startY + "-" + endX + "-" + endY + fileType;
    }
    public static void splitMapAndSave(int numTiles){
        BufferedImage bufferedImage = UtilImageIO.loadImageNotNull(MAP_LOCATION);/* Load or obtain the image */;

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
                String tileName = MapToData.tileCoordinatesToName(x , y ,startX, startY, endX, endY, TYPE_IMAGE_TRAIN);

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
}
