package org.simbrain.world.odorworld.actions;

import org.simbrain.util.SFileChooser;
import org.simbrain.world.odorworld.OdorWorldPanel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;

public class LoadTileMapAction extends AbstractAction {
    /**
     * Plot GUI component.
     */
    private final OdorWorldPanel component;

    /**
     * Construct a show prefs action.
     *
     * @param component parent component
     */
    public LoadTileMapAction(final OdorWorldPanel component) {
        super("Load Tile Map...");
        if (component == null) {
            throw new IllegalArgumentException("Desktop component must not be null");
        }
        this.component = component;
    }

    @Override
    public void actionPerformed(final ActionEvent event) {
        SFileChooser chooser = new SFileChooser(".", "Load vectors");
        File theFile = chooser.showOpenDialog();
        if (theFile != null) {
            //component.getWorld().setTileMap(new TileMap(theFile));
        }
    }

}