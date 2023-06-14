package Navigation;

import boofcv.abst.scene.SceneRecognition;
import boofcv.gui.image.ImageGridPanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.GrayU8;
import org.apache.commons.io.FilenameUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import Navigation.MapToData;

import javax.imageio.ImageIO;

import static Navigation.FinalVariables.*;

/*
A class that shows a map, and draws rectangles on it

 */
public class MapGui {

    ImageGridPanel gui;// gui for displaying map with red square for match
    BufferedImage mapImage;

    public MapGui(){
        // create gui to display map and later squares of matches on it
        gui = new ImageGridPanel(1, 1);
        mapImage = UtilImageIO.loadImageNotNull(MAP_LOCATION);
        gui.setImage(0, 0, mapImage);
        gui.setPreferredSize(new Dimension(mapImage.getWidth(), mapImage.getHeight()));
    }

    public void clearGui(){
        // clear map from previous drawings by replacing image with drawings with fresh image
        gui.setImage(0,0, new BufferedImage(mapImage.getColorModel(), mapImage.copyData(null),
                mapImage.isAlphaPremultiplied(), null));
    }
    public void updateGui(SceneRecognition.Match match, Color color){
        // get map pixel coordinates from filename of match
        String bestMatchDir = match.id;
        String name = FilenameUtils.getBaseName(new File(bestMatchDir).getName());
        var pointPair = MapToData.tileNameToCoordinate(name);
//        System.out.println("got pixel coordinates: " +Arrays.toString(coordinates));
        int tlx = pointPair.getFirst().getFirst(); int tly = pointPair.getFirst().getSecond();
        int brx = pointPair.getFirst().getSecond(); int bry = pointPair.getSecond().getSecond();

        // draw square over map
        // draw a red quadrilateral around the current frame in the mosaic
        Graphics2D g2 = gui.getImage(0, 0).createGraphics();
        g2.setColor(color);
        g2.drawLine(tlx, tly, brx, tly); // top line
        g2.drawLine(tlx,bry,brx,bry); // bottom line
        g2.drawLine(tlx,tly, tlx, bry); // left line
        g2.drawLine(brx,tly,brx,bry); // right line
        gui.repaint();

    }
    public void display(){
        ShowImages.showWindow(gui, "match on map", true);
    }


}
