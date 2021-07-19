package com.jug.gui.assignmentview;

import com.jug.gui.MoMAGui;
import com.jug.lp.AbstractAssignment;
import com.jug.lp.GrowthLineTrackingILP;
import com.jug.lp.Hypothesis;
import com.jug.util.OSValidator;
import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.type.numeric.real.FloatType;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.HashMap;
import java.util.Set;

/**
 * @author jug
 */
public class AssignmentsEditorViewer extends JTabbedPane implements ChangeListener {

    // -------------------------------------------------------------------------------------
    // statics
    // -------------------------------------------------------------------------------------
    private static final long serialVersionUID = 6588846114839723373L;
    private final MoMAGui gui;
    // -------------------------------------------------------------------------------------
    // fields
    // -------------------------------------------------------------------------------------
    private AssignmentsEditorCanvasView activeAssignments;
    private AssignmentsEditorCanvasView inactiveMappingAssignments;
    private AssignmentsEditorCanvasView inactiveDivisionAssignments;
    private AssignmentsEditorCanvasView inactiveExitAssignments;
    private AssignmentsEditorCanvasView inactiveLysisAssignments;
    private AssignmentsEditorCanvasView fixedAssignments;
    private int curTabIdx = 0;
    private JPanel nextHackTab;
    private HashMap<Hypothesis<Component<FloatType, ?>>, Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>>> data = new HashMap<>();

    // -------------------------------------------------------------------------------------
    // construction
    // -------------------------------------------------------------------------------------

    /**
     *
     */
    public AssignmentsEditorViewer(final int height, final MoMAGui callbackGui) {
        this.gui = callbackGui;
        this.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        buildGui(height);
    }

    // -------------------------------------------------------------------------------------
    // getters and setters
    // -------------------------------------------------------------------------------------

    // -------------------------------------------------------------------------------------
    // methods
    // -------------------------------------------------------------------------------------

    /**
     * Builds the user interface.
     */
    private void buildGui(final int height) {
        activeAssignments = new AssignmentsEditorCanvasView(height, gui);
        inactiveMappingAssignments = new AssignmentsEditorCanvasView(height, gui);
        inactiveDivisionAssignments = new AssignmentsEditorCanvasView(height, gui);
        inactiveExitAssignments = new AssignmentsEditorCanvasView(height, gui);
        inactiveLysisAssignments = new AssignmentsEditorCanvasView(height, gui);
        fixedAssignments = new AssignmentsEditorCanvasView(height, gui);

        // Hack to enable non-Mac MoMA to only use one row of tabs
        nextHackTab = new JPanel();
        final JComponent[] tabsToRoll =
                {activeAssignments, inactiveMappingAssignments, inactiveDivisionAssignments, inactiveExitAssignments, inactiveLysisAssignments, fixedAssignments};
        final String[] namesToRoll =
                {"OPT", "M", "D", "E", "L", "GT"};
        final AssignmentsEditorViewer me = this;
        final ChangeListener changeListener = changeEvent -> {
            final JTabbedPane sourceTabbedPane = (JTabbedPane) changeEvent.getSource();
            if (sourceTabbedPane.getSelectedComponent().equals(nextHackTab)) {
                final int oldIdx = curTabIdx;
                curTabIdx++;
                if (curTabIdx >= tabsToRoll.length) curTabIdx = 0;
                me.add(namesToRoll[curTabIdx], tabsToRoll[curTabIdx]);
                me.remove(tabsToRoll[oldIdx]);
                me.setSelectedIndex(1);
            }
        };

        activeAssignments.display(GrowthLineTrackingILP.getActiveAssignments(data));
        inactiveMappingAssignments.display(GrowthLineTrackingILP.filterAssignmentsWithPredicate(data, aa -> aa.getType() == GrowthLineTrackingILP.ASSIGNMENT_MAPPING));
        inactiveDivisionAssignments.display(GrowthLineTrackingILP.filterAssignmentsWithPredicate(data, aa -> aa.getType() == GrowthLineTrackingILP.ASSIGNMENT_DIVISION));
        inactiveExitAssignments.display(GrowthLineTrackingILP.filterAssignmentsWithPredicate(data, aa -> aa.getType() == GrowthLineTrackingILP.ASSIGNMENT_EXIT));
        inactiveLysisAssignments.display(GrowthLineTrackingILP.filterAssignmentsWithPredicate(data, aa -> aa.getType() == GrowthLineTrackingILP.ASSIGNMENT_LYSIS));
        fixedAssignments.setData(GrowthLineTrackingILP.filterAssignmentsWithPredicate(data, aa -> aa.isGroundTruth() || aa.isGroundUntruth()));

        if (!OSValidator.isMac()) {
            this.add(">", nextHackTab);
            this.add(namesToRoll[curTabIdx], tabsToRoll[curTabIdx]);
            this.setSelectedIndex(1);
            this.addChangeListener(changeListener);
        } else {
            for (int i = 0; i < tabsToRoll.length; i++) {
                this.add(namesToRoll[i], tabsToRoll[i]);
            }
        }
    }

    /**
     * Draw this instance of assignmentViewer without having a fitting HashMap.
     */
    public void display() {
        HashMap<Hypothesis<Component<FloatType, ?>>, Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>>> emptyHashMap = new HashMap<>();
        display(emptyHashMap);
    }

    /**
     * Receives and visualizes a new HashMap of assignments.
     *
     * @param hashMap a <code>HashMap</code> containing pairs of segmentation
     *                hypothesis at some time-point t and assignments towards t+1.
     */
    public void display(final HashMap<Hypothesis<Component<FloatType, ?>>, Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>>> hashMap) {
        if (!hashMap.equals(this.data)) {
            inactiveMappingAssignments.setData(GrowthLineTrackingILP.filterAssignmentsWithPredicate(hashMap, aa -> aa.getType() == GrowthLineTrackingILP.ASSIGNMENT_MAPPING));
            inactiveDivisionAssignments.setData(GrowthLineTrackingILP.filterAssignmentsWithPredicate(hashMap, aa -> aa.getType() == GrowthLineTrackingILP.ASSIGNMENT_DIVISION));
            inactiveExitAssignments.setData(GrowthLineTrackingILP.filterAssignmentsWithPredicate(hashMap, aa -> aa.getType() == GrowthLineTrackingILP.ASSIGNMENT_EXIT));
            inactiveLysisAssignments.setData(GrowthLineTrackingILP.filterAssignmentsWithPredicate(hashMap, aa -> aa.getType() == GrowthLineTrackingILP.ASSIGNMENT_LYSIS));
        }
        fixedAssignments.setData(GrowthLineTrackingILP.filterAssignmentsWithPredicate(hashMap, aa -> aa.isGroundTruth() || aa.isGroundUntruth()));
        activeAssignments.setData(GrowthLineTrackingILP.getActiveAssignments(hashMap));
        this.data = hashMap;
    }

    /**
     * @see javax.swing.event.ChangeListener#stateChanged(javax.swing.event.ChangeEvent)
     */
    @Override
    public void stateChanged(final ChangeEvent e) {
        if (this.getSelectedComponent().equals(activeAssignments)) {
            activeAssignments.setData(GrowthLineTrackingILP.getActiveAssignments(data));
        } else if (this.getSelectedComponent().equals(inactiveMappingAssignments)) {
            inactiveMappingAssignments.setData(GrowthLineTrackingILP.filterAssignmentsWithPredicate(data, aa -> aa.getType() == GrowthLineTrackingILP.ASSIGNMENT_MAPPING));
        } else if (this.getSelectedComponent().equals(inactiveDivisionAssignments)) {
            inactiveDivisionAssignments.setData(GrowthLineTrackingILP.filterAssignmentsWithPredicate(data, aa -> aa.getType() == GrowthLineTrackingILP.ASSIGNMENT_DIVISION));
        } else if (this.getSelectedComponent().equals(inactiveExitAssignments)) {
            inactiveExitAssignments.setData(GrowthLineTrackingILP.filterAssignmentsWithPredicate(data, aa -> aa.getType() == GrowthLineTrackingILP.ASSIGNMENT_EXIT));
        } else if (this.getSelectedComponent().equals(inactiveLysisAssignments)) {
            inactiveLysisAssignments.setData(GrowthLineTrackingILP.filterAssignmentsWithPredicate(data, aa -> aa.getType() == GrowthLineTrackingILP.ASSIGNMENT_LYSIS));
        } else {
            fixedAssignments.setData(GrowthLineTrackingILP.filterAssignmentsWithPredicate(data, aa -> aa.isGroundTruth() || aa.isGroundUntruth()));
        }
    }

    /**
     * Returns the <code>AssignmentsEditorCanvasView</code> that holds all active
     * assignments.
     *
     * @return
     */
    public AssignmentsEditorCanvasView getActiveAssignmentsForHtmlExport() {
        return this.activeAssignments;
    }

}
