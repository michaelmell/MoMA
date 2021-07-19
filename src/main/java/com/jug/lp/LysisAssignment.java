package com.jug.lp;

import com.jug.MoMA;
import com.jug.export.FactorGraphFileBuilder_SCALAR;
import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBVar;
import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.type.numeric.real.FloatType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jug
 */
@SuppressWarnings( "restriction" )
public class LysisAssignment extends AbstractAssignment< Hypothesis< Component< FloatType, ? > > > {

    private final HypothesisNeighborhoods< Hypothesis< Component< FloatType, ? > >, AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > edges;
	private final Hypothesis< Component< FloatType, ? >> who;

	private static int dcId = 0;

	/**
	 * Creates an ExitAssignment.
	 *
	 * @param nodes
	 * @param edges
	 * @param who
     */
	public LysisAssignment(final GRBVar ilpVariable, final GrowthLineTrackingILP ilp, final AssignmentsAndHypotheses<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>, Hypothesis<Component<FloatType, ?>>> nodes, final HypothesisNeighborhoods<Hypothesis<Component<FloatType, ?>>, AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> edges, final Hypothesis<Component<FloatType, ?>> who) {
		super( GrowthLineTrackingILP.ASSIGNMENT_LYSIS, ilpVariable, ilp );
		this.edges = edges;
        this.who = who;
	}

	/**
	 * This method is void. Lysis assignments do not come with assignment
	 * specific constrains...
	 *
	 */
	@Override
	public void addConstraintsToLP() throws GRBException { }

	/**
	 * @see AbstractAssignment#getConstraintsToSave_PASCAL()
	 */
	@Override
	public List< String > getConstraintsToSave_PASCAL() {
		return null;
	}

	/**
	 * Adds a list of constraints and factors as strings.
	 *
	 */
	@Override
	public void addFunctionsAndFactors( final FactorGraphFileBuilder_SCALAR fgFile, final List< Integer > regionIds ) {
	}

	/**
	 * Returns the segmentation hypothesis this exit-assignment is associated
	 * with.
	 *
	 * @return the associated segmentation-hypothesis.
	 */
	public Hypothesis< Component< FloatType, ? >> getAssociatedHypothesis() {
		return who;
	}

	/**
	 * @see AbstractAssignment#getId()
	 */
	@Override
	public int getId() {
		return who.getId();
	}
}
