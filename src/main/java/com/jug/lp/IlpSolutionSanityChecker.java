package com.jug.lp;

import com.jug.GrowthLine;
import gurobi.GRBException;
import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.type.numeric.real.FloatType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class IlpSolutionSanityChecker {
    private GrowthLineTrackingILP ilp;
    private GrowthLine gl;

    public IlpSolutionSanityChecker(GrowthLineTrackingILP ilp,
                                    GrowthLine gl) {
        this.ilp = ilp;
        this.gl = gl;
    }

    void CheckSolutionContinuityConstraintForAllTimesteps() {
        System.out.println("--------- Start: CheckSolutionContinuityConstraintForTimestepBaseOnOptimalHypotheses ---------");
        for (int t = 1; t < gl.size(); t++) { /* we start at t=1 to have incoming defined assignments from previous time step */
            CheckSolutionContinuityConstraintForTimestepBaseOnOptimalHypotheses(t);
        }
        System.out.println("--------- End: CheckSolutionContinuityConstraintForTimestepBaseOnOptimalHypotheses ---------");

        System.out.println("--------- Start: CheckContinuityConstraintForAllOptimalAssignments ---------");
        for (int t = 1; t < gl.size(); t++) { /* we start at t=1 to have incoming defined assignments from previous time step */
            CheckContinuityConstraintForAllOptimalAssignments(t);
        }
        System.out.println("--------- End: CheckContinuityConstraintForAllOptimalAssignments ---------");
    }

    void CheckContinuityConstraintForAllOptimalAssignments(int t){
        Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> incomingAssignments = ilp.getOptimalAssignments(t - 1);
        Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> outgoingAssignments = ilp.getOptimalAssignments(t);
        Set<ExitAssignment> incomingExitAssignments = ilp.getEdgeSets().getAssignmentsOfType(incomingAssignments, ExitAssignment.class);
        Set<LysisAssignment> incomingLysisAssignments = ilp.getEdgeSets().getAssignmentsOfType(incomingAssignments, LysisAssignment.class);
        Set<DivisionAssignment> incomingDivisionAssignments = ilp.getEdgeSets().getAssignmentsOfType(incomingAssignments, DivisionAssignment.class);
        int incomingExitCount = incomingExitAssignments.size();
        int incomingLysisCount = incomingLysisAssignments.size();
        int incomingDivisionCount = incomingDivisionAssignments.size();
        int incomingTotalCount = incomingAssignments.size();
        int outgoingTotalCount = outgoingAssignments.size();
        if (outgoingTotalCount - incomingDivisionCount != (incomingTotalCount - incomingExitCount - incomingLysisCount)){
            System.out.println(String.format("ERROR: Continuity constraint violation at t=%d", t));
            System.out.println(String.format("incoming total: %d", incomingTotalCount));
            System.out.println(String.format("outgoing total: %d", outgoingTotalCount));
            System.out.println(String.format("incoming exit: %d", incomingExitCount));
            System.out.println(String.format("incoming lysis: %d", incomingLysisCount));
            System.out.println(String.format("incoming division: %d", incomingDivisionCount));
        }
    }



    /**
     * This method checks the continuity constraint: outgoingAssignmentCount == incomingAssignmentsCount for each
     * time step {@param t}
     *
     * @param t time step at which the continuity constraint is checked
     */
    void CheckSolutionContinuityConstraintForTimestepBaseOnOptimalHypotheses(int t) {
        try{
            List<Hypothesis<Component<FloatType, ?>>> currentOptimalHypotheses = ilp.getOptimalSegmentation(t);
            List<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> incomingAssignments = new ArrayList<>();
            List<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> outgoingAssignments = new ArrayList<>();
            for (Hypothesis<Component<FloatType, ?>> hyp : currentOptimalHypotheses) {
                incomingAssignments.add(ilp.getOptimalLeftAssignment(hyp));
                outgoingAssignments.add(ilp.getOptimalRightAssignment(hyp));
            }
            int incomingAssignmentCount = incomingAssignments.size();
            int outgoingAssignmentCount = outgoingAssignments.size();

            assert (outgoingAssignmentCount != incomingAssignmentCount) :
                    String.format("ERROR: Continuity constraint violation at t=%d", t);
            if (outgoingAssignmentCount != incomingAssignmentCount) {
                System.out.println(String.format("ERROR: Continuity constraint violation at t=%d", t));
                System.out.println(String.format("timestep %d:", t));
                System.out.println(String.format("incoming: %d", incomingAssignmentCount));
                System.out.println(String.format("outgoing: %d", outgoingAssignmentCount));
            }
        }
        catch (GRBException e) {
            e.printStackTrace();
        }
    }
}
