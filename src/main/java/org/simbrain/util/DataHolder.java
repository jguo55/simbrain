package org.simbrain.util;

/**
 * Holds data used in conjunction with update rules which work by passing a rule over an array of vavlues and
 * updating them. Implementing classes hold custom data (usually arrays) specific to a rule.
 * Example: a data holder for a spiking data holds an array of spike times.
 */
public interface DataHolder {

    // TODO: Move these and make fields private as the design stabilizes

    class BiasedDataHolder implements DataHolder {

        public double[] biases;

        public BiasedDataHolder(int size) {
            biases = new double[size];
        }
    }

    class SpikingDataHolder implements DataHolder {

        public boolean[] spikes;
        public double[] lastSpikeTimes;

        public SpikingDataHolder(int size) {
            spikes = new boolean[size];
            lastSpikeTimes = new double[size];
        }
    }
}
