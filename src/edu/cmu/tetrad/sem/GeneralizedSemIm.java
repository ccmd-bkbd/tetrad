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

import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.model.calculator.expression.Context;
import edu.cmu.tetradapp.model.calculator.expression.Expression;
import edu.cmu.tetradapp.model.calculator.parser.ExpressionLexer;
import edu.cmu.tetradapp.model.calculator.parser.Token;
import pal.math.ConjugateDirectionSearch;
import pal.math.MultivariateFunction;
import pal.math.OrthogonalHints;

import java.text.NumberFormat;
import java.util.*;

import static java.lang.Math.*;

/**
 * Represents a generalized SEM instantiated model. The parameteric form of this model allows arbitrary
 */
public class GeneralizedSemIm implements IM, TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The wrapped PM, that holds all of the expressions and structure for the model.
     */
    private GeneralizedSemPm pm;

    /**
     * A map from parameters names to their values--these form the context for evaluating expressions.
     * Variables do not appear in this list. All parameters are double-valued.
     */
    private Map<String, Double> parameterValues;

    /**
     * True iff only positive data should be simulated.
     */
    private boolean simulatePositiveDataOnly = false;

    /**
     * The coefficient of a (linear) self-loop for each variable, or NaN if there is none.
     */
    private double selfLoopCoef = Double.NaN;


    /**
     * Constructs a new GeneralizedSemIm from the given GeneralizedSemPm by picking values for each of
     * the parameters from their initial distributions.
     *
     * @param pm the GeneralizedSemPm. Includes all of the equations and distributions of the model.
     */
    public GeneralizedSemIm(GeneralizedSemPm pm) {
        this.pm = new GeneralizedSemPm(pm);

        this.parameterValues = new HashMap<String, Double>();

        Set<String> parameters = pm.getParameters();

        for (String parameter : parameters) {
            Expression expression = pm.setParameterExpression(parameter);

            Context context = new Context() {
                public Double getValue(String var) {
                    return parameterValues.get(var);
                }
            };

            double initialValue = expression.evaluate(context);
            parameterValues.put(parameter, initialValue);
        }
    }

    public GeneralizedSemIm(GeneralizedSemPm pm, SemIm semIm) {
        this(pm);
        SemPm semPm = semIm.getSemPm();

        Set<String> parameters = pm.getParameters();

        // If there are any missing parameters, just ignore the sem IM.
        for (String parameter : parameters) {
            Parameter paramObject = semPm.getParameter(parameter);

            if (paramObject == null) {
                return;
            }
        }

        for (String parameter : parameters) {
            Parameter paramObject = semPm.getParameter(parameter);

            if (paramObject == null) {
                throw new IllegalArgumentException("Parameter missing from Gaussian SEM IM: " + parameter);
            }

            double value = semIm.getParamValue(paramObject);

            if (paramObject.getType() == ParamType.VAR) {
                value = Math.sqrt(value);
            }

            setParameterValue(parameter, value);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static GeneralizedSemIm serializableInstance() {
        return new GeneralizedSemIm(GeneralizedSemPm.serializableInstance());
    }

    /**
     * @return a copy of the stored GeneralizedSemPm.
     */
    public GeneralizedSemPm getGeneralizedSemPm() {
        return new GeneralizedSemPm(pm);
    }

    /**
     * @param parameter The parameter whose values is to be set.
     * @param value     The double value that <code>param</code> is to be set to.
     */
    public void setParameterValue(String parameter, double value) {
        if (parameter == null) {
            throw new NullPointerException("Parameter not specified.");
        }

        if (!parameterValues.keySet().contains(parameter)) {
            throw new IllegalArgumentException("Not a parameter in this model: " + parameter);
        }

        parameterValues.put(parameter, value);
    }

    /**
     * @param parameter The parameter whose value is to be retrieved.
     * @return The retrieved value.
     */
    public double getParameterValue(String parameter) {
        if (parameter == null) {
            throw new NullPointerException("Parameter not specified.");
        }

        if (!parameterValues.keySet().contains(parameter)) {
            throw new IllegalArgumentException("Not a parameter in this model: " + parameter);
        }

        return parameterValues.get(parameter);
    }

    /**
     * @return the user's String formula with numbers substituted for parameters, where substitutions exist.
     */
    public String getNodeSubstitutedString(Node node) {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        String expressionString = pm.getNodeExpressionString(node);

        if (expressionString == null) return null;

        ExpressionLexer lexer = new ExpressionLexer(expressionString);
        StringBuilder buf = new StringBuilder();
        Token token;

        while ((token = lexer.nextTokenIncludingWhitespace()) != Token.EOF) {
            String tokenString = lexer.getTokenString();

            if (token == Token.PARAMETER) {
                Double value = parameterValues.get(tokenString);

                if (value != null) {
                    buf.append(nf.format(value));
                    continue;
                }
            }

            buf.append(tokenString);
        }

        return buf.toString();
    }

    /**
     * @param node              The node whose expression is being evaluated.
     * @param substitutedValues A mapping from Strings parameter names to Double values; these values will be
     *                          substituted for the stored values where applicable.
     * @return the expression string with values substituted for parameters.
     */
    public String getNodeSubstitutedString(Node node, Map<String, Double> substitutedValues) {
        if (node == null) {
            throw new NullPointerException();
        }

        if (substitutedValues == null) {
            throw new NullPointerException();
        }

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        String expressionString = pm.getNodeExpressionString(node);

        ExpressionLexer lexer = new ExpressionLexer(expressionString);
        StringBuilder buf = new StringBuilder();
        Token token;

        while ((token = lexer.nextTokenIncludingWhitespace()) != Token.EOF) {
            String tokenString = lexer.getTokenString();

            if (token == Token.PARAMETER) {
                Double value = substitutedValues.get(tokenString);

                if (value == null) {
                    value = parameterValues.get(tokenString);
                }

                if (value != null) {
                    buf.append(nf.format(value));
                    continue;
                }
            }

            buf.append(tokenString);
        }

        return buf.toString();
    }

    /**
     * Returns a String representation of the IM, in this case a lsit of parameters and their values.
     */
    public String toString() {
        List<String> parameters = new ArrayList<String>(pm.getParameters());
        Collections.sort(parameters);
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        StringBuilder buf = new StringBuilder();
        GeneralizedSemPm pm = getGeneralizedSemPm();
        buf.append("\nVariable nodes:\n");

        for (Node node : pm.getVariableNodes()) {
            String string = getNodeSubstitutedString(node);
            buf.append("\n" + node + " = " + string);
        }

        buf.append("\n\nErrors:\n");

        for (Node node : pm.getErrorNodes()) {
            String string = getNodeSubstitutedString(node);
            buf.append("\n" + node + " ~ " + string);
        }

        buf.append("\n\nParameter values:\n");
        for (String parameter : parameters) {
            double value = getParameterValue(parameter);
            buf.append("\n" + parameter + " = " + nf.format(value));
        }

        return buf.toString();
    }

    public DataSet simulateData(int sampleSize, boolean latentDataSaved) {
        long seed = RandomUtil.getInstance().getSeed();
        TetradLogger.getInstance().log("info", "Seed = " + seed);
        RandomUtil.getInstance().setSeed(seed);

        if (pm.getGraph().isTimeLagModel()) {
            return simulateTimeSeries(sampleSize);
        }

//        return simulateDataRecursive(sampleSize, latentDataSaved);
//        return simulateDataMinimizeSurface(sampleSize, latentDataSaved);
        return simulateDataAvoidInfinity(sampleSize, latentDataSaved);
//        return simulateDataNSteps(sampleSize, latentDataSaved);
    }

    private DataSet simulateTimeSeries(int sampleSize) {
        SemGraph semGraph = new SemGraph(getSemPm().getGraph());
        semGraph.setShowErrorTerms(true);
        TimeLagGraph timeLagGraph = getSemPm().getGraph().getTimeLagGraph();

        List<Node> variables = new ArrayList<Node>();

        for (Node node : timeLagGraph.getLag0Nodes()) {
            if (node.getNodeType() == NodeType.ERROR) continue;
            variables.add(new ContinuousVariable(timeLagGraph.getNodeId(node).getName()));
        }

        List<Node> lag0Nodes = timeLagGraph.getLag0Nodes();

        for (Node node : new ArrayList<Node>(lag0Nodes)) {
            if (node.getNodeType() == NodeType.ERROR) {
                lag0Nodes.remove(node);
            }
        }

        DataSet fullData = new ColtDataSet(sampleSize, variables);

        Map<Node, Integer> nodeIndices = new HashMap<Node, Integer>();

        for (int i = 0; i < lag0Nodes.size(); i++) {
            nodeIndices.put(lag0Nodes.get(i), i);
        }

        Graph contemporaneousDag = timeLagGraph.subgraph(timeLagGraph.getLag0Nodes());

        List<Node> tierOrdering = contemporaneousDag.getTierOrdering();

        for (Node node : new ArrayList<Node>(tierOrdering)) {
            if (node.getNodeType() == NodeType.ERROR) {
                tierOrdering.remove(node);
            }
        }

        final Map<String, Double> variableValues = new HashMap<String, Double>();

        Context context = new Context() {
            public Double getValue(String term) {
                Double value = parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                } else {
                    return RandomUtil.getInstance().nextNormal(0, 1);
                }
            }
        };

        ROW:
        for (int currentStep = 0; currentStep < sampleSize; currentStep++) {
            for (Node node : tierOrdering) {
                Expression expression = pm.getNodeExpression(node);
                double value = expression.evaluate(context);

                if (isSimulatePositiveDataOnly() && value < 0) {
                    currentStep--;
                    continue ROW;
                }

                int col = nodeIndices.get(node);
                fullData.setDouble(currentStep, col, value);
                variableValues.put(node.getName(), value);
            }

            for (Node node : lag0Nodes) {
                TimeLagGraph.NodeId _id = timeLagGraph.getNodeId(node);

                for (int lag = 1; lag <= timeLagGraph.getMaxLag(); lag++) {
                    Node _node = timeLagGraph.getNode(_id.getName(), lag);
                    int col = lag0Nodes.indexOf(node);

                    if (_node == null) {
                        continue;
                    }

                    if (currentStep - lag + 1 >= 0) {
                        double _value = fullData.getDouble((currentStep - lag + 1), col);
                        variableValues.put(_node.getName(), _value);
                    }
                }
            }
        }

        return fullData;
    }

    /**
     * This simulates data by picking random values for the exogenous terms and
     * percolating this information down through the SEM, assuming it is
     * acyclic. Fast for large simulations but hangs for cyclic models.
     *
     * @param sampleSize > 0.
     * @return the simulated data set.
     */
    public DataSet simulateDataRecursive(int sampleSize, boolean latentDataSaved) {
        final Map<String, Double> variableValues = new HashMap<String, Double>();

        Context context = new Context() {
            public Double getValue(String term) {
                Double value = parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                }

                throw new IllegalArgumentException("No value recorded for '" + term + "'");
            }
        };

        List<Node> variables = pm.getNodes();
        List<Node> continuousVariables = new LinkedList<Node>();
        List<Node> nonErrorVariables = pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (Node node : nonErrorVariables) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        DataSet fullDataSet = new ColtDataSet(sampleSize, continuousVariables);

        // Create some index arrays to hopefully speed up the simulation.
        SemGraph graph = pm.getGraph();
        List<Node> tierOrdering = graph.getFullTierOrdering();

        int[] tierIndices = new int[variables.size()];

        for (int i = 0; i < tierIndices.length; i++) {
            tierIndices[i] = nonErrorVariables.indexOf(tierOrdering.get(i));
        }

        int[][] _parents = new int[variables.size()][];

        for (int i = 0; i < variables.size(); i++) {
            Node node = variables.get(i);
            List<Node> parents = graph.getParents(node);

            _parents[i] = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                Node _parent = parents.get(j);
                _parents[i][j] = variables.indexOf(_parent);
            }
        }

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {
            variableValues.clear();

            for (int tier = 0; tier < tierOrdering.size(); tier++) {
                Node node = tierOrdering.get(tier);
                Expression expression = pm.getNodeExpression(node);
                double value = expression.evaluate(context);
                variableValues.put(node.getName(), value);

                int col = tierIndices[tier];

                if (col == -1) {
                    continue;
                }

//                if (isSimulatePositiveDataOnly() && value < 0) {
//                    row--;
//                    continue ROW;
//                }

//                if (!Double.isNaN(selfLoopCoef) && row > 0) {
//                    value += selfLoopCoef * fullDataSet.getDouble(row - 1, col);
//                }

//                value = min(max(value, -5.), 5.);

                fullDataSet.setDouble(row, col, value);
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }
    }



    public DataSet simulateDataMinimizeSurface(int sampleSize, boolean latentDataSaved) {
        final Map<String, Double> variableValues = new HashMap<String, Double>();

        final double func_tolerance = 1.0e-4;
        final double param_tolerance = 1.0e-3;

        List<Node> continuousVariables = new LinkedList<Node>();
        final List<Node> variableNodes = pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        DataSet fullDataSet = new ColtDataSet(sampleSize, continuousVariables);

        final Context context = new Context() {
            public Double getValue(String term) {
                Double value = parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                }

                throw new IllegalArgumentException("No value recorded for '" + term + "'");
            }
        };

        final double[] _metric = new double[1];

        MultivariateFunction function = new MultivariateFunction() {
            double metric;

            public double evaluate(double[] doubles) {
                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), doubles[i]);
                }

                double[] image = new double[doubles.length];

                for (int i = 0; i < variableNodes.size(); i++) {
                    Node node = variableNodes.get(i);
                    Expression expression = pm.getNodeExpression(node);
                    image[i] = expression.evaluate(context);

                    if (Double.isNaN(image[i])) {
                        throw new IllegalArgumentException("Undefined value for expression " + expression);
                    }
                }

                metric = 0.0;

                for (int i = 0; i < variableNodes.size(); i++) {
                    double diff = doubles[i] - image[i];
                    metric += diff * diff;
                }

                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), image[i]);
                }

                _metric[0] = metric;

                return metric;
            }

            public int getNumArguments() {
                return variableNodes.size();
            }

            public double getLowerBound(int i) {
                return -10000;
            }

            public double getUpperBound(int i) {
                return 10000;
            }

            public double getMetric() {
                return -metric;
            }

            public OrthogonalHints getOrthogonalHints() {
                return null;
            }
        };

        ConjugateDirectionSearch search = new ConjugateDirectionSearch();
        search.step = 10.0;

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Take random draws from error distributions.
            for (int i = 0; i < variableNodes.size(); i++) {
                Node variable = variableNodes.get(i);
                Node error = pm.getErrorNode(variable);

                Expression expression = pm.getNodeExpression(error);
                double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
            }

            for (int i = 0; i < variableNodes.size(); i++) {
                Node variable = variableNodes.get(i);
                variableValues.put(variable.getName(), 0.0);// RandomUtil.getInstance().nextUniform(-5, 5));
            }

            while (true) {

                double[] values = new double[variableNodes.size()];

                for (int i = 0; i < values.length; i++) {
                    values[i] = variableValues.get(variableNodes.get(i).getName());
                }

                search.optimize(function, values, func_tolerance, param_tolerance);

                for (int i = 0; i < variableNodes.size(); i++) {
                    if (isSimulatePositiveDataOnly() && values[i] < 0) {
                        row--;
                        continue ROW;
                    }

                    if (!Double.isNaN(selfLoopCoef) && row > 0) {
                        values[i] += selfLoopCoef * fullDataSet.getDouble(row - 1, i);
                    }

                    variableValues.put(variableNodes.get(i).getName(), values[i]);
                    fullDataSet.setDouble(row, i, values[i]);
                }

                if (_metric[0] < 0.01) {
                    break; // while
                }
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }
    }

    public DataSet simulateDataAvoidInfinity(int sampleSize, boolean latentDataSaved) {
        final Map<String, Double> variableValues = new HashMap<String, Double>();

        List<Node> continuousVariables = new LinkedList<Node>();
        final List<Node> variableNodes = pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        DataSet fullDataSet = new ColtDataSet(sampleSize, continuousVariables);

        final Context context = new Context() {
            public Double getValue(String term) {
                Double value = parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                }

                throw new IllegalArgumentException("No value recorded for '" + term + "'");
            }
        };

        boolean allInRange = true;

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Take random draws from error distributions.
            for (Node variable : variableNodes) {
                Node error = pm.getErrorNode(variable);

                Expression expression = pm.getNodeExpression(error);
                double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
            }

            // Set the variable nodes to zero.
            for (Node variable : variableNodes) {
                Node error = pm.getErrorNode(variable);

                Expression expression = pm.getNodeExpression(error);
                double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(variable.getName(), value); //0.0; //RandomUtil.getInstance().nextUniform(-1, 1));
            }

            // Repeatedly update variable values until one of them hits infinity or negative infinity or
            // convergence within delta.

            double delta = 1e-10;
            int count = -1;

            while (++count < 5000) {
                double[] values = new double[variableNodes.size()];

                for (int i = 0; i < values.length; i++) {
                    Node node = variableNodes.get(i);
                    Expression expression = pm.getNodeExpression(node);
                    double value = expression.evaluate(context);
                    values[i] = value;
                }

                allInRange = true;

                for (int i = 0; i < values.length; i++) {
                    Node node = variableNodes.get(i);

                    // If any of the variables hasn't converged or if any of the variable values has gone
                    // outside of the bound (-1e6, 1e6), judge nonconvergence and pick another random starting point.
                    if (!(Math.abs(variableValues.get(node.getName()) - values[i]) < delta)) {
                        if (!(Math.abs(variableValues.get(node.getName())) < 1e6)) {
                            if (count < 1000) {
                                row--;
                                continue ROW;
                            }
                        }

                        allInRange = false;
                        break;
                    }

                }

                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), values[i]);
                }

                if (allInRange) {
                    break;
                }
            }

            if (!allInRange) {
                if (count < 10000) {
                    row--;
                    System.out.println("Trying another starting point...");
                    continue ROW;
                } else {
                    System.out.println("Couldn't converge in simulation.");

                    for (int i = 0; i < variableNodes.size(); i++) {
                        fullDataSet.setDouble(row, i, Double.NaN);
//                    continue ROW;
                        return fullDataSet;
                    }
                }
            }

            for (int i = 0; i < variableNodes.size(); i++) {
                double value = variableValues.get(variableNodes.get(i).getName());

                if (isSimulatePositiveDataOnly() && value < 0) {
                    row--;
                    continue ROW;
                }

                if (!Double.isNaN(selfLoopCoef) && row > 0) {
                    value += selfLoopCoef * fullDataSet.getDouble(row - 1, i);
                }

                fullDataSet.setDouble(row, i, value);
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }

    }

    public DataSet simulateDataSpecial1(int sampleSize, boolean latentDataSaved, int timeStepsPerSecond) {

        final Map<String, Double> variableValues = new HashMap<String, Double>();

        List<Node> continuousVariables = new LinkedList<Node>();
        final List<Node> variableNodes = pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        double[][] impulses = new double[continuousVariables.size()][sampleSize];

        for (int j = 0; j < impulses.length; j++) {
            int value = 0;

            for (int i = 0; i < impulses[j].length; i++) {
                double random = RandomUtil.getInstance().nextDouble();
                if (value == 0 && random < 0.1) {
                    value = 1;
                } else if (value == 1 && random < 0.4) {
                    value = 0;
                }

                impulses[j][i] = value;
            }
        }

        DataSet fullDataSet = new ColtDataSet(sampleSize, continuousVariables);

        final Context context = new Context() {
            public Double getValue(String term) {
                Double value = parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                }

                throw new IllegalArgumentException("No value recorded for '" + term + "'");
            }
        };

        boolean allInRange = true;
        int _count = -1;

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Take random draws from error distributions.
            for (Node variable : variableNodes) {
                Node error = pm.getErrorNode(variable);

                Expression expression = pm.getNodeExpression(error);
                double value = expression.evaluate(context);

                value += impulses[variableNodes.indexOf(variable)][row];

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
            }

            // Set the variable nodes to zero.
            for (Node variable : variableNodes) {
                Node error = pm.getErrorNode(variable);

                Expression expression = pm.getNodeExpression(error);
                double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(variable.getName(), value); //0.0; //RandomUtil.getInstance().nextUniform(-1, 1));
            }

            // Repeatedly update variable values until one of them hits infinity or negative infinity or
            // convergence within delta.

            double delta = 1e-6;
            int count = -1;

            while (++count < 1000) {
                double[] values = new double[variableNodes.size()];

                for (int i = 0; i < values.length; i++) {
                    Node node = variableNodes.get(i);
                    Expression expression = pm.getNodeExpression(node);
                    double value = expression.evaluate(context);
                    values[i] = value;
                }

                allInRange = true;

                for (int i = 0; i < values.length; i++) {
                    Node node = variableNodes.get(i);

                    if (!(Math.abs(variableValues.get(node.getName()) - values[i]) < delta)) {
                        allInRange = false;
                        break;
                    }
                }


                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), values[i]);
                }

                if (allInRange) {
                    break;
                }
            }

            if (!allInRange && ++_count < 20) {
                row--;
                System.out.println("Trying another starting point...");
                continue ROW;
            } else if (_count >= 100) {
                System.out.println("Couldn't converge in simulation.");

                for (int i = 0; i < variableNodes.size(); i++) {
                    fullDataSet.setDouble(row, i, Double.NaN);
//                    continue ROW;
                    return fullDataSet;
                }
            }

            for (int i = 0; i < variableNodes.size(); i++) {
                double value = variableValues.get(variableNodes.get(i).getName());

                if (isSimulatePositiveDataOnly() && value < 0) {
                    row--;
                    continue ROW;
                }

                if (!Double.isNaN(selfLoopCoef) && row > 0) {
                    value += selfLoopCoef * fullDataSet.getDouble(row - 1, i);
                }

                fullDataSet.setDouble(row, i, value);
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }


    }

    public DataSet simulateDataSpecial2(int sampleSize, boolean latentDataSaved, int timeStepsPerSecond, double sigma) {

        final Map<String, Double> variableValues = new HashMap<String, Double>();

        List<Node> continuousVariables = new LinkedList<Node>();
        final List<Node> variableNodes = pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        double[][] impulses = new double[continuousVariables.size()][sampleSize];


        for (int j = 0; j < impulses.length; j++) {
            int zeroThisRound = 0;
            int oneThisRound = 0;

            double totalZeroThisRound = 0;
            double totalOneThisRound = 0;

            int numZeroThisRound = 0;
            int numOneThisRound = 0;

            int value = 0;

            double alpha = 0.025;

            for (int i = 0; i < impulses[j].length; i++) {
                double random = RandomUtil.getInstance().nextDouble();
                if (value == 0 && random < alpha / 4.0) {
                    value = 1;
//                    System.out.println("Zero this round = " + zeroThisRound / (double) timeStepsPerSecond);
                    totalZeroThisRound += zeroThisRound / (double) timeStepsPerSecond;
                    numZeroThisRound++;
                    oneThisRound = 0;
                } else if (value == 1 && random < alpha) {
                    value = 0;
//                    System.out.println("One this round = " + oneThisRound / (double) timeStepsPerSecond);
                    totalOneThisRound += oneThisRound / (double) timeStepsPerSecond;
                    numOneThisRound++;
                    zeroThisRound = 0;
                }

                impulses[j][i] = value;

//                System.out.println(value);

                if (value == 0) {
                    zeroThisRound++;
                } else {
                    oneThisRound++;
                }
            }

            System.out.println();
            System.out.println("Average 0 this round = " + totalZeroThisRound / (double) numZeroThisRound);
            System.out.println("Average 1 this round = " + totalOneThisRound / (double) numOneThisRound);
        }


        DataSet fullDataSet = new ColtDataSet(sampleSize, continuousVariables);

        final Context context = new Context() {
            public Double getValue(String term) {
                Double value = parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                }

                throw new IllegalArgumentException("No value recorded for '" + term + "'");
            }
        };

        // Do the simulation.
        for (int row = 0; row < sampleSize; row++) {
            if (row == 0) {
                for (int i = 0; i < variableNodes.size(); i++) {
                    Node variable = variableNodes.get(i);
                    Node error = pm.getErrorNode(variable);

                    Expression expression = pm.getNodeExpression(error);
                    double errorValue = expression.evaluate(context);
//                    errorValue += impulses[variableNodes.indexOf(variable)][row];

                    if (Double.isNaN(errorValue)) {
                        throw new IllegalArgumentException("Undefined value for expression: " + expression);
                    }

//                    errorValue = Math.max(0, errorValue);

                    fullDataSet.setDouble(row, i, errorValue);
                    variableValues.put(variable.getName(), errorValue);
                }
            } else {
                for (int i = 0; i < variableNodes.size(); i++) {
                    Node variable = variableNodes.get(i);

                    Node error = pm.getErrorNode(variable);

                    Expression errorExpression = pm.getNodeExpression(error);
                    double errorValue = errorExpression.evaluate(context);
                    errorValue += impulses[variableNodes.indexOf(variable)][row];

                    if (Double.isNaN(errorValue)) {
                        throw new IllegalArgumentException("Undefined value for expression: " + errorExpression);
                    }

                    variableValues.put(error.getName(), errorValue);

                    double value = 0; //variableValues.get(variable.getName());

                    selfLoopCoef = -.05;
                    double delta = 0.05;

                    // sl * z0
                    if (!Double.isNaN(selfLoopCoef) && row > 0) {
                        double v = sigma * selfLoopCoef;
                        value += v * fullDataSet.getDouble(row - 1, i);
                    }

                    // sigma * A z0 + C u0
                    Expression expression = pm.getNodeExpression(variable);
                    value += expression.evaluate(context);

                    // z = sigma * A z + C u + z0
                    value += variableValues.get(variable.getName());

                    value *= delta; //timeStepsPerSecond;

//                    value = Math.max(0, value);

                    if (Double.isNaN(value)) {
                        throw new IllegalArgumentException("Undefined value for expression: " + expression);
                    }

                    fullDataSet.setDouble(row, i, value);
                    variableValues.put(variable.getName(), value);
                }

                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), fullDataSet.getDouble(row, i));
                }
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }

    }


    TetradVector simulateOneRecord(TetradVector e) {
        final Map<String, Double> variableValues = new HashMap<String, Double>();

        final List<Node> variableNodes = pm.getVariableNodes();

        final Context context = new Context() {
            public Double getValue(String term) {
                Double value = parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                }

                throw new IllegalArgumentException("No value recorded for '" + term + "'");
            }
        };

        // Take random draws from error distributions.
        for (int i = 0; i < variableNodes.size(); i++) {
            Node error = pm.getErrorNode(variableNodes.get(i));
            variableValues.put(error.getName(), e.get(i));
        }

        // Set the variable nodes to zero.
        for (Node variable : variableNodes) {
            variableValues.put(variable.getName(), 0.0);// RandomUtil.getInstance().nextUniform(-5, 5));
        }

        // Repeatedly update variable values until one of them hits infinity or negative infinity or
        // convergence within delta.

        double delta = 1e-6;
        int count = -1;

        while (true && ++count < 10000) {
            double[] values = new double[variableNodes.size()];

            for (int i = 0; i < values.length; i++) {
                Node node = variableNodes.get(i);
                Expression expression = pm.getNodeExpression(node);
                double value = expression.evaluate(context);
                values[i] = value;
            }

            boolean allInRange = true;

            for (int i = 0; i < values.length; i++) {
                Node node = variableNodes.get(i);

                if (!(Math.abs(variableValues.get(node.getName()) - values[i]) < delta)) {
                    allInRange = false;
                    break;
                }
            }


            for (int i = 0; i < variableNodes.size(); i++) {
                variableValues.put(variableNodes.get(i).getName(), values[i]);
            }

            if (allInRange) {
                break;
            }
        }

        TetradVector _case = new TetradVector(e.size());

        for (int i = 0; i < variableNodes.size(); i++) {
            double value = variableValues.get(variableNodes.get(i).getName());
            _case.set(i, value);
        }

        return _case;
    }

    public DataSet simulateDataNSteps(int sampleSize, boolean latentDataSaved) {
        final Map<String, Double> variableValues = new HashMap<String, Double>();

        List<Node> continuousVariables = new LinkedList<Node>();
        final List<Node> variableNodes = pm.getVariableNodes();

        // Work with a copy of the variables, because their type can be set externally.
        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());

            if (var.getNodeType() != NodeType.ERROR) {
                continuousVariables.add(var);
            }
        }

        DataSet fullDataSet = new ColtDataSet(sampleSize, continuousVariables);

        final Context context = new Context() {
            public Double getValue(String term) {
                Double value = parameterValues.get(term);

                if (value != null) {
                    return value;
                }

                value = variableValues.get(term);

                if (value != null) {
                    return value;
                }

                throw new IllegalArgumentException("No value recorded for '" + term + "'");
            }
        };

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Take random draws from error distributions.
            for (Node variable : variableNodes) {
                Node error = pm.getErrorNode(variable);

                Expression expression = pm.getNodeExpression(error);
                double value = expression.evaluate(context);

                if (Double.isNaN(value)) {
                    throw new IllegalArgumentException("Undefined value for expression: " + expression);
                }

                variableValues.put(error.getName(), value);
            }

            // Set the variable nodes to zero.
            for (Node variable : variableNodes) {
                variableValues.put(variable.getName(), 0.0);// RandomUtil.getInstance().nextUniform(-5, 5));
            }

            // Repeatedly update variable values until one of them hits infinity or negative infinity or
            // convergence within delta.

            for (int m = 0; m < 1; m++) {
                double[] values = new double[variableNodes.size()];

                for (int i = 0; i < values.length; i++) {
                    Node node = variableNodes.get(i);
                    Expression expression = pm.getNodeExpression(node);
                    double value = expression.evaluate(context);

                    if (Double.isNaN(value)) {
                        throw new IllegalArgumentException("Undefined value for expression: " + expression);
                    }

                    values[i] = value;
                }

                for (double value : values) {
                    if (value == Double.POSITIVE_INFINITY || value == Double.NEGATIVE_INFINITY) {
                        row--;
                        continue ROW;
                    }
                }

                for (int i = 0; i < variableNodes.size(); i++) {
                    variableValues.put(variableNodes.get(i).getName(), values[i]);
                }

            }

            for (int i = 0; i < variableNodes.size(); i++) {
                double value = variableValues.get(variableNodes.get(i).getName());
                fullDataSet.setDouble(row, i, value);
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }

    }


    public GeneralizedSemPm getSemPm() {
        return new GeneralizedSemPm(pm);
    }

    public void setSubstitutions(Map<String, Double> parameterValues) {
        for (String parameter : parameterValues.keySet()) {
            if (this.parameterValues.keySet().contains(parameter)) {
                this.parameterValues.put(parameter, parameterValues.get(parameter));
            }
        }
    }

    public boolean isSimulatePositiveDataOnly() {
        return simulatePositiveDataOnly;
    }

    public void setSimulatePositiveDataOnly(boolean simulatedPositiveDataOnly) {
        this.simulatePositiveDataOnly = simulatedPositiveDataOnly;
    }

    public void setSelfLoop(double selfLoop) {
        this.selfLoopCoef = selfLoop;
    }
}

