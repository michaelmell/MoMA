package com.jug.gui.assignmentview;

import com.jug.lp.ExitAssignment;
import com.jug.lp.Hypothesis;
import com.jug.lp.LysisAssignment;
import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ValuePair;

import java.awt.*;
import java.awt.geom.GeneralPath;

public class LysisAssignmentView extends AssignmentView {
    private final int ASSIGNMENT_DISPLAY_OFFSET;

    public LysisAssignmentView(final LysisAssignment lysisAssignment, int width, int ASSIGNMENT_DISPLAY_OFFSET) {
        this.abstractAssignment = lysisAssignment;
        this.width = width;
        this.ASSIGNMENT_DISPLAY_OFFSET = ASSIGNMENT_DISPLAY_OFFSET;
        setupPolygon();
    }

    @Override
    Color GetDefaultColor() {
        return Color.ORANGE;
    }

    @Override
    Color GetPrunedColor() {
        return new Color(1f, 1f, 0f, 0.2f);
    }

    private void setupPolygon() {
        final Hypothesis<Component<FloatType, ?>> leftHyp = ((LysisAssignment) abstractAssignment).getAssociatedHypothesis();
        final ValuePair<Integer, Integer> limitsLeft = leftHyp.getLocation();

        final int x1 = 0;
        final int y1 = limitsLeft.getA() + ASSIGNMENT_DISPLAY_OFFSET;
        final int x2 = 0;
        final int y2 = limitsLeft.getB() + ASSIGNMENT_DISPLAY_OFFSET;
        final int y3 = limitsLeft.getB() + ASSIGNMENT_DISPLAY_OFFSET;
        final int y4 = limitsLeft.getA() + ASSIGNMENT_DISPLAY_OFFSET;

        polygon = new GeneralPath();
        polygon.moveTo(x1, y1);
        polygon.lineTo(x2, y2);
        polygon.lineTo(this.width / 5, y3);
        polygon.lineTo(this.width / 5, y4);
        polygon.closePath();
    }
}
