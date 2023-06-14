package Navigation;

import boofcv.gui.image.ImageGridPanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.Planar;

import java.awt.*;
import java.awt.image.BufferedImage;

public class VideoGui {
    ImageGridPanel gui;
    public VideoGui(Planar<GrayF32> frame){
        // Create the GUI for displaying the results + input image
        gui = new ImageGridPanel(1, 1);
        // (0,0) for input, (0,1) for mosiac
        gui.setImage(0, 0, new BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB));
        gui.setPreferredSize(new Dimension(frame.width, frame.height));
    }
    public void update(BufferedImage frame){
        gui.setImage(0, 0, frame);

    }
    public void update(Planar<GrayF32> frame){
        // clear map from previous drawings by replacing image with drawings with fresh image
//        gui.setImage(0,0, ConvertBufferedImage.convertTo(frame, null, true));
        BufferedImage bframe = ConvertBufferedImage.convertTo(frame,null, true);
        update(bframe);
    }
    public void display(){
        ShowImages.showWindow(gui, "vid", true);
    }
}
