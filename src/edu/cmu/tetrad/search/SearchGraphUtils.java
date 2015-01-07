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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.CombinationGenerator;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.collections.map.MultiKeyMap;

import java.util.*;

/**
 * Graph utilities for search algorithms. Lots of orientation method, for instance.
 *
 * @author Joseph Ramsey
 */
public final class SearchGraphUtils {

    /**
     * Orients according to background knowledge.
     */
    public static void pcOrientbk(IKnowledge bk, Graph graph, List<Node> nodes) {
        TetradLogger.getInstance().log("details", "Staring BK Orientation.");
        for (Iterator<KnowledgeEdge> it = bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = translate(edge.getFrom(), nodes);
            Node to = translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to-->from
            graph.removeEdge(from, to);
            graph.addDirectedEdge(to, from);
//            graph.setEndpoint(from, to, Endpoint.TAIL);
//            graph.setEndpoint(to, from, Endpoint.ARROW);

            TetradLogger.getInstance().log("knowledgeOrientations", SearchLogUtils.edgeOrientedMsg("IKnowledge", graph.getEdge(to, from)));
        }

        for (Iterator<KnowledgeEdge> it = bk.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = translate(edge.getFrom(), nodes);
            Node to = translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient from-->to
            graph.removeEdge(from, to);
            graph.addDirectedEdge(from, to);

//            graph.setEndpoint(to, from, Endpoint.TAIL);
//            graph.setEndpoint(from, to, Endpoint.ARROW);
            TetradLogger.getInstance().log("knowledgeOrientations", SearchLogUtils.edgeOrientedMsg("IKnowledge", graph.getEdge(from, to)));
        }


        TetradLogger.getInstance().log("details", "Finishing BK Orientation.");
    }

    /**
     * Performs step C of the algorithm, as indicated on page xxx of CPS, with the modification that X--W--Y is oriented
     * as X-->W<--Y if W is *determined by* the sepset of (X, Y), rather than W just being *in* the sepset of (X, Y).
     */
    public static void pcdOrientC(SepsetMap set, IndependenceTest test,
                                  IKnowledge knowledge, Graph graph) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        List<Node> nodes = graph.getNodes();

        for (Node y : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(y);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(x, z)) {
                    continue;
                }

                List<Node> sepset = set.get(x, z);

                if (sepset == null) {
                    continue;
                }

                List<Node> augmentedSet = new LinkedList<Node>(sepset);
                augmentedSet.add(y);

//                if (test.splitDetermines(Collections.singletonList(y), x, z)) {
//                    continue;
//                }

                if (test.determines(sepset, y)) {
                    TetradLogger.getInstance().log("info", sepset + " determines " + y);
                    continue;
                }

                boolean splitDeterminexSepsetXZ = test.determines(sepset, x) || test.determines(sepset, z);
                boolean splitDeterminesAugmentedSetXZ = test.determines(augmentedSet, x) || test.determines(augmentedSet, z);

                if (!splitDeterminexSepsetXZ && splitDeterminesAugmentedSetXZ) {
                    TetradLogger.getInstance().log("info", sepset + " split determines " + x + " and " + z);
                    continue;
                }

                if (!isArrowpointAllowed(x, y, knowledge) ||
                        !isArrowpointAllowed(z, y, knowledge)) {
                    continue;
                }

                graph.setEndpoint(x, y, Endpoint.ARROW);
                graph.setEndpoint(z, y, Endpoint.ARROW);

                TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    //    /**
//     * Performs step D of the algorithm, as indicated on page xxx of CPS. This
//     * method should be called again if it returns true.
//     *
//     * <pre>
//     * Meek-Orient(G, t)
//     * 1.while orientations can be made, for arbitrary a, b, c, and d:
//     * 2.    If a --> b, b --> c, a not in adj(c), and Is-Noncollider(a, b, c) then orient b --> c.
//     * 3.    If a --> b, b --> c, a --- c, then orient a --> c.
//     * 4.    If a --- b, a --- c, a --- d, c --> b, d --> b, then orient a --> b.
//     * 5.    If a --> b, b in adj(d) a in adj(c), a --- d, b --> c, c --> d, then orient a --> d.
//     * </pre>
//     */
//    public static void orientUsingMeekRules(IKnowledge knowledge, Graph graph) {
//        LogUtils.getInstance().info("Starting Orientation Step D.");
//        boolean changed;
//
//        do {
//            changed = meekR1(graph, knowledge) || meekR2(graph, knowledge) ||
//                    meekR3(graph, knowledge) || meekR4(graph, knowledge);
//        } while (changed);
//
//        LogUtils.getInstance().info("Finishing Orientation Step D.");
//    }

    /**
     * Orients using Meek rules, double checking noncolliders locally.
     */
    public static void orientUsingMeekRulesLocally(IKnowledge knowledge,
                                                   Graph graph, IndependenceTest test, int depth) {
        TetradLogger.getInstance().log("info", "Starting Orientation Step D.");
        boolean changed;

        do {
            changed = meekR1Locally(graph, knowledge, test, depth) ||
                    meekR2(graph, knowledge) || meekR3(graph, knowledge) ||
                    meekR4(graph, knowledge);
        } while (changed);

        TetradLogger.getInstance().log("info", "Finishing Orientation Step D.");
    }

    public static void orientUsingMeekRulesLocally2(IKnowledge knowledge,
                                                    Graph graph, IndependenceTest test, int depth) {
        TetradLogger.getInstance().log("info", "Starting Orientation Step D.");
        boolean changed;

        do {
            changed = meekR1Locally2(graph, knowledge, test, depth) ||
                    meekR2(graph, knowledge) || meekR3(graph, knowledge) ||
                    meekR4(graph, knowledge);
        } while (changed);

        TetradLogger.getInstance().log("info", "Finishing Orientation Step D.");
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients x *-* y *-* z as x *-> y <-* z just in
     * case y is in Sepset({x, z}).
     */
    public static void orientCollidersUsingSepsets(SepsetMap set,
                                                   IKnowledge knowledge, Graph graph, boolean verbose) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");

//        verifySepsetIntegrity(set, graph);

        List<Node> nodes = graph.getNodes();

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);
//            Collections.sort(adjacentNodes);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(b, c)) {
                    continue;
                }

                // Skip triple already oriented.
                if (graph.getEdge(a, b).pointsTowards(a) && graph.getEdge(a, c).pointsTowards(a)) {
                    continue;
                }

                List<Node> sepset = set.get(b, c);

                if (sepset != null && !sepset.contains(a) &&
                        isArrowpointAllowed(b, a, knowledge) &&
                        isArrowpointAllowed(c, a, knowledge)) {
                    if (verbose) {
                        System.out.println("Collider orientation <" + b + ", " + a + ", " + c + "> sepset = " + sepset);
                    }

                    graph.setEndpoint(b, a, Endpoint.ARROW);
                    graph.setEndpoint(c, a, Endpoint.ARROW);
                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(b, a, c, sepset));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");
    }

    // Tests whether adding a for b--a--c to the sepset (if it's not there) yields independence. Poor man's CPC.
    public static void orientCollidersUsingSepsets(SepsetMap set,
                                                   IKnowledge knowledge, Graph graph,
                                                   IndependenceTest test) {

//        // False just in case a should be tried in the sepset of b and c.
//        if (false) {
//            orientCollidersUsingSepsets(set, knowledge, graph);
//        }

        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");

//        verifySepsetIntegrity(set, graph);

        List<Node> nodes = graph.getNodes();

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);
            Collections.sort(adjacentNodes);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);


                // Skip triples that are shielded.
                if (graph.isAdjacentTo(b, c)) {
                    continue;
                }

                List<Node> sepset = set.get(b, c);

                List<Node> sepset2 = new ArrayList<Node>(sepset);

                if (!sepset2.contains(a)) {
                    System.out.println("\nADDING " + a);

                    sepset2.add(a);
                    double alpha = test.getAlpha();
                    test.setAlpha(alpha);

                    if (test.isIndependent(b, c, sepset2)) {
                        sepset = sepset2;
                    }
                }

                if (!sepset.contains(a) &&
                        isArrowpointAllowed(b, a, knowledge) &&
                        isArrowpointAllowed(c, a, knowledge)) {
                    System.out.println("Collider orientation <" + b + ", " + a + ", " + c + "> sepset = " + sepset);

                    graph.setEndpoint(b, a, Endpoint.ARROW);
                    graph.setEndpoint(c, a, Endpoint.ARROW);
                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(b, a, c, sepset));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");
    }

    public static void orientCollidersUsingSepsets2(SepsetMap set,
                                                    IKnowledge knowledge, Graph graph, Graph trueGraph) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");

//        verifySepsetIntegrity(set, graph);

        List<Node> nodes = graph.getNodes();

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

//                System.out.println("Collider orientation <b, a, c> = <" + b + ", " + a + ", " + c + ">");

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(b, c)) {
                    continue;
                }

                List<Node> sepset = set.get(b, c);

                if (graph.getEdge(b, a).getProximalEndpoint(a) == Endpoint.ARROW && graph.getEdge(c, a).getProximalEndpoint(a) == Endpoint.ARROW) {
                    Edge edge1 = trueGraph.getEdge(b, a);
                    Edge edge2 = trueGraph.getEdge(c, a);

                    System.out.println(edge1 + " " + edge2);

                    if (edge1 == null || edge2 == null) {
                        System.out.println("*** " + graph.getEdge(b, a) + " " + graph.getEdge(c, a) + " " + trueGraph);
                    }

                    if (!(edge1.getProximalEndpoint(a) == Endpoint.ARROW && edge2.getProximalEndpoint(a) == Endpoint.ARROW)) {
                        System.out.println("Sepset(" + b + ", " + c + ") = " + sepset + " a = " + a);
                    }
                }

                if (sepset != null && !sepset.contains(a) &&
                        isArrowpointAllowed(b, a, knowledge) &&
                        isArrowpointAllowed(c, a, knowledge)) {

                    graph.setEndpoint(b, a, Endpoint.ARROW);
                    graph.setEndpoint(c, a, Endpoint.ARROW);
                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(b, a, c, sepset));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");
    }


    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients x *-* y *-* z as x *-> y <-* z just in
     * case y is in Sepset({x, z}).
     */
    public static void orientCollidersUsingSepsets4(SepsetMap set,
                                                    IKnowledge knowledge, Graph graph, IndependenceTest test) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");

//        verifySepsetIntegrity(set, graph);

        List<Node> nodes = graph.getNodes();

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);
            Collections.sort(adjacentNodes);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);


                // Skip triples that are shielded.
                if (graph.isAdjacentTo(b, c)) {
                    continue;
                }

                List<Node> sepset = set.get(b, c);
                List<Node> sepset2;

                if (sepset != null) {
                    sepset2 = new ArrayList<Node>(sepset);
                    if (!sepset2.contains(a)) {
                        sepset2.add(a);
                    }
                    if (test.isIndependent(b, c, sepset2)) {
                        sepset = sepset2;
                    }
                }

                if (sepset != null && !sepset.contains(a) &&
                        isArrowpointAllowed(b, a, knowledge) &&
                        isArrowpointAllowed(c, a, knowledge)) {
//                    System.out.println("Collider orientation <" + b + ", " + a + ", " + c + "> sepset = " + sepset);

                    graph.setEndpoint(b, a, Endpoint.ARROW);
                    graph.setEndpoint(c, a, Endpoint.ARROW);
                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(b, a, c, sepset));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");
    }

    public static void orientCollidersUsingSepsets5(SepsetMap set,
                                                    IKnowledge knowledge, Graph graph,
                                                    IndependenceTest test) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");

        LinkedList<Double> pValues = new LinkedList<Double>();
        Map<Double, Triple> triples = new HashMap<Double, Triple>();
        Map<Triple, List<Node>> sepsets = new HashMap<Triple, List<Node>>();

        List<Node> nodes = graph.getNodes();

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);
            Collections.sort(adjacentNodes);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);


                // Skip triples that are shielded.
                if (graph.isAdjacentTo(b, c)) {
                    continue;
                }

                List<Node> sepset = set.get(b, c);

                test.isIndependent(b, c, sepset);
                double p = test.getPValue();

                if (sepset != null && !sepset.contains(a) &&
                        isArrowpointAllowed(b, a, knowledge) &&
                        isArrowpointAllowed(c, a, knowledge)) {
                    pValues.add(p);
                    Triple triple = new Triple(b, a, c);
                    triples.put(p, triple);
                    sepsets.put(triple, sepset);

//                    graph.setEndpoint(b, a, Endpoint.ARROW);
//                    graph.setEndpoint(c, a, Endpoint.ARROW);
//                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(b, a, c, sepset));
                }
            }
        }

        Collections.sort(pValues);

        for (int i = pValues.size() - 1; i >= pValues.size() / 2; i--) {
//        while (!pValues.isEmpty()) {
//            double p = pValues.removeLast();
            double p = pValues.get(i);
            Triple triple = triples.get(p);
            List<Node> sepset = sepsets.get(triple);

            Node b = triple.getX();
            Node a = triple.getY();
            Node c = triple.getZ();

//            if (!(graph.getEndpoint(a, b) == Endpoint.ARROW
//                    || graph.getEndpoint(a, c) == Endpoint.ARROW)) {
            graph.setEndpoint(b, a, Endpoint.ARROW);
            graph.setEndpoint(c, a, Endpoint.ARROW);
            System.out.println("Collider orientation <" + b + ", " + a + ", " + c + "> sepset = " + sepset);
            TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(b, a, c, sepset));
//            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");
    }


    public static void orientCollidersLocally(IKnowledge knowledge, Graph graph,
                                              IndependenceTest test,
                                              int depth) {
        orientCollidersLocally(knowledge, graph, test, depth, null);
    }

    public static void orientCollidersLocally(IKnowledge knowledge, Graph graph,
                                              IndependenceTest test,
                                              int depth, Set<Node> nodesToVisit) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");

        if (nodesToVisit == null) {
            nodesToVisit = new HashSet<Node>(graph.getNodes());
        }

        for (Node a : nodesToVisit) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(b, c)) {
                    continue;
                }

                if (isArrowpointAllowed1(b, a, knowledge) &&
                        isArrowpointAllowed1(c, a, knowledge)) {
                    if (!existsLocalSepsetWith(b, a, c, test, graph, depth)) {
                        graph.setEndpoint(b, a, Endpoint.ARROW);
                        graph.setEndpoint(c, a, Endpoint.ARROW);
                        TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(b, a, c));
                    }
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");
    }

    /**
     * Performs step C of the algorithm, as indicated on page xxx of CPS
     */
    public static void orientCollidersLocallyDet(IKnowledge knowledge, Graph graph,
                                                 IndependenceTest test, int depth) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        List<Node> nodes = graph.getNodes();

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(b, c)) {
                    continue;
                }

                if (isArrowpointAllowed1(b, a, knowledge) &&
                        isArrowpointAllowed1(c, a, knowledge)) {
                    if (existsLocalSepsetWithoutDet(b, a, c, test, graph,
                            depth)) {
                        graph.setEndpoint(b, a, Endpoint.ARROW);
                        graph.setEndpoint(c, a, Endpoint.ARROW);
                        TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(b, a, c));
                    }
                }
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    public static boolean existsLocalSepsetWith(Node x, Node y, Node z,
                                                IndependenceTest test, Graph graph, int depth) {
        Set<Node> __nodes = new HashSet<Node>(graph.getAdjacentNodes(x));
        __nodes.addAll(graph.getAdjacentNodes(z));
        __nodes.remove(x);
        __nodes.remove(z);

        List<Node> _nodes = new LinkedList<Node>(__nodes);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 1; d <= _depth; d++) {
            if (_nodes.size() >= d) {
                ChoiceGenerator cg2 = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg2.next()) != null) {
                    List<Node> condSet = GraphUtils.asList(choice, _nodes);

                    if (!condSet.contains(y)) {
                        continue;
                    }

//                    LogUtils.getInstance().finest("Trying " + condSet);

                    if (test.isIndependent(x, z, condSet)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean existsLocalSepsetWithout(Node x, Node y, Node z,
                                                   IndependenceTest test, Graph graph, int depth) {
        Set<Node> __nodes = new HashSet<Node>(graph.getAdjacentNodes(x));
        __nodes.addAll(graph.getAdjacentNodes(z));
        __nodes.remove(x);
        __nodes.remove(z);
        List<Node> _nodes = new LinkedList<Node>(__nodes);
        TetradLogger.getInstance().log("adjacencies",
                "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            if (_nodes.size() >= d) {
                ChoiceGenerator cg2 = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg2.next()) != null) {
                    List<Node> condSet = GraphUtils.asList(choice, _nodes);

                    if (condSet.contains(y)) {
                        continue;
                    }

                    //            LogUtils.getInstance().finest("Trying " + condSet);

                    if (test.isIndependent(x, z, condSet)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean existsLocalSepsetWithoutDet(Node x, Node y, Node z,
                                                      IndependenceTest test, Graph graph, int depth) {
        Set<Node> __nodes = new HashSet<Node>(graph.getAdjacentNodes(x));
        __nodes.addAll(graph.getAdjacentNodes(z));
        __nodes.remove(x);
        __nodes.remove(z);
        List<Node> _nodes = new LinkedList<Node>(__nodes);
        TetradLogger.getInstance().log("adjacencies",
                "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            if (_nodes.size() >= d) {
                ChoiceGenerator cg2 = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg2.next()) != null) {
                    List<Node> condSet = GraphUtils.asList(choice, _nodes);

                    if (condSet.contains(y)) {
                        continue;
                    }

                    if (test.determines(condSet, y)) {
                        continue;
                    }

                    //        LogUtils.getInstance().finest("Trying " + condSet);

                    if (test.isIndependent(x, z, condSet)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

//    /**
//     * Meek's rule R1 (same as CPS).
//     */
//    public static boolean meekR1(Graph graph, IKnowledge knowledge) {
//        List<Node> nodes = graph.getNodes();
//        boolean changed = true;
//
//        while (changed) {
//            changed = false;
//
//            for (Node a : nodes) {
//                List<Node> adjacentNodes = graph.getAdjacentNodes(a);
//
//                if (adjacentNodes.size() < 2) {
//                    continue;
//                }
//
//                ChoiceGenerator cg =
//                        new ChoiceGenerator(adjacentNodes.size(), 2);
//                int[] combination;
//
//                while ((combination = cg.next()) != null) {
//                    Node b = adjacentNodes.get(combination[0]);
//                    Node c = adjacentNodes.get(combination[1]);
//
//                    // Skip triples that are shielded.
//                    if (graph.isAdjacentTo(b, c)) {
//                        continue;
//                    }
//
//                    if (graph.getEndpoint(b, a) == Endpoint.ARROW &&
//                            graph.isUndirectedFromTo(a, c)) {
//                        if (isArrowpointAllowed(a, c, knowledge)) {
//                            graph.setEndpoint(a, c, Endpoint.ARROW);
//                            SearchLogUtils.logEdgeOriented("Meek R1",
//                                    graph.getEdge(a, c), LOGGER);
//                            changed = true;
//                        }
//                    }
//                    else if (graph.getEndpoint(c, a) == Endpoint.ARROW &&
//                            graph.isUndirectedFromTo(a, b)) {
//                        if (isArrowpointAllowed(a, b, knowledge)) {
//                            graph.setEndpoint(a, b, Endpoint.ARROW);
//                            SearchLogUtils.logEdgeOriented("Meek R1",
//                                    graph.getEdge(a, b), LOGGER);
//                            changed = true;
//                        }
//                    }
//                }
//            }
//        }
//
//        return changed;
//    }

    /**
     * Orient away from collider.
     */
    public static boolean meekR1Locally(Graph graph, IKnowledge knowledge,
                                        IndependenceTest test, int depth) {
        List<Node> nodes = graph.getNodes();
        boolean changed = true;

        while (changed) {
            changed = false;

            for (Node a : nodes) {
                List<Node> adjacentNodes = graph.getAdjacentNodes(a);

                if (adjacentNodes.size() < 2) {
                    continue;
                }

                ChoiceGenerator cg =
                        new ChoiceGenerator(adjacentNodes.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    Node b = adjacentNodes.get(combination[0]);
                    Node c = adjacentNodes.get(combination[1]);

                    // Skip triples that are shielded.
                    if (graph.isAdjacentTo(b, c)) {
                        continue;
                    }

                    if (graph.getEndpoint(b, a) == Endpoint.ARROW &&
                            graph.isUndirectedFromTo(a, c)) {
                        if (existsLocalSepsetWithout(b, a, c, test, graph,
                                depth)) {
                            continue;
                        }

                        if (isArrowpointAllowed(a, c, knowledge)) {
                            graph.setEndpoint(a, c, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R1", graph.getEdge(a, c)));
                            changed = true;
                        }
                    } else if (graph.getEndpoint(c, a) == Endpoint.ARROW &&
                            graph.isUndirectedFromTo(a, b)) {
                        if (existsLocalSepsetWithout(b, a, c, test, graph,
                                depth)) {
                            continue;
                        }

                        if (isArrowpointAllowed(a, b, knowledge)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R1", graph.getEdge(a, b)));
                            changed = true;
                        }
                    }
                }
            }
        }

        return changed;
    }

    public static boolean meekR1Locally2(Graph graph, IKnowledge knowledge,
                                         IndependenceTest test, int depth) {
        List<Node> nodes = graph.getNodes();
        boolean changed = true;

        while (changed) {
            changed = false;

            for (Node a : nodes) {
                List<Node> adjacentNodes = graph.getAdjacentNodes(a);

                if (adjacentNodes.size() < 2) {
                    continue;
                }

                ChoiceGenerator cg =
                        new ChoiceGenerator(adjacentNodes.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    Node b = adjacentNodes.get(combination[0]);
                    Node c = adjacentNodes.get(combination[1]);

                    // Skip triples that are shielded.
                    if (graph.isAdjacentTo(b, c)) {
                        continue;
                    }

                    if (graph.getEndpoint(b, a) == Endpoint.ARROW &&
                            graph.isUndirectedFromTo(a, c)) {
                        if (existsLocalSepsetWithoutDet(b, a, c, test, graph,
                                depth)) {
                            continue;
                        }

                        if (isArrowpointAllowed(a, c, knowledge)) {
                            graph.setEndpoint(a, c, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R1", graph.getEdge(a, c)));
                            changed = true;
                        }
                    } else if (graph.getEndpoint(c, a) == Endpoint.ARROW &&
                            graph.isUndirectedFromTo(a, b)) {
                        if (existsLocalSepsetWithoutDet(b, a, c, test, graph,
                                depth)) {
                            continue;
                        }

                        if (isArrowpointAllowed(a, b, knowledge)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R1", graph.getEdge(a, b)));
                            changed = true;
                        }
                    }
                }
            }
        }

        return changed;
    }

    /**
     * If
     */
    public static boolean meekR2(Graph graph, IKnowledge knowledge) {
        List<Node> nodes = graph.getNodes();
        boolean changed = false;

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (graph.isDirectedFromTo(b, a) &&
                        graph.isDirectedFromTo(a, c) &&
                        graph.isUndirectedFromTo(b, c)) {
                    if (isArrowpointAllowed(b, c, knowledge)) {
                        graph.setEndpoint(b, c, Endpoint.ARROW);
                        TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(b, c)));
                    }
                } else if (graph.isDirectedFromTo(c, a) &&
                        graph.isDirectedFromTo(a, b) &&
                        graph.isUndirectedFromTo(c, b)) {
                    if (isArrowpointAllowed(c, b, knowledge)) {
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                        TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(c, b)));
                    }
                }
            }
        }

        return changed;
    }

    /**
     * Meek's rule R3. If a--b, a--c, a--d, c-->b, c-->b, then orient a-->b.
     */
    public static boolean meekR3(Graph graph, IKnowledge knowledge) {

        List<Node> nodes = graph.getNodes();
        boolean changed = false;

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 3) {
                continue;
            }

            for (Node b : adjacentNodes) {
                List<Node> otherAdjacents = new LinkedList<Node>(adjacentNodes);
                otherAdjacents.remove(b);

                if (!graph.isUndirectedFromTo(a, b)) {
                    continue;
                }

                ChoiceGenerator cg =
                        new ChoiceGenerator(otherAdjacents.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    Node c = otherAdjacents.get(combination[0]);
                    Node d = otherAdjacents.get(combination[1]);

                    if (graph.isAdjacentTo(c, d)) {
                        continue;
                    }

                    if (!graph.isUndirectedFromTo(a, c)) {
                        continue;
                    }

                    if (!graph.isUndirectedFromTo(a, d)) {
                        continue;
                    }

                    if (graph.isDirectedFromTo(c, b) &&
                            graph.isDirectedFromTo(d, b)) {
                        if (isArrowpointAllowed(a, b, knowledge)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R3", graph.getEdge(a, b)));
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }

        return changed;
    }

    public static boolean meekR4(Graph graph, IKnowledge knowledge) {
        if (knowledge == null) {
            return false;
        }

        List<Node> nodes = graph.getNodes();
        boolean changed = false;

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 3) {
                continue;
            }

            for (Node d : adjacentNodes) {
                if (!graph.isAdjacentTo(a, d)) {
                    continue;
                }

                List<Node> otherAdjacents = new LinkedList<Node>(adjacentNodes);
                otherAdjacents.remove(d);

                ChoiceGenerator cg =
                        new ChoiceGenerator(otherAdjacents.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    Node b = otherAdjacents.get(combination[0]);
                    Node c = otherAdjacents.get(combination[1]);

                    if (!graph.isUndirectedFromTo(a, b)) {
                        continue;
                    }

                    if (!graph.isUndirectedFromTo(a, c)) {
                        continue;
                    }

//                    if (!isUnshieldedNoncollider(c, a, b, graph)) {
//                        continue;
//                    }

                    if (graph.isDirectedFromTo(b, c) &&
                            graph.isDirectedFromTo(d, c)) {
                        if (isArrowpointAllowed(a, c, knowledge)) {
                            graph.setEndpoint(a, c, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek T1", graph.getEdge(a, c)));
                            changed = true;
                            break;
                        }
                    } else if (graph.isDirectedFromTo(c, d) &&
                            graph.isDirectedFromTo(d, b)) {
                        if (isArrowpointAllowed(a, b, knowledge)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek T1", graph.getEdge(a, b)));
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }

        return changed;
    }

//    /**
//     *
//     */
//    public static boolean meekR4(Graph graph, IKnowledge knowledge) {
//        if (knowledge == null) {
//            return false;
//        }
//
//        List<Node> nodes = graph.getNodes();
//        boolean changed = false;
//
//        for (Node a : nodes) {
//            List<Node> adjacentNodes = graph.getAdjacentNodes(a);
//
//            if (adjacentNodes.size() < 3) {
//                continue;
//            }
//
//            for (Node d: adjacentNodes) {
//                if (!graph.isUndirectedFromTo(a, d)) {
//                    continue;
//                }
//
//                List<Node> otherAdjacents = new LinkedList<Node>(adjacentNodes);
//                otherAdjacents.remove(d);
//
//                ChoiceGenerator cg =
//                        new ChoiceGenerator(otherAdjacents.size(), 2);
//                int[] combination;
//
//                while ((combination = cg.next()) != null) {
//                    Node b = otherAdjacents.get(combination[0]);
//                    Node c = otherAdjacents.get(combination[1]);
//
//                    if (graph.isAdjacentTo(b, d)) {
//                        continue;
//                    }
//
//                    if (!graph.isUndirectedFromTo(a, b)) {
//                        continue;
//                    }
//
//                    if (!graph.isAdjacentTo(a, c)) {
//                        continue;
//                    }
//
////                    if (!(graph.isUndirectedFromTo(a, c) ||
////                            graph.isDirectedFromTo(a, c) ||
////                            graph.isDirectedFromTo(c, a))) {
////                        continue;
////                    }
//
//                    if (graph.isDirectedFromTo(b, c) &&
//                            graph.isDirectedFromTo(c, d)) {
//                        if (isArrowpointAllowed(a, d, knowledge)) {
//                            graph.setEndpoint(a, d, Endpoint.ARROW);
//                            SearchLogUtils.logEdgeOriented("Meek T1",
//                                    graph.getEdge(a, d), LOGGER);
//                            changed = true;
//                            break;
//                        }
//                    }
//                    else if (graph.isDirectedFromTo(d, c) &&
//                            graph.isDirectedFromTo(c, b)) {
//                        if (isArrowpointAllowed(a, b, knowledge)) {
//                            graph.setEndpoint(a, b, Endpoint.ARROW);
//                            SearchLogUtils.logEdgeOriented("Meek T1",
//                                    graph.getEdge(a, b), LOGGER);
//                            changed = true;
//                            break;
//                        }
//                    }
//                }
//            }
//        }
//
//        return changed;
//    }

    /**
     * Checks if an arrowpoint is allowed by background knowledge.
     */
    public static boolean isArrowpointAllowed(Object from, Object to,
                                              IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }
        return !knowledge.edgeRequired(to.toString(), from.toString()) &&
                !knowledge.edgeForbidden(from.toString(), to.toString());
    }

    /**
     * Transforms a maximally directed pattern (PDAG) represented in graph <code>g</code> into an arbitrary DAG by
     * modifying <code>g</code> itself. Based on the algorithm described in </p> Chickering (2002) "Optimal structure
     * identification with greedy search" Journal of Machine Learning Research. </p> R. Silva, June 2004
     */
    public static void pdagToDag(Graph g) {
        Graph p = new EdgeListGraph(g);
        List<Edge> undirectedEdges = new ArrayList<Edge>();

        for (Edge edge : g.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.TAIL &&
                    edge.getEndpoint2() == Endpoint.TAIL &&
                    !undirectedEdges.contains(edge)) {
                undirectedEdges.add(edge);
            }
        }
        g.removeEdges(undirectedEdges);
        List<Node> pNodes = p.getNodes();

        do {
            Node x = null;

            for (Node pNode : pNodes) {
                x = pNode;

                if (p.getChildren(x).size() > 0) {
                    continue;
                }

                Set<Node> neighbors = new HashSet<Node>();

                for (Edge edge : p.getEdges()) {
                    if (edge.getNode1() == x || edge.getNode2() == x) {
                        if (edge.getEndpoint1() == Endpoint.TAIL &&
                                edge.getEndpoint2() == Endpoint.TAIL) {
                            if (edge.getNode1() == x) {
                                neighbors.add(edge.getNode2());
                            } else {
                                neighbors.add(edge.getNode1());
                            }
                        }
                    }
                }
                if (neighbors.size() > 0) {
                    Collection<Node> parents = p.getParents(x);
                    Set<Node> all = new HashSet<Node>(neighbors);
                    all.addAll(parents);
                    if (!GraphUtils.isClique(all, p)) {
                        continue;
                    }
                }

                for (Node neighbor : neighbors) {
                    Node node1 = g.getNode(neighbor.getName());
                    Node node2 = g.getNode(x.getName());

                    g.addDirectedEdge(node1, node2);
                }
                p.removeNode(x);
                break;
            }
            pNodes.remove(x);
        } while (pNodes.size() > 0);
    }

    /**
     * Get a graph and direct only the unshielded colliders.
     * @return the child nodes of unshielded colliders.
     */
    public static void basicPattern(Graph graph) {
        Set<Edge> undirectedEdges = new HashSet<Edge>();

        NEXT_EDGE:
        for (Edge edge : graph.getEdges()) {
            if (!edge.isDirected()) {
                continue;
            }

            Node x = edge.getNode1();
            Node y = edge.getNode2();

            for (Node parent : graph.getParents(y)) {
                if (parent != x) {
                    if (graph.isAdjacentTo(parent, x)) {
                        continue;
                    }

                    continue NEXT_EDGE;
                }
            }

            undirectedEdges.add(edge);
        }

        for (Edge nextUndirected : undirectedEdges) {
            Node node1 = nextUndirected.getNode1();
            Node node2 = nextUndirected.getNode2();

            graph.removeEdge(nextUndirected);
            graph.addUndirectedEdge(node1, node2);
        }
    }

    /**
     * Transforms a DAG represented in graph <code>graph</code> into a maximally directed pattern (PDAG) by modifying
     * <code>g</code> itself. Based on the algorithm described in </p> Chickering (2002) "Optimal structure
     * identification with greedy search" Journal of Machine Learning Research. It works for both BayesNets and SEMs.
     * </p> R. Silva, June 2004
     */
    public static void dagToPdag(Graph graph) {
        //do topological sort on the nodes
        Graph graphCopy = new EdgeListGraph(graph);
        Node orderedNodes[] = new Node[graphCopy.getNodes().size()];
        int count = 0;
        while (graphCopy.getNodes().size() > 0) {
            Set<Node> exogenousNodes = new HashSet<Node>();

            for (Node next : graphCopy.getNodes()) {
                if (graphCopy.isExogenous(next)) {
                    exogenousNodes.add(next);
                    orderedNodes[count++] = graph.getNode(next.getName());
                }
            }

            graphCopy.removeNodes(new ArrayList<Node>(exogenousNodes));
        }
        //ordered edges - improvised, inefficient implementation
        count = 0;
        Edge edges[] = new Edge[graph.getNumEdges()];
        boolean edgeOrdered[] = new boolean[graph.getNumEdges()];
        Edge orderedEdges[] = new Edge[graph.getNumEdges()];

        for (Edge edge : graph.getEdges()) {
            edges[count++] = edge;
        }

        for (int i = 0; i < edges.length; i++) {
            edgeOrdered[i] = false;
        }

        while (count > 0) {
            for (Node orderedNode : orderedNodes) {
                for (int k = orderedNodes.length - 1; k >= 0; k--) {
                    for (int q = 0; q < edges.length; q++) {
                        if (!edgeOrdered[q] &&
                                edges[q].getNode1() == orderedNodes[k] &&
                                edges[q].getNode2() == orderedNode) {
                            edgeOrdered[q] = true;
                            orderedEdges[orderedEdges.length - count] =
                                    edges[q];
                            count--;
                        }
                    }
                }
            }
        }

        //label edges
        boolean compelledEdges[] = new boolean[graph.getNumEdges()];
        boolean reversibleEdges[] = new boolean[graph.getNumEdges()];
        for (int i = 0; i < graph.getNumEdges(); i++) {
            compelledEdges[i] = false;
            reversibleEdges[i] = false;
        }
        for (int i = 0; i < graph.getNumEdges(); i++) {
            if (compelledEdges[i] || reversibleEdges[i]) {
                continue;
            }
            Node x = orderedEdges[i].getNode1();
            Node y = orderedEdges[i].getNode2();
            for (int j = 0; j < orderedEdges.length; j++) {
                if (orderedEdges[j].getNode2() == x && compelledEdges[j]) {
                    Node w = orderedEdges[j].getNode1();
                    if (!graph.isParentOf(w, y)) {
                        for (int k = 0; k < orderedEdges.length; k++) {
                            if (orderedEdges[k].getNode2() == y) {
                                compelledEdges[k] = true;
                                break;
                            }
                        }
                    } else {
                        for (int k = 0; k < orderedEdges.length; k++) {
                            if (orderedEdges[k].getNode1() == w &&
                                    orderedEdges[k].getNode2() == y) {
                                compelledEdges[k] = true;
                                break;
                            }
                        }
                    }
                }
                if (compelledEdges[i]) {
                    break;
                }
            }
            if (compelledEdges[i]) {
                continue;
            }
            boolean foundZ = false;

            for (Edge orderedEdge : orderedEdges) {
                Node z = orderedEdge.getNode1();
                if (z != x && orderedEdge.getNode2() == y &&
                        !graph.isParentOf(z, x)) {
                    compelledEdges[i] = true;
                    for (int k = i + 1; k < graph.getNumEdges(); k++) {
                        if (orderedEdges[k].getNode2() == y &&
                                !reversibleEdges[k]) {
                            compelledEdges[k] = true;
                        }
                    }
                    foundZ = true;
                    break;
                }
            }

            if (!foundZ) {
                reversibleEdges[i] = true;

                for (int j = i + 1; j < orderedEdges.length; j++) {
                    if (!compelledEdges[j] && orderedEdges[j].getNode2() == y) {
                        reversibleEdges[j] = true;
                    }
                }
            }
        }

        //undirect edges that are reversible
        for (int i = 0; i < reversibleEdges.length; i++) {
            if (reversibleEdges[i]) {
                graph.setEndpoint(orderedEdges[i].getNode1(),
                        orderedEdges[i].getNode2(), Endpoint.TAIL);
                graph.setEndpoint(orderedEdges[i].getNode2(),
                        orderedEdges[i].getNode1(), Endpoint.TAIL);
            }
        }
    }

    /**
     * Returns the pattern to which the given DAG belongs.
     */
    public static Graph patternFromDag(Graph dag) {
        Graph graph = new EdgeListGraph(dag);
        SearchGraphUtils.basicPattern(graph);
        MeekRules rules = new MeekRules();
        rules.orientImplied(graph);
        return graph;
    }

    public static Graph dagFromPattern(Graph pattern) {
        DagInPatternIterator dags = new DagInPatternIterator(pattern);
        return dags.next();

//        MeekRules rules = new MeekRules();
//        rules.orientImplied(graph);
//
//        WHILE:
//        while (true) {
//            List<Edge> edges = graph.getEdges();
//
//            for (Edge edge : edges) {
//                if (Edges.isUndirectedEdge(edge)) {
//                    Node node1 = edge.getNode1();
//                    Node node2 = edge.getNode2();
//
//                    if (!(pattern.isAncestorOf(node2, node1)) && !pattern.getParents(node2).isEmpty()) {
//                        edge.setEndpoint2(Endpoint.ARROW);
//                    }
//                    else if (!pattern.getParents(node1).isEmpty()) {
//                        edge.setEndpoint1(Endpoint.ARROW);
//                    }
//                    else {
//                        throw new IllegalArgumentException("Can't orient " + edge);
//                    }
//
//                    rules.orientImplied(graph);
//                    continue WHILE;
//                }
//            }
//
//            break;
//        }
//
////        if (pattern.getNumEdges() > 10) {
////            System.out.println(pattern);
////        }
//
//
//        return pattern;
//
////
////
////        Graph graph = new EdgeListGraph(pattern);
////        pdagToDag(graph);
////        return graph;
    }

    public static void arrangeByKnowledgeTiers(Graph graph,
                                               IKnowledge knowledge) {
        if (knowledge.getNumTiers() == 0) {
            throw new IllegalArgumentException("There are no Tiers to arrange.");
        }

        List<Node> nodes = graph.getNodes();
        List<String> varNames = new ArrayList<String>();
        int ySpace = 500 / knowledge.getNumTiers();
        ySpace = ySpace < 50 ? 50 : ySpace;

        for (Node node1 : nodes) {
            varNames.add(node1.getName());
        }

        List<String> notInTier = knowledge.getVarsNotInTier();

        int x = 0;
        int y = 50 - ySpace;

        if (notInTier.size() > 0) {
            y += ySpace;

            for (String name : notInTier) {
                x += 90;
                Node node = graph.getNode(name);

                if (node != null) {
                    node.setCenterX(x);
                    node.setCenterY(y);
                }
            }
        }

        for (int i = 0; i < knowledge.getNumTiers(); i++) {
            List<String> tier = knowledge.getTier(i);
            y += ySpace;
            x = -25;

            for (String name : tier) {
                x += 90;
                Node node = graph.getNode(name);

                if (node != null) {
                    node.setCenterX(x);
                    node.setCenterY(y);
                }
            }
        }
    }

    /**
     * Double checks a sepset map against a pattern to make sure that X is adjacent to Y in the pattern iff {X, Y} is
     * not in the domain of the sepset map.
     *
     * @param sepset  a sepset map, over variables V.
     * @param pattern a pattern over variables W, V subset of W.
     * @return true if the sepset map is consistent with the pattern.
     */
    public static boolean verifySepsetIntegrity(SepsetMap sepset, Graph pattern) {
        for (Node x : pattern.getNodes()) {
            for (Node y : pattern.getNodes()) {
                if (x == y) {
                    continue;
                }

                if ((pattern.isAdjacentTo(y, x)) != (sepset.get(x, y) == null)) {
                    System.out.println("Sepset not consistent with graph for {" + x + ", " + y + "}");
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns the set of nodes reachable from the given set of initial nodes in the given graph according to the
     * criteria in the given legal pairs object.
     * <p/>
     * A variable V is reachable from initialNodes iff for some variable X in initialNodes thers is a path U [X, Y1,
     * ..., V] such that legalPairs.isLegalFirstNode(X, Y1) and for each [H1, H2, H3] as subpaths of U,
     * legalPairs.isLegalPairs(H1, H2, H3).
     * <p/>
     * The algorithm used is a variant of Algorithm 1 from Geiger, Verma, & Pearl (1990).
     *
     * @param initialNodes  The nodes that reachability paths start from.
     * @param legalPairs    Specifies initial edges (given initial nodes) and legal edge pairs.
     * @param c             a set of vertices (intuitively, the set of variables to be conditioned on.
     * @param d             a set of vertices (intuitively to be used in tests of legality, for example, the set of
     *                      ancestors of c).
     * @param graph         the graph with respect to which reachability is
     * @param maxPathLength
     */
    public static Set<Node> getReachableNodes(List<Node> initialNodes,
                                              LegalPairs legalPairs, List<Node> c, List<Node> d, Graph graph, int maxPathLength) {
        HashSet<Node> reachable = new HashSet<Node>();
        MultiKeyMap visited = new MultiKeyMap();
        List<ReachabilityEdge> nextEdges = new LinkedList<ReachabilityEdge>();

        for (Node x : initialNodes) {
            List<Node> adjX = graph.getAdjacentNodes(x);

            for (Node y : adjX) {
                if (legalPairs.isLegalFirstEdge(x, y)) {
                    reachable.add(y);
                    nextEdges.add(new ReachabilityEdge(x, y));
                    visited.put(x, y, Boolean.TRUE);
                }
            }
        }

        int pathLength = 1;

        while (nextEdges.size() > 0) {
//            System.out.println("Path length = " + pathLength);
            if (++pathLength > maxPathLength) return reachable;

            List<ReachabilityEdge> currEdges = nextEdges;
            nextEdges = new LinkedList<ReachabilityEdge>();

            for (ReachabilityEdge edge : currEdges) {
                Node x = edge.getFrom();
                Node y = edge.getTo();
                List<Node> adjY = graph.getAdjacentNodes(y);

                for (Node z : adjY) {
                    if ((visited.get(y, z)) == Boolean.TRUE) {
                        continue;
                    }

                    if (legalPairs.isLegalPair(x, y, z, c, d)) {
                        reachable.add(z);

                        nextEdges.add(new ReachabilityEdge(y, z));
                        visited.put(y, z, Boolean.TRUE);
                    }
                }
            }
        }

        return reachable;
    }


    /**
     * Returns the string in nodelist which matches string in BK.
     */
    public static Node translate(String a, List<Node> nodes) {
        for (Node node : nodes) {
            if ((node.getName()).equals(a)) {
                return node;
            }
        }

        return null;
    }


    public static List<Set<Node>> powerSet(List<Node> nodes) {
        List<Set<Node>> subsets = new ArrayList<Set<Node>>();
        int total = (int) Math.pow(2, nodes.size());
        for (int i = 0; i < total; i++) {
            Set<Node> newSet = new HashSet<Node>();
            String selection = Integer.toBinaryString(i);
            for (int j = selection.length() - 1; j >= 0; j--) {
                if (selection.charAt(j) == '1') {
                    newSet.add(nodes.get(selection.length() - j - 1));
                }
            }
            subsets.add(newSet);
        }
        return subsets;
    }

    /**
     * Checks if an arrowpoint is allowed by background knowledge.
     */
    public static boolean isArrowpointAllowed1(Node from, Node to,
                                               IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }

        return !knowledge.edgeRequired(to.toString(), from.toString()) &&
                !knowledge.edgeForbidden(from.toString(), to.toString());
    }

    public static boolean isArrowpointAllowed2(Node from, Node to,
                                               IKnowledge knowledge, Graph graph) {
        if (knowledge == null) {
            return true;
        }

        if (!graph.getNodesInTo(to, Endpoint.ARROW).isEmpty()) {
            return false;
        }

        return !knowledge.edgeRequired(to.toString(), from.toString()) &&
                !knowledge.edgeForbidden(from.toString(), to.toString());
    }

    /**
     * Generates the list of DAGs in the given pattern.
     */
    public static List<Graph> generatePatternDags(Graph pattern, boolean orientBidirectedEdges) {
        if (orientBidirectedEdges) {
            pattern = GraphUtils.removeBidirectedOrientations(pattern);
        }

        return getDagsInPatternMeek(pattern, new Knowledge());
    }

    public static List<Graph> getDagsInPatternMeek(Graph pattern, IKnowledge knowledge) {
        DagInPatternIterator iterator = new DagInPatternIterator(pattern, knowledge);
        List<Graph> dags = new ArrayList<Graph>();

        while (iterator.hasNext()) {
            Graph graph = iterator.next();

            try {
                if (knowledge.isViolatedBy(graph)) {
                    continue;
                }

                dags.add(graph);
            } catch (IllegalArgumentException e) {
                System.out.println("Found a non-DAG: " + graph);
            }
        }

        return dags;
    }

    public static List<Dag> getAllDagsInUndirectedGraph(Graph graph) {
        Graph undirected = GraphUtils.undirectedGraph(graph);

        DagIterator iterator = new DagIterator(undirected);
        List<Dag> dags = new ArrayList<Dag>();

        while (iterator.hasNext()) {
            Graph _graph = iterator.next();

            try {
                Dag dag = new Dag(_graph);
                dags.add(dag);
            } catch (IllegalArgumentException e) {
                //
            }
        }

        return dags;
    }

    public static List<Dag> getAllDagsByDirectingUndirectedEdges(Graph graph) {
//        Graph undirected = undirectedGraph(graph);

        DagIterator iterator = new DagIterator(graph);
        List<Dag> dags = new ArrayList<Dag>();

        while (iterator.hasNext()) {
            Graph _graph = iterator.next();

            try {
                Dag dag = new Dag(_graph);
                dags.add(dag);
            } catch (IllegalArgumentException e) {
                //
            }
        }

        return dags;
    }

    public static List<Graph> getAllGraphsByDirectingUndirectedEdges(Graph skeleton) {
        List<Graph> graphs = new ArrayList<Graph>();
        List<Edge> edges = skeleton.getEdges();

        List<Integer> undirectedIndices = new ArrayList<Integer>();

        for (int i = 0; i < edges.size(); i++) {
            if (Edges.isUndirectedEdge(edges.get(i))) {
                undirectedIndices.add(i);
            }
        }

        int[] dims = new int[undirectedIndices.size()];

        for (int i = 0; i < undirectedIndices.size(); i++) {
            dims[i] = 2;
        }

        CombinationGenerator gen = new CombinationGenerator(dims);
        int[] comb;

        while ((comb = gen.next()) != null) {
            Graph graph = new EdgeListGraph(skeleton.getNodes());

            for (Edge edge : edges) {
                if (!Edges.isUndirectedEdge(edge)) {
                    graph.addEdge(edge);
                }
            }

            for (int i = 0; i < undirectedIndices.size(); i++) {
                Edge edge = edges.get(undirectedIndices.get(i));
                Node node1 = edge.getNode1();
                Node node2 = edge.getNode2();

                if (comb[i] == 1) {
                    graph.addEdge(Edges.directedEdge(node1, node2));
                } else {
                    graph.addEdge(Edges.directedEdge(node2, node1));
                }
            }

            graphs.add(graph);
        }

        return graphs;
    }

    public static Graph bestGuessCycleOrientation(Graph graph, IndependenceTest test) {
        while (true) {
            List<Node> cycle = GraphUtils.directedCycle(graph);

            if (cycle == null) {
                break;
            }

            LinkedList<Node> _cycle = new LinkedList<Node>(cycle);

            Node first = _cycle.getFirst();
            Node last = _cycle.getLast();

            _cycle.addFirst(last);
            _cycle.addLast(first);

            int _j = -1;
            double minP = Double.POSITIVE_INFINITY;

            for (int j = 1; j < _cycle.size() - 1; j++) {
                int i = j - 1;
                int k = j + 1;

                Node x = test.getVariable(_cycle.get(i).getName());
                Node y = test.getVariable(_cycle.get(j).getName());
                Node z = test.getVariable(_cycle.get(k).getName());

                test.isIndependent(x, z, Collections.singletonList(y));

                double p = test.getPValue();

                if (p < minP) {
                    _j = j;
                    minP = p;
                }
            }

            Node x = _cycle.get(_j - 1);
            Node y = _cycle.get(_j);
            Node z = _cycle.get(_j + 1);

            graph.removeEdge(x, y);
            graph.removeEdge(z, y);
            graph.addDirectedEdge(x, y);
            graph.addDirectedEdge(z, y);
        }

        return graph;
    }

    // The published version.
    public static CpcTripleType getCpcTripleType(Node x, Node y, Node z,
                                                 IndependenceTest test, int depth,
                                                 Graph graph, boolean verbose) {
        if (verbose) {
            System.out.println("Checking " + x + " --- " + y + " --- " + z);
        }

//        if ((x.getName().equals("X5") && z.getName().equals("X7"))
//            || (x.getName().equals("X7") && z.getName().equals("X5"))) {
//            System.out.println();
//        }

        int numSepsetsContainingY = 0;
        int numSepsetsNotContainingY = 0;

        List<Node> _nodes = graph.getAdjacentNodes(x);
        _nodes.remove(z);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        while (true) {
            for (int d = 0; d <= _depth; d++) {
                ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg.next()) != null) {
                    List<Node> cond = GraphUtils.asList(choice, _nodes);

                    if (test.isIndependent(x, z, cond)) {
                        if (verbose) {
                            System.out.println("Indep: " + x + " _||_ " + z + " | " + cond);
                        }

                        if (cond.contains(y)) {
                            numSepsetsContainingY++;
                        } else {
                            numSepsetsNotContainingY++;
                        }
                    }

                    if (numSepsetsContainingY > 0 && numSepsetsNotContainingY > 0) {
                        return CpcTripleType.AMBIGUOUS;
                    }
                }
            }

            _nodes = graph.getAdjacentNodes(z);
            _nodes.remove(x);
            TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

            _depth = depth;
            if (_depth == -1) {
                _depth = 1000;
            }
            _depth = Math.min(_depth, _nodes.size());

            for (int d = 0; d <= _depth; d++) {
                ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg.next()) != null) {
                    List<Node> cond = GraphUtils.asList(choice, _nodes);

                    if (test.isIndependent(x, z, cond)) {
//                        System.out.println("Indep: " + x + " _||_ " + z + " | " + cond);

                        if (cond.contains(y)) {
                            numSepsetsContainingY++;
                        } else {
                            numSepsetsNotContainingY++;
                        }
                    }

                    if (numSepsetsContainingY > 0 && numSepsetsNotContainingY > 0) {
                        return CpcTripleType.AMBIGUOUS;
                    }
                }
            }

            break;
        }

        if (numSepsetsContainingY > 0) {
            return CpcTripleType.NONCOLLIDER;
        } else {
            if (verbose) {
                System.out.println("Orienting " + x + "-->" + y + "<-" + z);
            }
            return CpcTripleType.COLLIDER;
        }
    }

    // Using a heuristic cutoff for determining independence, dependence, and ambiguity
    public static CpcTripleType getCpcTripleType2(Node x, Node y, Node z,
                                                  IndependenceTest test, int depth,
                                                  Graph graph) {
        int numSepsetsContainingY = 0;
        int numSepsetsNotContainingY = 0;

        Set<Set<Node>> withY = new HashSet<Set<Node>>();
        Set<Set<Node>> withoutY = new HashSet<Set<Node>>();

        List<Node> _nodes = graph.getAdjacentNodes(x);
        _nodes.remove(z);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, _nodes);

                if (test.isIndependent(x, z, cond)) {
                    if (cond.contains(y)) {
                        numSepsetsContainingY++;
                        withY.add(new HashSet<Node>(cond));
                    } else {
                        numSepsetsNotContainingY++;
                        withoutY.add(new HashSet<Node>(cond));
                    }
                }
            }
        }

        _nodes = graph.getAdjacentNodes(z);
        _nodes.remove(x);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, _nodes);

                if (test.isIndependent(x, z, cond)) {
                    if (cond.contains(y)) {
                        numSepsetsContainingY++;
                        withY.add(new HashSet<Node>(cond));
                    } else {
                        numSepsetsNotContainingY++;
                        withoutY.add(new HashSet<Node>(cond));
                    }
                }
            }
        }

//        int factor = 3;
        int factor = 1;

        numSepsetsContainingY = withY.size();
        numSepsetsNotContainingY = withoutY.size();

        if (numSepsetsContainingY > factor * numSepsetsNotContainingY) {
            return CpcTripleType.NONCOLLIDER;
        } else if (numSepsetsNotContainingY > factor * numSepsetsContainingY) {
            return CpcTripleType.COLLIDER;
        } else {
            return CpcTripleType.AMBIGUOUS;
        }
    }

    public static CpcTripleType getCpcTripleType3(Node x, Node y, Node z,
                                                  IndependenceTest test, int depth,
                                                  Graph graph) {
        int numSepsetsContainingY = 0;
        int numSepsetsNotContainingY = 0;

        Set<Set<Node>> withY = new HashSet<Set<Node>>();
        Set<Set<Node>> withoutY = new HashSet<Set<Node>>();

        Set<Node> n = new HashSet<Node>(graph.getAdjacentNodes(x));
        n.addAll(graph.getAdjacentNodes(z));
        List<Node> _nodes = new ArrayList<Node>(n);

//        List<Node> _nodes = graph.getAdjacentNodes(x);
//        _nodes.addAll(graph.getAdjacentNodes(z));
        _nodes.remove(x);
        _nodes.remove(z);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, _nodes);

                if (test.isIndependent(x, z, cond)) {
                    if (cond.contains(y)) {
                        numSepsetsContainingY++;
                        withY.add(new HashSet<Node>(cond));
                    } else {
                        numSepsetsNotContainingY++;
                        withoutY.add(new HashSet<Node>(cond));
                    }
                }
            }
        }

//        _nodes = graph.getAdjacentNodes(z);
//        _nodes.remove(x);
//        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);
//
//        _depth = depth;
//        if (_depth == -1) {
//            _depth = 1000;
//        }
//        _depth = Math.min(_depth, _nodes.size());

//        for (int d = 0; d <= _depth; d++) {
//            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
//            int[] choice;
//
//            while ((choice = cg.next()) != null) {
//                List<Node> cond = GraphUtils.asList(choice, _nodes);
//
//                if (test.isIndependent(x, z, cond)) {
//                    if (cond.contains(y)) {
//                        numSepsetsContainingY++;
//                        withY.add(new HashSet<Node>(cond));
//                    } else {
//                        numSepsetsNotContainingY++;
//                        withoutY.add(new HashSet<Node>(cond));
//                    }
//                }
//            }
//        }

//        int factor = 3;
        int factor = 1;

        numSepsetsContainingY = withY.size();
        numSepsetsNotContainingY = withoutY.size();

        if (numSepsetsContainingY > factor * numSepsetsNotContainingY) {
            return CpcTripleType.NONCOLLIDER;
        } else if (numSepsetsNotContainingY > factor * numSepsetsContainingY) {
            return CpcTripleType.COLLIDER;
        } else {
            return CpcTripleType.AMBIGUOUS;
        }
    }

    // Just a strong collider check. Not conservative.
    public static CpcTripleType getCpcTripleType4(Node x, Node y, Node z,
                                                  IndependenceTest test, int depth,
                                                  Graph graph) {
        List<Double> p = new ArrayList<Double>();

        List<Node> _nodes = graph.getAdjacentNodes(x);
        _nodes.remove(z);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, _nodes);

                if (!cond.contains(y)) continue;

                if (test.isIndependent(x, z, cond)) {
                    p.add(test.getPValue());
//                    return CpcTripleType.NONCOLLIDER;
                }
            }
        }

        _nodes = graph.getAdjacentNodes(z);
        _nodes.remove(x);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, _nodes);

                if (!cond.contains(y)) continue;

                if (test.isIndependent(x, z, cond)) {
                    p.add(test.getPValue());
//                    return CpcTripleType.NONCOLLIDER;
                }
            }
        }

        // Sorts p.
        double cutoff = StatUtils.fdrCutoff(test.getAlpha(), p, false);
        double min = p.get(0);
        return min > cutoff ? CpcTripleType.COLLIDER : CpcTripleType.NONCOLLIDER;

//        return CpcTripleType.COLLIDER;
    }

    public static Graph chooseDagInPattern(Graph graph) {
        return new DagInPatternIterator(graph).next();


//        Graph newGraph = new EdgeListGraph(graph);
//
//        for(Edge edge : newGraph.getEdges()){
//            if(Edges.isBidirectedEdge(edge)){
//                newGraph.removeEdge(edge);
//            }
//        }
//
//        PatternToDag search = new PatternToDag(new Pattern(newGraph));
//        Graph dag = search.patternToDagMeekRules();
//        GraphUtils.arrangeBySourceGraph(dag, graph);
//        return dag;
    }

    public static Graph patternForDag(final Graph dag) {
        Graph pattern = new EdgeListGraph(dag);
        SearchGraphUtils.basicPattern(pattern);
        MeekRules rules = new MeekRules();
        rules.orientImplied(pattern);
//        GraphUtils.replaceNodes(pattern, dag.getNodes());
        return pattern;
    }

    public static Graph patternForDag(final Graph dag, Knowledge knowledge) {
        Graph pattern = new EdgeListGraph(dag);
        SearchGraphUtils.basicPattern(pattern);
        orientRequired(knowledge, pattern, pattern.getNodes());
        MeekRules rules = new MeekRules();
        rules.setKnowledge(knowledge);
        rules.orientImplied(pattern);
//        GraphUtils.replaceNodes(pattern, dag.getNodes());
        return pattern;
    }

    public static void orientRequired(IKnowledge bk, Graph graph, List<Node> nodes) {
        TetradLogger.getInstance().log("details", "Staring BK Orientation.");
        for (Iterator<KnowledgeEdge> it = bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = translate(edge.getFrom(), nodes);
            Node to = translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to-->from
            graph.removeEdge(from, to);
            graph.addDirectedEdge(to, from);
//            graph.setEndpoint(from, to, Endpoint.TAIL);
//            graph.setEndpoint(to, from, Endpoint.ARROW);

            TetradLogger.getInstance().log("knowledgeOrientations", SearchLogUtils.edgeOrientedMsg("IKnowledge", graph.getEdge(to, from)));
        }

        for (Iterator<KnowledgeEdge> it = bk.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = translate(edge.getFrom(), nodes);
            Node to = translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient from-->to
            graph.removeEdge(from, to);
            graph.addDirectedEdge(from, to);

//            graph.setEndpoint(to, from, Endpoint.TAIL);
//            graph.setEndpoint(from, to, Endpoint.ARROW);
            TetradLogger.getInstance().log("knowledgeOrientations", SearchLogUtils.edgeOrientedMsg("IKnowledge", graph.getEdge(from, to)));
        }


        TetradLogger.getInstance().log("details", "Finishing BK Orientation.");
    }


//    /**
//     * The recursive method used to list the MB DAGS consistent with an
//     * Pattern (i.e. with the independence information available to the search.
//     */
//    public static Set<Graph> listPatternDags(Graph pattern,
//                                             boolean orientBidirectedEdges) {
//        Set<Graph> dags = new HashSet<Graph>();
//        Graph graph = new EdgeListGraph(pattern);
//
//        MeekRules rules = new MeekRules();
//        rules.orientImplied(graph);
//
//        for (Edge edge : rules.getChangedEdges().keySet()) {
//            graph.setHighlighted(edge, true);
//        }
//
//        List<Edge> edges = graph.getEdges();
//        Edge edge = null;
//
//        for (Edge _edge : edges) {
//            if (orientBidirectedEdges && Edges.isBidirectedEdge(_edge)) {
//                edge = _edge;
//                break;
//            }
//
//            if (Edges.isUndirectedEdge(_edge)) {
//                edge = _edge;
//                break;
//            }
//        }
//
//        if (edge == null) {
//            dags.add(graph);
//            return dags;
//        }
//
//        graph.setEndpoint(edge.getNode2(), edge.getNode1(), Endpoint.TAIL);
//        graph.setEndpoint(edge.getNode1(), edge.getNode2(), Endpoint.ARROW);
//        graph.setHighlighted(graph.getEdge(edge.getNode1(), edge.getNode2()), true);
//        dags.addAll(listPatternDags(graph, orientBidirectedEdges));
//
//        graph.setEndpoint(edge.getNode1(), edge.getNode2(), Endpoint.TAIL);
//        graph.setEndpoint(edge.getNode2(), edge.getNode1(), Endpoint.ARROW);
//        graph.setHighlighted(graph.getEdge(edge.getNode1(), edge.getNode2()), true);
//        dags.addAll(listPatternDags(graph, orientBidirectedEdges));
//
//        return dags;
//    }

    public static Graph dagFromPattern(Graph graph, IKnowledge knowledge) {
        boolean allowArbitraryOrientations = true;
        boolean allowNewColliders = true;
        DagInPatternIterator iterator = new DagInPatternIterator(graph, knowledge, allowArbitraryOrientations,
                allowNewColliders);
        Graph dag = iterator.next();
        return dag;
    }

    /**
     * Simple class to store edges for the reachability search.
     *
     * @author Joseph Ramsey
     */
    private static class ReachabilityEdge {
        private Node from;
        private Node to;

        public ReachabilityEdge(Node from, Node to) {
            this.from = from;
            this.to = to;
        }

        public int hashCode() {
            int hash = 17;
            hash += 63 * getFrom().hashCode();
            hash += 71 * getTo().hashCode();
            return hash;
        }

        public boolean equals(Object obj) {
            ReachabilityEdge edge = (ReachabilityEdge) obj;

            if (!(edge.getFrom().equals(this.getFrom()))) {
                return false;
            }

            return edge.getTo().equals(this.getTo());
        }

        public Node getFrom() {
            return from;
        }

        public Node getTo() {
            return to;
        }
    }

    public enum CpcTripleType {
        COLLIDER, NONCOLLIDER, AMBIGUOUS
    }
}



