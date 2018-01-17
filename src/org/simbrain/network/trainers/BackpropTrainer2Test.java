package org.simbrain.network.trainers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.jblas.DoubleMatrix;
import org.junit.Assert;
import org.junit.Test;
import org.simbrain.network.core.Network;
import org.simbrain.network.core.Neuron;
import org.simbrain.network.core.Synapse;
import org.simbrain.network.groups.SynapseGroup;
import org.simbrain.network.neuron_update_rules.SigmoidalRule;
import org.simbrain.network.subnetworks.BackpropNetwork;
import org.simbrain.network.trainers.BackpropTrainer2.UpdateMethod;
import org.simbrain.util.math.SquashingFunction;
import org.simbrain.util.math.SquashingFunctions;

public class BackpropTrainer2Test {

	@Test
	public void testCreation() {
		int noOut = 1;
		int noHid = 2;
		int noInp = 2;
		BackpropNetwork network = new BackpropNetwork(new Network(),
				new int[] { noInp, noHid, noOut });

		double str = 0.1;
		double[][] hidOutStrs = new double[noHid][noOut];
		int row = 0, col = 0;
		for (Neuron n : network.getOutputLayer().getNeuronListUnsafe()) {
			row = 0;
			for (Synapse s : n.getFanIn()) {
				s.forceSetStrength(str);
				hidOutStrs[row][col] = str;
				row++;
				str += 0.1;
			}
			col++;
		}

		str = 0.05;
		double[][] inpHidStrs = new double[noInp][noHid];
		row = 0;
		col = 0;
		for (Neuron n : network.getHiddenLayer().getNeuronListUnsafe()) {
			row = 0;
			for (Synapse s : n.getFanIn()) {
				s.forceSetStrength(str);
				inpHidStrs[row][col] = str;
				row++;
				str += 0.1;
			}
			col++;
		}

		double biases = 0;
		double [] outBiases = new double[noOut];
		int jj = 0;
		for (Neuron n : network.getOutputLayer().getNeuronListUnsafe()) {
			((SigmoidalRule) n.getUpdateRule()).setBias(0.314);
			biases += 0.314;
			outBiases[jj++] = ((SigmoidalRule) n.getUpdateRule()).getBias();
		}

		biases = 0.11;
		jj=0;
		double [] hidBiases = new double[noHid];
		for (Neuron n : network.getHiddenLayer().getNeuronList()) {
			((SigmoidalRule) n.getUpdateRule()).setBias(biases);
			biases += 0.1;
			hidBiases[jj++] = ((SigmoidalRule) n.getUpdateRule()).getBias();
		}

		BackpropTrainer2 trainer = new BackpropTrainer2(network);

		//        trainer.printDebugInfo();
		//         System.out.println(Arrays.deepToString(inpHidStrs));
		//         System.out.println();
		//         System.out.println(Arrays.deepToString(hidOutStrs));

		double[][] inHidJBlas = trainer.getWeightMatrices().get(0).transpose()
				.toArray2();
		System.out.println(Arrays.deepToString(inpHidStrs));
		System.out.println();
		System.out.println(Arrays.deepToString(inHidJBlas));
		System.out.println("-----");
		double[][] hidOutJBlas = trainer.getWeightMatrices().get(1).transpose()
				.toArray2();
		System.out.println(Arrays.deepToString(hidOutStrs));
		System.out.println();
		System.out.println(Arrays.deepToString(hidOutJBlas));
		System.out.println();
		System.out.println("-----");
		System.out.println();


		//  
		for (int ii = 0; ii < noInp; ++ii) {
			assertArrayEquals(inHidJBlas[ii], inpHidStrs[ii], 0);
		}
		for (int ii = 0; ii < noHid; ++ii) {
			assertArrayEquals(hidOutJBlas[ii], hidOutStrs[ii], 0);
		}
		

		
		List<DoubleMatrix> jblasBiases = trainer.getBiases();
		double [] biasesOutJBlas = jblasBiases.get(1).data;
		double [] biasesHidJBlas = jblasBiases.get(0).data;
		
		assertArrayEquals(biasesHidJBlas, hidBiases, 0);
		assertArrayEquals(biasesOutJBlas, outBiases, 0);
		
	}

	@Test
	public void testCopyBack() {
		int noOut = 1;
		int noHid = 2;
		int noInp = 2;
		BackpropNetwork network = new BackpropNetwork(new Network(),
				new int[] { noInp, noHid, noOut });
        network.getTrainingSet().setInputData(
                new double[][] { { 0, 0 }, { 0, 1 }, { 1, 0 }, { 1, 1 } });
        network.getTrainingSet()
                .setTargetData(new double[][] { { 0 }, { 1 }, { 1 }, { 0 } });
		double str = 0.1;
		for (Neuron n : network.getOutputLayer().getNeuronListUnsafe()) {
			for (Synapse s : n.getFanIn()) {
				s.forceSetStrength(str);
				str += 0.1;
			}
		}

		str = 0.05;
		for (Neuron n : network.getHiddenLayer().getNeuronListUnsafe()) {
			for (Synapse s : n.getFanIn()) {
				s.forceSetStrength(str);
				str += 0.1;
			}
		}

		double biases = 0;
		for (Neuron n : network.getOutputLayer().getNeuronListUnsafe()) {
			((SigmoidalRule) n.getUpdateRule()).setBias(0.314);
			biases += 0.314;
		}

		biases = 0.11;
		for (Neuron n : network.getHiddenLayer().getNeuronList()) {
			((SigmoidalRule) n.getUpdateRule()).setBias(biases);
			biases += 0.1;
		}

		BackpropTrainer2 trainer = new BackpropTrainer2(network);
        trainer.setUpdateMethod(UpdateMethod.SINGLE);
		trainer.initData();
        trainer.setLearningRate(0.1);
        
        trainer.apply();
        trainer.commitChanges();
        
        double[] hidBiases = network.getHiddenLayer().getBiases();
        double[] outBiases = network.getOutputLayer().getBiases();
        Object[] tmp = network.getHiddenLayer().getIncomingSgs().toArray();
        double[][] inpHidStrs = ((SynapseGroup) tmp[0]).getWeightMatrix();
        tmp = network.getOutputLayer().getIncomingSgs().toArray();
        double[][] hidOutStrs = ((SynapseGroup) tmp[0]).getWeightMatrix();
        
		//        trainer.printDebugInfo();
		//         System.out.println(Arrays.deepToString(inpHidStrs));
		//         System.out.println();
		//         System.out.println(Arrays.deepToString(hidOutStrs));

		double[][] inHidJBlas = trainer.getWeightMatrices().get(0).transpose()
				.toArray2();
		System.out.println(Arrays.deepToString(inpHidStrs));
		System.out.println();
		System.out.println(Arrays.deepToString(inHidJBlas));
		System.out.println("-----");
		double[][] hidOutJBlas = trainer.getWeightMatrices().get(1).transpose()
				.toArray2();
		System.out.println(Arrays.deepToString(hidOutStrs));
		System.out.println();
		System.out.println(Arrays.deepToString(hidOutJBlas));

		//  
		for (int ii = 0; ii < noInp; ++ii) {
			assertArrayEquals(inHidJBlas[ii], inpHidStrs[ii], 0);
		}
		for (int ii = 0; ii < noHid; ++ii) {
			assertArrayEquals(hidOutJBlas[ii], hidOutStrs[ii], 0);
		}
		

		
		List<DoubleMatrix> jblasBiases = trainer.getBiases();
		double [] biasesOutJBlas = jblasBiases.get(1).data;
		double [] biasesHidJBlas = jblasBiases.get(0).data;
		
		assertArrayEquals(biasesHidJBlas, hidBiases, 0);
		assertArrayEquals(biasesOutJBlas, outBiases, 0);
		
	}

	
//	  public BackpropNetwork createTestNetwork(int noInp, int noHid, int noOut, NeuronUpdateRule hidRule, NeuronUpdateRule outRule) {
//	        BackpropNetwork network = new BackpropNetwork(new Network(),
//	                new int[] { noInp, noHid, noOut });
//	        network.getHiddenLayer().setNeuronType(hidRule);
//	        network.getOutputLayer().setNeuronType(outRule);
//	        return network;
//	    }
//
//	    // Just to illustrate how to set up some tests
//	    public void createSomeNetworks() {
//
//	        SigmoidalRule tanh = new SigmoidalRule(SquashingFunction.TANH);
//	        SigmoidalRule logistic = new SigmoidalRule(SquashingFunction.LOGISTIC);
//	        SigmoidalRule arctan = new SigmoidalRule(SquashingFunction.ARCTAN);
//	        LinearRule linear = new LinearRule(); // May have to make Linear implement transfer function
//
//	        BackpropNetwork network1 = createTestNetwork(2,2,2, tanh, linear);
//	        BackpropNetwork network2 = createTestNetwork(2,2,2, logistic, linear);
//	        BackpropNetwork network3 = createTestNetwork(2,2,2, arctan, linear);
//	        BackpropNetwork network4 = createTestNetwork(2,2,2, tanh, tanh);
//
//	        // Try some non-standard bounds. The transfer functions should be able to handle this
//	        SigmoidalRule logistic_1_1 = new SigmoidalRule(SquashingFunction.LOGISTIC);
//	        logistic_1_1.setLowerBound(-1);
//	        logistic_1_1.setUpperBound(1);
//	        BackpropNetwork network5 = createTestNetwork(2,2,2, logistic_1_1, linear);
//
//	    }

	@Test
    public void testLogistic() {
	    double epsilon = 0.001;

	    // Check that the logistic is in between the bounds at 0
	    double actual = SquashingFunctions.logistic(0, 1, 0, 1);
	    double expected = 0.5;
        assertEquals(actual, expected, epsilon);

        // Check that logistic of a small value is less than logistic of a large value
        actual = SquashingFunctions.logistic(-100, 1, 0, 1);
        expected = SquashingFunctions.logistic(100, 1, 0, 1);
        Assert.assertTrue(actual < expected);

        // Check that logistic approximately reaches the bounds for very large or small values
        actual = SquashingFunctions.logistic(1e9, 5, 0, 1);
        expected = 5;
        assertEquals(actual, expected, epsilon);

        actual = SquashingFunctions.logistic(-1e9, 1, -3, 1);
        expected = -3;
        assertEquals(actual, expected, epsilon);

        // Check that logistic with a large slope is more negative for negative values and more positive
        // for positive values, and equal for zero
        actual = SquashingFunctions.logistic(-1, 1, 0, 1);
        expected = SquashingFunctions.logistic(-1, 1, 0, 10);
        Assert.assertTrue(actual > expected);

        actual = SquashingFunctions.logistic(0, 1, 0, 1);
        expected = SquashingFunctions.logistic(0, 1, 0, 10);
        assertEquals(actual, expected, epsilon);

        actual = SquashingFunctions.logistic(1, 1, 0, 1);
        expected = SquashingFunctions.logistic(1, 1, 0, 10);
        Assert.assertTrue(actual < expected);

        // Check that the matrix form is approximately equal to the singular form
        DoubleMatrix xs = DoubleMatrix.rand(1000).muli(10).subi(5);
        xs.put(0, 0);
        xs.put(1, -1e9);
        xs.put(2, 1e9);
        DoubleMatrix actuals = DoubleMatrix.zeros(1000);
        SquashingFunctions.logistic(xs, actuals, -2, 4.7, 0.8);
        for (int i = 0; i < 1000; ++i) {
            expected = SquashingFunctions.logistic(xs.get(i), -2, 4.7, 0.8);
            assertEquals(actuals.get(i), expected, epsilon);
        }
    }

    @Test
	public void testLogisticDerivative() {
        double delta = 0.001;
        double epsilon = 0.001;

        // Check that the derivative is approximately equal to the limit of the difference f(x) - f(x+d) as d goes to 0
        double[] xs = {-1e9, -1000, -10, -3.33, -1, -0.001, 0.0, 0.0001, 4.7, 16, 1e9};
        for (double x : xs) {
            double actual = SquashingFunctions.derivLogistic(x, 13, 3, 1.9);
            double expected = (SquashingFunctions.logistic(x, 13, 3, 1.9) -
                    SquashingFunctions.logistic(x - delta, 13, 3, 1.9)) / delta;
            assertEquals(actual, expected, epsilon);
        }

        // Check that the matrix form is approximately equal to the singular form
        DoubleMatrix inputs = new DoubleMatrix(xs);
        DoubleMatrix actuals = DoubleMatrix.zeros(xs.length);
        SquashingFunctions.derivLogistic(inputs, actuals, 10, 0, 0.25);
        for (int i = 0; i < actuals.length; ++i) {
            double expected = SquashingFunctions.derivLogistic(xs[i], 10, 0, 0.25);
            assertEquals(actuals.get(i), expected, epsilon);
        }

        // Check that the logistic with derivatives is equal to the logistic and the derivative
        DoubleMatrix actualDerivs = DoubleMatrix.zeros(xs.length);
        SquashingFunctions.logisticWithDerivative(inputs, actuals, actualDerivs, 100, -100, 1);
        DoubleMatrix expecteds = DoubleMatrix.zeros(xs.length);
        SquashingFunctions.logistic(inputs, expecteds, 100, -100, 1);
        DoubleMatrix expectedDerivs = DoubleMatrix.zeros(xs.length);
        SquashingFunctions.derivLogistic(inputs, expectedDerivs, 100, -100, 1);
        //assertArrayEquals(actuals.data, expecteds.data, epsilon);
        //assertArrayEquals(actualDerivs.data, expectedDerivs.data, epsilon);
	}
	
}
