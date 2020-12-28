package org.simbrain.world.imageworld;

/**
 * The "Pixel display" component which allows data to be received from Neural networks
 * and other Simbrain components via couplings and rendered in a Buffered "pixel" image.
 */
public class PixelConsumer extends ImageWorld {

    /**
     * The BufferedImage that displays whatever pixel pattern is currently being
     * received from other Simbrain components via couplings.
     */
    private PixelConsumerSource emitterMatrix;

    /**
     * Construct the image world.
     *
     */
    public PixelConsumer() {
        super();
        getImagePanel().setShowGridLines(true);
        emitterMatrix = new PixelConsumerSource();
        initializeDefaultSensorMatrices();
    }

    // public void clearImage() {
    //     emitterMatrix.clear();
    //     emitterMatrix.emitImage();
    // }

    @Override
    public boolean getUseColorEmitter() {
        return emitterMatrix.isUsingRGBColor();
    }

    public void setUseColorEmitter(boolean value) {
        emitterMatrix.setUsingRGBColor(value);
    }

    public int getEmitterWidth() {
        return emitterMatrix.getWidth();
    }

    public int getEmitterHeight() {
        return emitterMatrix.getHeight();
    }

    /**
     * Set the size of the emitter matrix.
     */
    public void resizeEmitterMatrix(int width, int height) {
        emitterMatrix.setSize(width, height);
    }

    /**
     * Update the emitter matrix image.
     */
    public void update() {
        emitterMatrix.emitImage();
    }

    @Override
    public ImageSourceAdapter getImageSource() {
        return emitterMatrix;
    }
}