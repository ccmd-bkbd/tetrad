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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: josephramsey
 * Date: Aug 30, 2010
 * Time: 6:43:40 PM
 * To change this template use File | Settings | File Templates.
 */
public interface Scorer {
    double score(Graph dag);

    ICovarianceMatrix getCovMatrix();

    String toString();

    double getFml();

    double getLogLikelihood();

    double getTruncLL();

    double getBicScore();

    double getAicScore();

    double getKicScore();

    double getChiSquare();

    double getPValue();

    DataSet getDataSet();

    int getNumFreeParams();

    int getDof();

    int getSampleSize();

    List<Node> getMeasuredNodes();

    TetradMatrix getSampleCovar();

    TetradMatrix getEdgeCoef();

    TetradMatrix getErrorCovar();

    List<Node> getVariables();

    SemIm getEstSem();
}

