/*
 * Part of Simbrain--a java-based neural network kit
 * Copyright (C) 2005,2007 The Authors.  See http://www.simbrain.net/credits
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.simbrain.gauge.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import org.simbrain.util.Utils;

import Jama.Matrix;

import com.Ostermiller.util.CSVParser;
import com.Ostermiller.util.CSVPrinter;

/**
 * <b>Dataset</b> represents a set of n-dimensional points. Both the low and
 * high dimensional data of the current {@link Projector} are instances of this
 * class. Dataset provides methods for working with such sets (e.g. open dataset
 * up, adding points, checking their integrity, finding nearest neighbors of a
 * point, calculating their interpoint distances, etc.). It is assumed that all
 * points in a dataset have the same dimensionality.
 */
public class Dataset {
    /**
     * The data.
     */
    private final ArrayList<double[]> dataset = new ArrayList<double[]>();

    /**
     * Persistent form of data.
     */
    private final ArrayList<String> persistentData = new ArrayList<String>();

    /**
     * Number of dimensions in the dataset.
     */
    private int dimensions;

    /**
     * Matrix of interpoint distances.
     */
    private double[] distances = new double[10240];

    /**
     * for castor?
     */
    public Dataset() {

    }

    /**
     * Default constructor for adding datasets.
     * 
     */
    public Dataset(File file) {
        readData(file);
    }

    /**
     * Creates and instance of Dataset.
     * 
     * @param ndims dimension of dataset
     */
    public Dataset(final int dimensions) {
        this.dimensions = dimensions;
    }

    /**
     * Creates and instance of Dataset.
     * 
     * @param ndims dimension of dataset
     * @param numpoints number of points
     */
    public Dataset(final int dimensions, final int numPoints) {
        this.dimensions = dimensions;

        for (int i = 0; i < numPoints; i++) {
            double[] point = new double[dimensions];
            addPoint(point);
        }
    }

    /**
     * Get a specificed point in the dataset.
     * 
     * @param i index of the point to get
     * 
     * @return the n-dimensional datapoint
     */
    public double[] getPoint(final int i) {
        if (i >= getNumPoints()) {
            System.err.println("Error: requested datapoint outside of dataset range");

            return null;
        }

        return dataset.get(i);
    }

    private int getDistanceEnd() {
        int lastPoint = getNumPoints() - 1;
        return getDistanceIndex(lastPoint) + lastPoint;
    }

    private void ensureDistances() {
        if (getDistanceEnd() > distances.length) {
            int newLength = distances.length * 4;
            double[] newDistances = new double[newLength];
            System.arraycopy(distances, 0, newDistances, 0, distances.length);
            Arrays.fill(newDistances, distances.length, newLength, -1);
            distances = newDistances;
        }
    }

    private void _addPoint(double[] point) {
        dataset.add(point);
        ensureDistances();
    }

    /**
     * Add a new datapoint to the dataset.
     * 
     * @param point A point in the high dimensional space
     * @param tolerance forwarded to isUniquePoint; if -1 then add point
     *            regardless of whether it is unique or not
     * 
     * @return true if point added, false otherwise
     */
    public boolean addPoint(final double[] point, final double tolerance) {
        try {
            checkDimension(point);
            
            if (isUniquePoint(point, tolerance)) {
                _addPoint(point);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Add datapoint without checking whether it is unique or not.
     * 
     * @param point point to be added
     */
    public void addPoint(final double[] point) {
        checkDimension(point);

        _addPoint(point);
    }

    private void _setPoint(int index, double[] point) {
        dataset.set(index, point);
        ensureDistances();
    }

    /**
     * Set a specified point in the dataset.
     * 
     * @param i the point to set
     * @param point the new n-dimensional point
     */
    public void setPoint(final int i, final double[] point) {
        if ((i < 0) || (i >= getNumPoints())) {
            System.err.println("Error: trying to set a datapoint which does not exist");

            return;
        }

        checkDimension(point);

        _setPoint(i, point);
    }

    /**
     * @return the number of points in the dataset
     */
    public int getNumPoints() {
        return dataset.size();
    }

    /**
     * Clear all data, high and low dimensional.
     */
    public void clear() {
        dataset.clear();
    }

    private int getDistanceIndex(int point) {
        switch (point) {
        case 0:
            return -1; /* no start for point 0 */
        case 1:
            return 0;
        default:
            int n = point - 1;
            return ((n * n) + n) / 2;
        }
    }

    /**
     * A must be greater than B
     * 
     * @param pointA
     * @param pointB
     */
    private double calculateDistance(int pointA, int pointB) {
        if (pointA <= pointB) {
            throw new IllegalArgumentException("pointA must be greater than pointB - A: " + pointA
                    + " B: " + pointB);
        }

        int start = getDistanceIndex(pointA);

        double distance = getDistance(dataset.get(pointA), dataset.get(pointB));
        distances[start + pointB] = distance;

        return distance;
    }

    private void calculateDistances(int point) {
        int start = getDistanceIndex(point);

        for (int i = 0; i < point; i++) {
            distances[start + i] = getDistance(dataset.get(point), dataset.get(i));
        }

        int numPoints = getNumPoints();

        for (int i = point + 1; i < numPoints; i++) {
            start = getDistanceIndex(i);
            distances[start + point] = getDistance(dataset.get(point), dataset.get(i));
        }
    }

    private void calculateDistances() {
        for (int point = 0; point < dataset.size(); point++) {
            calculateDistances(point);
        }
    }

    /**
     * Randomize dataset to a value between 0 and upperBound.
     * 
     * @param upperBound highest value to be used
     */
    public void randomize(final int upperBound) {
        for (int i = 0; i < getNumPoints(); i++) {
            double[] point = new double[dimensions];

            for (int j = 0; j < dimensions; j++) {
                point[j] = Math.random() * upperBound;
            }

            setPoint(i, point);
        }

        Arrays.fill(distances, -1);

        calculateDistances();
    }

    /**
     * Get the minimum interpoint distance between points in the dataset.
     * 
     * @return minimum distance between any two points in the low-d dataset
     */
    public double getMinimumDistance() {
        double l = Double.MAX_VALUE;
        int stop = getDistanceEnd();

        calculateDistances();

        for (int i = 0; i < stop; i++) {
            l = Math.min(l, distances[i]);
        }

        return l;
    }

    /**
     * Get the maximimum interpoint distance between points in the dataset.
     * 
     * @return maximum distance between any two points in the low-d dataset
     */
    public double getMaximumDistance() {
        double l = 0;
        int stop = getDistanceEnd();

        calculateDistances();

        for (int i = 0; i < stop; i++) {
            l = Math.max(l, distances[i]);
        }

        return l;
    }

    /**
     * Read in stored dataset file.
     * 
     * @param file Name of file to read in
     */
    private void readData(final File file) {
        String[][] values = null;
        CSVParser theParser = null;

        clear();

        try {
            theParser = new CSVParser(new FileInputStream(file), "", "", "#");

            // # is a comment delimeter in net files
            values = theParser.getAllValues();
        } catch (Exception e) {
            System.out.println("Could not open file stream: " + e.toString());
        }

        String[] line;
        double[] dataPoint;

        for (int i = 0; i < values.length; i++) {
            line = values[i];
            dataPoint = new double[values[0].length];

            for (int j = 0; j < line.length; j++) {
                dataPoint[j] = Double.parseDouble(line[j]);
            }

            dimensions = dataPoint.length;

            addPoint(dataPoint);
        }
    }

    /**
     * Save the current datast to a stored file.
     * 
     * @param theFile the file where data should be saved
     */
    public void saveData(final File theFile) {
        FileOutputStream f = null;

        try {
            f = new FileOutputStream(theFile);
        } catch (Exception e) {
            System.err.println("Could not open file stream: " + e.toString());
        }

        if (f == null) {
            return;
        }

        CSVPrinter thePrinter = new CSVPrinter(f);

        thePrinter.printlnComment("");
        thePrinter.printlnComment("File: " + theFile.getName());
        thePrinter.printlnComment("");
        thePrinter.println();
        thePrinter.println(this.getDoubleStrings());

        thePrinter.println();
    }

    /**
     * Find repeated points and perturb them slightly so they don't overlap.
     * 
     * @param factor Distance to perturb
     */
    public void perturbOverlappingPoints(final double factor) {
        double distance;
        boolean repeat;
        int numPoints = getNumPoints();

        for (int i = 0; i < numPoints; i++) {
            repeat = false;
            // look for repeated points by computing distance to
            // previous points

            for (int j = i + 1; j < numPoints; j++) {
                distance = getDistance(i, j);

                if ((distance == 0) || (Double.isNaN(distance))) {
                    repeat = true;

                    continue;
                }
            }

            // if point is repeated assume a random perturbation will fix it
            if (repeat) {
                for (int k = 0; k < dimensions; k++) {
                    setComponent(i, k, getComponent(i, k) + ((Math.random() - 0.5) * factor));
                }
            } else {
                continue;
            }
        }
    }

    /**
     * Print out low dimensional points so maple can plot them Just does low
     * dimension = 2.
     */
    public void resultsToMaple() {
        double[] y;
        System.out.println("with(plots):");
        System.out.println("points := [");

        for (int i = 0; i < getNumPoints(); i++) {
            y = (double[]) getPoint(i);
            System.out.println("[" + y[0] + "," + y[1] + "],");
        }

        System.out.println("]:");
        System.out.println("plotsetup(ps,plotoutput=`plot.ps`,"
                + "plotoptions=`portrait,noborder,width=6.0in,height=6.0in`):");
        System.out.println("plot(points, style=POINT,symbol=CIRCLE);");
    }

    /**
     * Get a specific coordinate of a specific datapoint. Say, the second
     * component of the third datapoint in a 5-dimensional dataset with 50
     * points.
     * 
     * @param datapointNumber index of the point to get
     * @param dimension dimension of the desired component
     * 
     * @return the value of of n'th component of the specified datapoint
     */
    public double getComponent(final int datapointNumber, final int dimension) {
        // check dimension < dimensions
        double[] point = getPoint(datapointNumber);

        return point[dimension];
    }

    /**
     * Set a specific coordinate of a specific datapoint. Say, the second
     * component of the third datapoint in a 5-dimensional dataset with 50
     * points.
     * 
     * @param datapointNumber index of the point to get
     * @param dimension dimension of the desired component
     * @param newValue the new value of the n'th component of the specified
     *            datapoint
     */
    public void setComponent(final int datapointNumber, final int dimension, final double newValue) {
        // check dimension < dimensions
        getPoint(datapointNumber)[dimension] = newValue;
    }

    private void checkDimension(double[] point) {
        if ((point.length > 1) && (point.length != dimensions)) {
            throw new IllegalArgumentException("Error: Dataset is " + dimensions
                    + " dimensional, added data is " + point.length + " dimensional");
        }
    }

    /**
     * Check that a given point is "new", that is, that it is not already in the
     * dataset.
     * 
     * @param point the point to check
     * @param tolerance distance within which a point is considered old, and
     *            outside of which it is considered new
     * 
     * @return true if the point is new, false otherwise
     */
    private boolean isUniquePoint(final double[] point, final double tolerance) {
        return !(getClosestDistance(point) < tolerance);
    }

    /**
     * Returns the point closest to a given point.
     * 
     * @param point the point to check
     * 
     * @return the distance between this point and the closest other point in
     *         the dataset
     */
    private double getClosestDistance(final double[] point) {
        double dist = Double.MAX_VALUE;

        for (int i = 0; i < getNumPoints(); i++) {
            double temp = getDistance(point, getPoint(i));

            if (temp < dist) {
                dist = temp;
            }
        }

        return dist;
    }

    /**
     * Returns the index of the closest point.
     * 
     * @param point the point to check
     * 
     * @return the index of the point closest to this one in the dataset
     */
    public int getClosestIndex(final double[] point) {
        double dist = Double.MAX_VALUE;
        int ret = 0;

        for (int i = 0; i < getNumPoints(); i++) {
            double temp = getDistance(point, getPoint(i));

            if (temp < dist) {
                dist = temp;
                ret = i;
            }
        }

        return ret;
    }

    /**
     * Returns the k'th nearest neighbor.
     * 
     * @param k which nearest neighbor (first, second, etc.) to find
     * @param point the point whose neighbors are to be found
     * 
     * @return index of nearest neighbor
     */
    public int getKthNearestNeighbor(final int k, final double[] point) {
        // k-= 1;
        if (k > getNumPoints()) {
            System.out.println("ERROR: Non-existent datapoint requested");

            return -1;
        }

        int numPoints = getNumPoints();
        boolean[] pastClosest = new boolean[numPoints];
        double[] distances = new double[numPoints];
        ArrayList<Integer> ret = new ArrayList<Integer>();

        // Make an array of neighbors and populate distances
        for (int i = 0; i < numPoints; i++) {
            distances[i] = getDistance(getPoint(i), point);
            pastClosest[i] = false;
        }

        // Find k-th nearest neighbor
        for (int i = 0; i <= k; i++) {
            double min = Double.MAX_VALUE;
            int closest = 0;

            for (int j = 0; j < numPoints; j++) {
                if (pastClosest[j]) {
                    continue;
                }

                if (distances[j] < min) {
                    min = distances[j];
                    closest = j;
                }
            }

            pastClosest[closest] = true;
            ret.add(new Integer(closest));
        }

        return ((Integer) ret.get(k)).intValue();
    }

    /**
     * Get the distance between two points.
     * 
     * @param index1 index of point 1
     * @param index2 index of point 2
     * 
     * @return distance between points 1 and 2
     */
    public double getDistance(int index1, int index2) {
        int numPoints = getNumPoints();

        if (index1 < 0 || index1 > numPoints) {
            System.out.println("Dataset.getDistance() - index1: " + index1 + " out of bounds");

            return 0;
        } else if (index2 < 0 || index2 > numPoints) {
            System.out.println("Dataset.getDistance() - index2: " + index2 + " out of bounds");

            return 0;
        }

        if (index1 == index2) {
            return 0;
        } else if (index1 < index2) {
            int swap = index2;
            index2 = index1;
            index1 = swap;
        }

        double d = distances[getDistanceIndex(index1) + index2];

        if (d < 0) {
            return calculateDistance(index1, index2);
        } else {
            return d;
        }
    }

    /**
     * Returns tyhe euclidean distance between two points.
     * 
     * @param point1 First point of distance
     * @param point2 Second point of distance
     * 
     * @return the Euclidean distance between points 1 and 2
     */
    public double getDistance(final double[] point1, final double[] point2) {
        if (point1.length != point2.length) {
            System.out.println("Points of different dimensions are being compared");

            return 0;
        }

        double sum = 0;

        for (int i = 0; i < point1.length; i++) {
            sum += Math.pow(point1[i] - point2[i], 2);
        }

        return Math.sqrt(sum);
    }

    /**
     * @return the dimensionality of the points in the dataset
     */
    public int getDimensions() {
        return dimensions;
    }

    /**
     * Returns a matrix of interpoint distances, between the points in the
     * dataset. Note that the lower triangular duplicates the upper triangular
     * 
     * @return a matrix of interpoint distances
     */
    public double[][] getDistances() {
        System.out.println("in getDistances");

        calculateDistances();

        int numPoints = getNumPoints();
        double[][] temp = new double[numPoints][numPoints];

        for (int i = 0; i < numPoints; i++) {
            for (int j = 0; j < numPoints; j++) {
                temp[i][j] = getDistance(i, j);
            }
        }

        return temp;
    }

    /**
     * @return the sum of the distances between points in the dataset
     */
    public double getSumDistances() {
        double sum = 0;
        int stop = getDistanceEnd();

        calculateDistances();

        for (int i = 0; i < stop; i++) {
            sum += distances[i];
        }

        return sum;
    }

    /**
     * Returns the mean of the dataset on a given dimension.
     * 
     * @param d index of the dimension whose mean to get
     * 
     * @return mean of dataset on dimension d
     */
    public double getMean(final int d) {
        double sum = 0;
        int numPoints = getNumPoints();

        for (int i = 0; i < numPoints; i++) {
            sum += getComponent(i, d);
        }

        return sum / numPoints;
    }

    /**
     * Returns the covariance of the ith component of the dataset with respect
     * to the jth component.
     * 
     * @param i first dimension
     * @param j seconnd dimesion
     * 
     * @return covariance of i with respect to j
     */
    public double getCovariance(final int i, final int j) {
        double sum = 0;
        double meanI;
        double meanJ;
        int numPoints = getNumPoints();

        meanI = getMean(i);
        meanJ = getMean(j);

        for (int index = 0; index < numPoints; index++) {
            sum += ((getComponent(index, i) - meanI) * (getComponent(index, j) - meanJ));
        }

        return sum / (numPoints);
    }

    /**
     * Returns a covariance matrix for the dataset.
     * 
     * @return covariance matrix which describes how the data covary along each
     *         dimension
     */
    public Matrix getCovarianceMatrix() {
        Matrix m = new Matrix(dimensions, dimensions);

        for (int i = 0; i < dimensions; i++) {
            for (int j = i; j < dimensions; j++) {
                m.set(i, j, getCovariance(i, j));

                if (i != j) {
                    m.set(j, i, m.get(i, j)); // This is a symmetric matrix
                }
            }
        }

        return m;
    }

    /**
     * Returns the k'th most variant dimesion. For example, the most variant
     * dimension (k=1), or the least variant dimension (k=num_dimensions).
     * 
     * @param k Number of variant dimension
     * @return the k'th most variant dimension
     */
    public int getKthVariantDimension(final int k) {
        int n = k - 1;

        if (n > dimensions) {
            System.out.println("ERROR: Non-existent dimension requested");

            return -1;
        }

        boolean[] pastGreatest = new boolean[dimensions];
        double[] variances = new double[dimensions];
        ArrayList<Integer> ret = new ArrayList<Integer>();

        // Make an array of variances and populate booles
        for (int i = 0; i < dimensions; i++) {
            Double var = new Double(getCovariance(i, i));

            // System.out.println("[" + i + "]=" + var);
            variances[i] = var.doubleValue();
            pastGreatest[i] = false;
        }

        // Find k-th maximium variance
        for (int i = 0; i <= n; i++) {
            double max = 0;
            int greatest = 0;

            for (int j = 0; j < dimensions; j++) {
                if (pastGreatest[j]) {
                    continue;
                }

                if (variances[j] > max) {
                    max = variances[j];
                    greatest = j;
                }
            }

            pastGreatest[greatest] = true;
            ret.add(new Integer(greatest));
        }

        return ((Integer) ret.get(n)).intValue();
    }

    /**
     * @return a reference to the dataset
     */
    public ArrayList<double[]> getDatasetCopy() {
        return new ArrayList<double[]>(dataset);
    }

    public void mirror(Dataset other) {
        this.dataset.clear();
        this.dataset.addAll(other.dataset);
    }

    /**
     * Print out all points in the dataset Useful for debugging.
     */
    public void printDataset() {
        double[] tempPoint;

        for (int i = 0; i < getNumPoints(); i++) {
            System.out.println("\n[" + i + "]");
            tempPoint = (double[]) getPoint(i);

            for (int j = 0; j < tempPoint.length; j++) {
                System.out.print(" " + tempPoint[j]);
            }
        }

        System.out.println(" "); // add a carriage return
    }

    /**
     * Returns a matrix of strings, one row for each datapoint, representing the
     * dataset.
     * 
     * @return a matrix of strings representing the dataset
     */
    public String[][] getDoubleStrings() {
        int numPoints = getNumPoints();
        String[][] ret = new String[numPoints][dimensions];
        double[] tempPoint = new double[dimensions];

        for (int i = 0; i < numPoints; i++) {
            tempPoint = (double[]) getPoint(i);

            for (int j = 0; j < tempPoint.length; j++) {
                ret[i][j] = Double.toString(tempPoint[j]);
            }
        }

        return ret;
    }

    /**
     * Initializes persistant data.
     * 
     */
    public void initPersistentData() {
        persistentData.clear();

        for (int i = 0; i < getNumPoints(); i++) {
            persistentData.add(Utils.doubleArrayToString(dataset.get(i)));
        }
    }

    /**
     * Initializes Dataset from persitent data.
     * 
     */
    public void initCastor() {
        dataset.clear();

        for (int i = 0; i < persistentData.size(); i++) {
            addPoint(Utils.getVectorString((String) persistentData.get(i), ","));
        }
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (double[] point : dataset) {
            for (double d : point) {
                builder.append(d);
                builder.append(' ');
            }
            builder.append('\n');
        }

        return builder.toString();
    }
}