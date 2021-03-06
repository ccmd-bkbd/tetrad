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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetradapp.model.calculator.parser.ExpressionParser;
import edu.cmu.tetradapp.util.StringTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.util.*;
import java.util.List;

/**
 * Edits an expression for a node in the generalized SEM PM.
 *
 * @author Joseph Ramsey
 */
public class GeneralizedTemplateEditor extends JComponent {

    /**
     * The color that selected text is being rendered. Either black or red.
     */
    private Color color = Color.BLACK;

    /**
     * The start index of selected text.
     */
    private int start = 0;

    /**
     * The width of the selected text.
     */
    private int stringWidth = 0;

    /**
     * The time that the selectded text should be colored. (Must do this all indirectly using a thread because we cannot
     * listen to the text pane.
     */
    private long recolorTime = System.currentTimeMillis();

    /**
     * The text pane in which parsed text is rendered, typed, and colored.
     */
    private JTextPane expressionTextPane;

    /**
     * The document for the expression text pane that holds the parsed text.
     */
    private DefaultStyledDocument expressionTextDoc;

    /**
     * g The generalized SEM PM that's being edited.
     */
    private GeneralizedSemPm semPm;

    /**
     * The latest parser that was used to parse the expression in <code>expressionTextPane</code>. Needed to get the
     * most up to date list of parameters.
     */
    private ExpressionParser latestParser;

    /**
     * The box in which labels for each equation are rendered.
     */
    private Box equationsBox = Box.createVerticalBox();

    /**
     * If this is selected, then variables are listed in the box. Exclusive with <code>errorsCheckBox</code> and
     * <code>parametersCheckBox</code>.
     */
    private JRadioButton variablesButton;

    /**
     * If this is selected, then error terms are listed in the box. Exclusive with <code>variablesCheckBox</code> and
     * <code>parametersCheckBox</code>.
     */
    private JRadioButton errorsButton;

    /**
     * If this is selected, then parameters are listed in the box. Exclusive with <code>variablesCheckBox</code> and
     * <code>errorsCheckBox</code>.
     */
    private JRadioButton parametersButton;

    /**
     * The field that indicates what names of variables or parameters should start with in order to be changed.
     */
    private StringTextField startsWithField;

    //=============================================CONSTRUCTORS================================================//

    /**
     * Constructs the editor.
     *
     * @param semPm The GeneralizedSemPm that's being edited. The edits will be made to a copy of this PM.
     */
    public GeneralizedTemplateEditor(final GeneralizedSemPm semPm) {
        if (semPm == null) {
            throw new NullPointerException("SEM PM must be provided.");
        }

        this.semPm = new GeneralizedSemPm(semPm);

        this.equationsBox = Box.createVerticalBox();
        JScrollPane scroll = new JScrollPane(equationsBox);
        scroll.setPreferredSize(new Dimension(400, 300));

        StyleContext sc = new StyleContext();
        expressionTextDoc = new DefaultStyledDocument(sc);
        expressionTextPane = new JTextPane(expressionTextDoc);

        setParseText(semPm.getVariablesTemplate());

        final ExpressionParser parser = new ExpressionParser(); //new ArrayList<String>(), ExpressionParser.RestrictionType.MAY_NOT_CONTAIN);
        this.latestParser = parser;

        try {
            String template = getSemPm().getVariablesTemplate();
            parser.parseExpression(template);
        } catch (ParseException e) {
            throw new RuntimeException("Cannot parse the stored expression.", e);
        }

        final Map<String, String> expressionsMap = getExpressionsMap(semPm);
        String[] expressionTokens = getExpressionTokens(expressionsMap);
        final JComboBox expressionsBox = new JComboBox(expressionTokens);
        expressionsBox.setMaximumSize(expressionsBox.getPreferredSize());


        JButton insertButton = new JButton("Insert");

        insertButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                String token = (String) expressionsBox.getSelectedItem();
                String signature;

                if ("-New Parameter-".equals(token)) {
                    signature = nextParameterName("b");
                } else {
                    signature = expressionsMap.get(token);
                }

                while (signature.contains("%")) {
                    signature = signature.replaceFirst("%", nextParameterName("b"));
                }

                expressionTextPane.replaceSelection(signature);
            }
        });

        variablesButton = new JRadioButton("Variables");
        errorsButton = new JRadioButton("Errors");
        parametersButton = new JRadioButton("Parameters");

        ButtonGroup group = new ButtonGroup();
        group.add(variablesButton);
        group.add(errorsButton);
        group.add(parametersButton);

        variablesButton.setSelected(true);
        updateEquationsDisplay();

        variablesButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                setParseText(getSemPm().getVariablesTemplate());
                updateEquationsDisplay();
            }
        });

        errorsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                setParseText(getSemPm().getErrorsTemplate());
                updateEquationsDisplay();
            }
        });

        parametersButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                String startsWith = startsWithField.getText();
                String template = getSemPm().getStartsWithParameterTemplate(startsWith);
                if (template == null) {
                    template = getSemPm().getParametersTemplate();
                }

                setParseText(template);
                updateEquationsDisplay();
            }
        });

        final JButton applyButton = new JButton("APPLY");

        applyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                applyChanges();
                updateEquationsDisplay();

                if (variablesButton.isSelected()) {
                    String template = expressionTextPane.getText();

                    try {
                        getSemPm().setVariablesTemplate(template);
                    } catch (ParseException e) {
                        //
                    }
                } else if (errorsButton.isSelected()) {
                    String template = expressionTextPane.getText();

                    try {
                        getSemPm().setErrorsTemplate(template);
                    } catch (ParseException e) {
                        //
                    }
                } else if (parametersButton.isSelected()) {
                    String template = expressionTextPane.getText();

                    try {
                        getSemPm().setParametersTemplate(template);

                        String startsWith = startsWithField.getText();

                        getSemPm().setStartsWithParametersTemplate(startsWith, template);
                    } catch (ParseException e) {
                        //
                    }
                }
            }
        });

        startsWithField = new StringTextField("", 6);

        startsWithField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                String text = startsWithField.getText();
                String template = getSemPm().getStartsWithParameterTemplate(text);
                if (template != null) {
                    setParseText("boo");
                    updateEquationsDisplay();
                }
            }
        });

        Box b = Box.createVerticalBox();

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Type Template:"));
        b4.add(Box.createHorizontalGlue());
        b4.add(expressionsBox);
        b4.add(Box.createHorizontalStrut(5));
        b4.add(insertButton);
        b.add(b4);
        b.add(Box.createVerticalStrut(5));

        JScrollPane expressionScroll = new JScrollPane(expressionTextPane);
        expressionScroll.setPreferredSize(new Dimension(500, 50));
        Box b5 = Box.createHorizontalBox();
        b5.add(expressionScroll);
        b.add(b5);
        b.add(Box.createVerticalStrut(5));

        JPanel applyToPanel = new JPanel();
        applyToPanel.setLayout(new FlowLayout(FlowLayout.LEFT));

        Box b7 = Box.createHorizontalBox();
        b7.add(new JLabel("Apply to: "));
        b7.add(variablesButton);
        b7.add(errorsButton);
        b7.add(parametersButton);
        b7.add(Box.createHorizontalGlue());
        b7.add(new JLabel("Starts with: "));
        b7.add(startsWithField);
        b.add(b7);
        b.add(Box.createVerticalStrut(5));

        b.add(new JScrollPane(scroll));

        Box b8 = Box.createHorizontalBox();
        b8.add(Box.createHorizontalGlue());
        b8.add(applyButton);
        b.add(b8);
        b.add(Box.createVerticalStrut(5));

        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);

        expressionTextDoc.addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent documentEvent) {
                listen();
            }

            public void removeUpdate(DocumentEvent documentEvent) {
                listen();
            }

            public void changedUpdate(DocumentEvent documentEvent) {
                listen();
            }
        });

        class ColorThread extends Thread {
            private boolean stop = false;

            @Override
            public void run() {
                StyledDocument document = (StyledDocument) expressionTextPane.getDocument();

                Style red = expressionTextPane.addStyle("Red", null);
                StyleConstants.setForeground(red, Color.RED);

                Style black = expressionTextPane.addStyle("Black", null);
                StyleConstants.setForeground(black, Color.BLACK);

                while (!stop) {
                    if (System.currentTimeMillis() < recolorTime) {
                        continue;
                    }

                    if (color == Color.RED) {
                        document.setCharacterAttributes(start, stringWidth, expressionTextPane.getStyle("Red"), true);
                    } else if (color == Color.BLACK) {
                        document.setCharacterAttributes(start, stringWidth, expressionTextPane.getStyle("Black"), true);
                    }

                    try {
                        sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            public void scheduleStop() {
                this.stop = true;
            }
        }
        ;

        final ColorThread thread = new ColorThread();
        thread.start();

        addAncestorListener(new AncestorListener() {
            public void ancestorAdded(AncestorEvent event) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public void ancestorRemoved(AncestorEvent event) {
                thread.scheduleStop();
            }

            public void ancestorMoved(AncestorEvent event) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

//            public void componentHidden(ComponentEvent componentEvent) {
//                thread.scheduleStop();
//            }
        });

        expressionTextPane.setCaretPosition(expressionTextPane.getText().length());

        // When the dialog closes, we want to make sure the expression gets parsed and set.
        addAncestorListener(new AncestorListener() {
            public void ancestorAdded(AncestorEvent ancestorEvent) {
            }

            public void ancestorRemoved(AncestorEvent ancestorEvent) {
                listen();
            }

            public void ancestorMoved(AncestorEvent ancestorEvent) {
            }
        });

        setFocusCycleRoot(true);
    }


    public void useParametersAsStartup() {
        parametersButton.setSelected(true);
    }

    //==================================================PRIVATE METHODS=========================================//

    private String[] getExpressionTokens(Map<String, String> expressionsMap) {
        String[] expressionTokens = new String[expressionsMap.keySet().size()];
        int i = -1;

        for (String token : expressionsMap.keySet()) {
            expressionTokens[++i] = token;
        }
        return expressionTokens;
    }

    private Map<String, String> getExpressionsMap(GeneralizedSemPm semPm) {
        String[][] templateExpressions = new String[][]{
                {"NEW", "NEW(b)"},
                {"TSUM", "TSUM($)"},
                {"TPROD", "TPROD($)"},
        };

        // These are the expressions the user can choose from. The display form is on the left, and the template
        // form is on the. Obviously you use a % for a new parameter. In case you want to change it.
        String[][] expressions = new String[][]{
                {"+", " + "},
                {"-", " - "},
                {"*", " * "},
                {"/", " / "},
                {"^", "^"},
//                {"+(a, b, ...)", "+(%, %)"},
//                {"*(a, b, ...)", "*(%, %)"},
                {"pow(a, b)", "pow(%, %)"},
                {"sqrt(a)", "sqrt(%)"},
                {"sin(a)", "sin(%)"},
                {"cos(a)", "cos(%)"},
                {"tan(a)", "tan(%)"},
                {"asin(a)", "asin(%)"},
                {"acos(a)", "acos(%)"},
                {"atan(a)", "atan(%)"},
                {"sinh(a)", "sinh(%)"},
                {"tanh(a)", "tanh(%)"},
                {"ln(a)", "ln(%)"},
                {"log10(a)", "log10(%)"},
                {"round(a)", "round(%)"},
                {"ceil(a)", "ceil(%)"},
                {"floor(a)", "floor(%)"},
                {"signum(a)", "signum(%)"},
                {"abs(a)", "abs(%)"},
                {"max(a, b, ...)", "max(%, %)"},
                {"min(a, b, ...)", "min(%, %)"},
                {"AND(a, b)", "AND(%, %)"},
                {"OR(a, b)", "OR(%, %)"},
                {"XOR(a, b)", "XOR(%, %)"},
                {"IF(a, b, c)", "IF(%, %, %)"},
                {"<", " < "},
                {"<=", " <= "},
                {"=", " = "},
                {">=", " >= "},
                {">", " > "},
                {"Normal(mean, sd)", "Normal(%, %)"},
                {"Uniform(mean, sd)", "Uniform(%, %)"},
                {"StudentT(df)", "StudentT(%)"},
                {"Beta(alpha, beta)", "Beta(%, %)"},
                {"Gamma(alpha, lambda)", "Gamma(%, %)"},
                {"ChiSquare(df)", "ChiSquare(%)"},
                {"Hyperbolic(alpha, beta)", "Hyperbolic(%, %)"},
                {"Poisson(lambda)", "Poisson(%)"},
                {"ExponentialPower(tau)", "ExponentialPower(%)"},
                {"Exponential(lambda)", "Exponential(%)"},
                {"VonMises(freedom)", "VonMises(%)"},
                {"Split(a1, b1, a2, b2, ...)", "Split(%, %, %, %)"},
                {"Discrete(a1, a2, a3, a4, ...)", "Discrete(%, %, %, %)"},
                {"Indicator(p)", "Indicator(.5)"},
                {"Mixture(a1, dist1, b1, dist2, ...)", "Mixture(%, Normal(%, %), %, Normal(%, %))"},
        };

        List<Node> nodes = semPm.getNodes();

        final Map<String, String> expressionsMap = new LinkedHashMap<String, String>();

        for (int i = 0; i < templateExpressions.length; i++) {
            expressionsMap.put(templateExpressions[i][0], templateExpressions[i][1]);
        }

        for (int i = 0; i < nodes.size(); i++) {
            expressionsMap.put(nodes.get(i).getName(), nodes.get(i).getName());
        }

        for (int i = 0; i < expressions.length; i++) {
            expressionsMap.put(expressions[i][0], expressions[i][1]);
        }
        return expressionsMap;
    }

    //===============================================PUBLIC METHODS===========================================//

    public String nextParameterName(String base) {
        Set<String> parameters = getSemPm().getParameters();
        parameters.addAll(latestParser.getParameters());

        // Names should start with "1."
        int i = 0;

        loop:
        while (true) {
            String name = base + (++i);

            for (String parameter : parameters) {
                if (parameter.equals(name)) {
                    continue loop;
                }
            }

            break;
        }

        return base + i;
    }

    public GeneralizedSemPm getSemPm() {
        return semPm;
    }

    //==============================================PRIVATE METHODS============================================//

    private void setParseText(String text) {
        try {
            // Add the text to the document
            expressionTextDoc.remove(0, expressionTextPane.getText().length());
            expressionTextDoc.insertString(0, text, null);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    private void applyChanges() {
        List<Node> nodes = new ArrayList<Node>();
        List<String> parameters = new ArrayList<String>();
        String startWith = startsWithField.getText();

        if (variablesButton.isSelected()) {
            for (Node node : getSemPm().getVariableNodes()) {
                if (!getSemPm().getGraph().isParameterizable(node)) {
                    continue;
                }

                String formula = "";

                try {
                    formula = TemplateExpander.getInstance().expandTemplate(expressionTextPane.getText(),
                            getSemPm(), node);
                } catch (ParseException e) {
                    continue;
                }

                if (!node.getName().startsWith(startWith)) {
                    continue;
                }

                try {
                    getSemPm().setNodeExpression(node, formula);
                } catch (ParseException e) {
                    continue;
                }

                nodes.add(node);
            }
        } else if (errorsButton.isSelected()) {
            for (Node node : getSemPm().getErrorNodes()) {
                if (!getSemPm().getGraph().isParameterizable(node)) {
                    continue;
                }

                String formula = "";

                try {
                    formula = TemplateExpander.getInstance().expandTemplate(expressionTextPane.getText(),
                            getSemPm(), node);
                } catch (ParseException e) {
                    continue;
                }

                if (!node.getName().startsWith(startWith)) {
                    continue;
                }

                try {
                    getSemPm().setNodeExpression(node, formula);
                } catch (ParseException e) {
                    continue;
                }

                nodes.add(node);
            }
        } else if (parametersButton.isSelected()) {
            List<String> _parameters = new ArrayList<String>(getSemPm().getParameters());
            Collections.sort(_parameters);

            for (String parameter : _parameters) {
                String formula = "";

                try {
                    formula = TemplateExpander.getInstance().expandTemplate(expressionTextPane.getText(),
                            getSemPm(), null);
                } catch (ParseException e) {
                    continue;
                }

                if (!parameter.startsWith(startWith)) {
                    continue;
                }

                try {
                    getSemPm().setParameterExpression(parameter, formula);
                    getSemPm().setParameterExpression(startWith, parameter, formula);
                } catch (ParseException e) {
                    continue;
                }

                parameters.add(parameter);
            }
        }
    }

    private void updateEquationsDisplay() {
        equationsBox.removeAll();

        if (variablesButton.isSelected()) {
            for (Node node : getSemPm().getVariableNodes()) {
                if (!getSemPm().getGraph().isParameterizable(node)) {
                    continue;
                }

                Box c = Box.createHorizontalBox();
                String symbol = node.getNodeType() == NodeType.ERROR ? " ~ " : " = ";
                JLabel label = new JLabel(node + symbol + getSemPm().getNodeExpressionString(node));
                c.add(label);
                c.add(Box.createHorizontalGlue());
                equationsBox.add(c);
                equationsBox.add(Box.createVerticalStrut(5));
            }
        } else if (errorsButton.isSelected()) {
            for (Node node : getSemPm().getErrorNodes()) {
                if (node == null) continue;

                Box c = Box.createHorizontalBox();
                String symbol = node.getNodeType() == NodeType.ERROR ? " ~ " : " = ";
                JLabel label = new JLabel(node + symbol + getSemPm().getNodeExpressionString(node));
                c.add(label);
                c.add(Box.createHorizontalGlue());
                equationsBox.add(c);
                equationsBox.add(Box.createVerticalStrut(5));
            }
        } else if (parametersButton.isSelected()) {
            List<String> _parameters = new ArrayList<String>(getSemPm().getParameters());
            Collections.sort(_parameters);

            for (String parameter : _parameters) {
                Box c = Box.createHorizontalBox();
                JLabel label = new JLabel(parameter + " ~ " + getSemPm().getParameterExpressionString(parameter));

                c.add(label);
                c.add(Box.createHorizontalGlue());

                equationsBox.add(c);
                equationsBox.add(Box.createVerticalStrut(5));
            }
        }

        equationsBox.setBorder(new EmptyBorder(5, 5, 5, 5));

        equationsBox.revalidate();
        equationsBox.repaint();
    }

    private void listen() {
        String expressionString = expressionTextPane.getText();

        ExpressionParser parser = new ExpressionParser();

        try {
            if (!"".equals(expressionString.trim())) {
                parser.parseExpression(expressionString);
            }

            color = Color.BLACK;
            start = 0;
            stringWidth = expressionString.length();
            recolorTime = System.currentTimeMillis();

            if (variablesButton.isSelected()) {
                if (!"".equals(expressionString.trim())) {
                    getSemPm().setVariablesTemplate(expressionString);
                }
            }

            if (errorsButton.isSelected()) {
                if (!"".equals(expressionString.trim())) {
                    getSemPm().setErrorsTemplate(expressionString);
                }
            }

            if (parametersButton.isSelected()) {
                if (!"".equals(expressionString.trim())) {
                    getSemPm().setParametersTemplate(expressionString);
                }
            }
        } catch (ParseException e) {
            color = Color.RED;
            start = e.getErrorOffset();
            stringWidth = parser.getNextOffset() - e.getErrorOffset();
            recolorTime = System.currentTimeMillis();
        }

        this.latestParser = parser;
    }
}
