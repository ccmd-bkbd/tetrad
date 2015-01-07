///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010 by Peter Spirtes, Richard Scheines, Joseph Ramsey, //
// and Clark Glymour.                                                        //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesEstimator;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author Joseph Ramsey
 */
public class BayesEstimatorWrapper implements SessionModel, GraphSource {
    static final long serialVersionUID = 23L;

    /**
     * @serial Cannot be null.
     */
    private String name;

    /**
     * @serial Cannot be null.
     */
    private BayesIm bayesIm;

    /**
     * @serial Cannot be null.
     */
    private DataSet dataSet;

    //=================================CONSTRUCTORS========================//

    public BayesEstimatorWrapper(DataWrapper dataWrapper,
                                 BayesPmWrapper bayesPmWrapper) {
        if (dataWrapper == null) {
            throw new NullPointerException(
                    "BayesDataWrapper must not be null.");
        }

        if (bayesPmWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null");
        }

        DataSet dataSet = (DataSet) dataWrapper.getSelectedDataModel();
        BayesPm bayesPm = bayesPmWrapper.getBayesPm();

        this.dataSet = dataSet;

//        if (DataUtils.containsMissingValue(dataSet)) {
//            throw new IllegalArgumentException("Please remove or impute missing values.");
//        }

        System.out.println(dataSet);

        estimate(dataSet, bayesPm);
        log(bayesIm);
    }

    public BayesEstimatorWrapper(DataWrapper dataWrapper,
                                 BayesImWrapper bayesImWrapper) {
        if (dataWrapper == null) {
            throw new NullPointerException(
                    "BayesDataWrapper must not be null.");
        }

        if (bayesImWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null");
        }

        DataSet dataSet = (DataSet) dataWrapper.getSelectedDataModel();
        BayesPm bayesPm = bayesImWrapper.getBayesIm().getBayesPm();

//        if (DataUtils.containsMissingValue(dataSet)) {
//            throw new IllegalArgumentException("Please remove or impute missing values.");
//        }

        estimate(dataSet, bayesPm);
        log(bayesIm);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static BayesEstimatorWrapper serializableInstance() {
        return new BayesEstimatorWrapper(DataWrapper.serializableInstance(),
                BayesPmWrapper.serializableInstance());
    }

    //==============================PUBLIC METHODS========================//



    public BayesIm getEstimatedBayesIm() {
        return this.bayesIm;
    }

    private void estimate(DataSet DataSet, BayesPm bayesPm) {
        Graph graph = bayesPm.getDag();

        for (Object o : graph.getNodes()) {
            Node node = (Node) o;
            if (node.getNodeType() == NodeType.LATENT) {
                throw new IllegalArgumentException("Estimation of Bayes IM's " +
                        "with latents is not supported.");
            }
        }

        if (DataUtils.containsMissingValue(dataSet)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        try {
            MlBayesEstimator estimator = new MlBayesEstimator();
            this.bayesIm = estimator.estimate(bayesPm, DataSet);
        }
        catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            throw new RuntimeException("Value assignments between Bayes PM " +
                    "and discrete data set do not match.");
        }
    }

    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (bayesIm == null) {
            throw new NullPointerException();
        }

//        if (dataSet == null) {
//            throw new NullPointerException();
//        }
    }

    public Graph getGraph() {
        return bayesIm.getBayesPm().getDag();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    //======================== Private Methods ======================//

    private void log(BayesIm im) {
        TetradLogger.getInstance().log("info", "ML estimated Bayes IM.");
        TetradLogger.getInstance().log("im", im.toString());
    }
}



