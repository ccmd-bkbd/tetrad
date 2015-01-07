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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.List;


/**
 * Wraps a data model so that a random sample will automatically be drawn on
 * construction from a SemIm. Measured variables only.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class ImpliedCovarianceDataWrapper extends DataWrapper implements SessionModel {
    static final long serialVersionUID = 23L;
    private SemIm semIm = null;

    //==============================CONSTRUCTORS=============================//

    public ImpliedCovarianceDataWrapper(SemEstimatorWrapper wrapper) {
//        int sampleSize = params.getSampleSize();
//        boolean latentDataSaved = params.isIncludeLatents();
        SemEstimator semEstimator = wrapper.getSemEstimator();
        SemIm semIm1 = semEstimator.getEstimatedSem();

        if (semIm1 != null) {

            TetradMatrix matrix2D = semIm1.getImplCovarMeas();
            int sampleSize = semIm1.getSampleSize();
            List<Node> variables = wrapper.getSemEstimator().getEstimatedSem().getSemPm().getMeasuredNodes();
            CovarianceMatrix cov = new CovarianceMatrix(variables, matrix2D, sampleSize);
            setDataModel(cov);
            setSourceGraph(wrapper.getSemEstimator().getEstimatedSem().getSemPm().getGraph());
            this.semIm = wrapper.getEstimatedSemIm();
        }

        LogDataUtils.logDataModelList("Data simulated from a linear structural equation model.", getDataModelList());
    }

    public SemIm getSemIm() {
        return this.semIm;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new ImpliedCovarianceDataWrapper(SemEstimatorWrapper.serializableInstance());
    }
}