package edu.cmu.causality2.model.experiment;

import edu.cmu.causality2.model.graph.ManipulatedGraph;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * This class describes the Bayes IM graph model of the correct manipulated
 * graph.
 *
 * @author Matthew Easterday
 */
public class CorrectManipulatedGraphBayesIm {

    /**
     * @return the manipulated Bayes IM graph given the correct Bayes IM and the
     *         experimental setup.
     */
    public static BayesIm createIm(BayesIm correctIm, ExperimentalSetup experiment) {
        Dag mGraph = new Dag(new ManipulatedGraph(correctIm.getDag(), experiment));

        // Check for cycles. jdramsey 6/9/13 todo

        BayesPm pm = new BayesPm(mGraph, correctIm.getBayesPm());
        //this line is not working when you have manipulations
        BayesIm im = new MlBayesIm(pm, correctIm, MlBayesIm.MANUAL);
        //change the im based on locked and randomized variables in the
        //quantitative experimental setup
        List<String> variableNames = experiment.getGraph().getNodeNames();
        ManipulationType type;
        String variableName;
        for (Node node : experiment.getGraph().getNodes()) {
            type = experiment.getManipulation(node).getType();
            if (type == ManipulationType.LOCKED) {
                setVariableLocked(im, experiment, node);
            } else if (type == ManipulationType.RANDOMIZED) {
                setVariableRandomized(im, node);
            } else if (type == ManipulationType.NONE) {
                //do nothing
            }
        }
        return im;
    }


    private static void setVariableLocked(BayesIm IM, ExperimentalSetup experimentQN, Node node) throws IllegalArgumentException {
        int nodeIndex = IM.getNodeIndex(node);

        //if this node is  lock, make sure that it has no parents
        int numParents = IM.getNumParents(nodeIndex);
        if (numParents != 0) {
            throw new IllegalArgumentException(node + " should be locked and have no parents");
        }

        String lockedValue = ((Locked) experimentQN.getManipulation(node)).getLockedAtValue();
        int valueIndex = IM.getBayesPm().getCategoryIndex(node, lockedValue);

        //make all the jointDistributionProbabilities for the given variable 0
        for (int i = 0; i < IM.getBayesPm().getNumCategories(node); i++) {
            IM.setProbability(nodeIndex, 0, i, 0);
        }
        //set the locked value to 1.0
        IM.setProbability(nodeIndex, 0, valueIndex, 1.0);
    }

    private static void setVariableRandomized(BayesIm IM, Node node) {
        int nodeIndex = IM.getNodeIndex(node);
        int numValues = IM.getNumColumns(nodeIndex);

        for (int i = 0; i < IM.getBayesPm().getNumCategories(node); i++) {
            IM.setProbability(nodeIndex, 0, i, 1.0 / ((double) numValues));
        }
    }

}