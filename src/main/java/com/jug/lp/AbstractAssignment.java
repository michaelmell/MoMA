/**
 *
 */
package com.jug.lp;

import java.util.List;

import com.jug.export.FactorGraphFileBuilder_SCALAR;

import gurobi.GRB;
import gurobi.GRBConstr;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBVar;

/**
 * Partially implemented class for everything that wants to be an assignment.
 * The main purpose of such a class is to store the value of the corresponding
 * Gurobi assignment variable and the ability to add assignment specific
 * constraints to the ILP (model).
 *
 * @author jug
 */
@SuppressWarnings( "restriction" )
public abstract class AbstractAssignment< H extends Hypothesis< ? > > {

	private int type;

	protected GrowthLineTrackingILP ilp;

	private int exportVarIdx = -1;
	private GRBVar ilpVar;

	private boolean isGroundTruth = false;
	private boolean isGroundUntruth = false;
	private GRBConstr constrGroundTruth;

	private boolean isPruned = false;

	/**
	 * Creates an assignment...
	 *
	 * @param type
	 * @param cost
	 */
	public AbstractAssignment( final int type, final GRBVar ilpVariable, final GrowthLineTrackingILP ilp ) {
		this.setType( type );
		setGRBVar( ilpVariable );
		setGrowthLineTrackingILP( ilp );
	}

	abstract public int getId();

	/**
	 * @return the type
	 */
	public int getType() {
		return type;
	}

	/**
	 * @param type
	 *            the type to set
	 */
	public void setType( final int type ) {
		this.type = type;
	}

//	/**
//	 * This function is for example used when exporting a FactorGraph
//	 * that describes the entire optimization problem at hand.
//	 *
//	 * @return a variable index that is unique for the indicator
//	 *         variable used for this assignment.
//	 * @throws Exception
//	 */
	public int getVarIdx() {
		if ( exportVarIdx == -1 ) {
			System.out.println( "AAAAACHTUNG!!! Variable index not initialized before export was attempted!" );
		}
		return exportVarIdx;
	}

	/**
	 * @return the ilpVar
	 */
	public GRBVar getGRBVar() {
		return ilpVar;
	}

	/**
	 * @param ilpVar
	 *            the ilpVar to set
	 */
	public void setGRBVar( final GRBVar ilpVar ) {
		this.ilpVar = ilpVar;
	}

//	/**
//	 * One can set a variable id.
//	 * This is used for exporting purposes like e.g. by
//	 * <code>FactorGraphFileBuilder</code>.
//	 *
//	 * @param varId
//	 */
	public void setVarId( final int varId ) {
		this.exportVarIdx = varId;
	}

	/**
	 * @param model
	 *            GRBModel instance (the ILP)
	 */
	public void setGrowthLineTrackingILP( final GrowthLineTrackingILP ilp ) {
		this.ilp = ilp;
	}

	/**
	 * @return the cost
	 * @throws GRBException
	 */
	public float getCost() {
		float cost = 0;
		try {
			cost = ( float ) getGRBVar().get( GRB.DoubleAttr.Obj );
		} catch ( final GRBException e ) {
			System.err.println( "CRITICAL: cost could not be read out of Gurobi ILP!" );
//			e.printStackTrace();
		}
		return cost;
	}

    /**
	 * @return true, if the ilpVar of this Assignment is equal to 1.0.
	 * @throws GRBException
	 */
	public boolean isChoosen() throws GRBException {
		return ( getGRBVar().get( GRB.DoubleAttr.X ) == 1.0 );
	}

	/**
	 * Abstract method that will, once implemented, add a set of assignment
	 * related constraints to the ILP (model) later to be solved by Gurobi.
	 *
	 * @throws GRBException
	 */
	public abstract void addConstraintsToLP() throws GRBException;

	/**
	 * Abstract method that will, once implemented, build the constraint
	 * representations needed to save the FG.
	 *
	 * @throws GRBException
	 */
	public abstract List< String > getConstraintsToSave_PASCAL();

	/**
	 * Adds a list of functions and factors to the FactorGraphFileBuilder.
	 * This fkt and fac is used to save a FactorGraph describing the
	 * optimization problem at hand.
	 */
	public abstract void addFunctionsAndFactors( FactorGraphFileBuilder_SCALAR fgFile, final List< Integer > regionIds );

	/**
	 * @return
	 */
	public boolean isGroundTruth() {
		return isGroundTruth;
	}

	/**
	 * @return
	 */
	public boolean isGroundUntruth() {
		return isGroundUntruth;
	}

	/**
	 *
	 */
	public void setGroundTruth( final boolean groundTruth ) {
		this.isGroundTruth = groundTruth;
		this.isGroundUntruth = false;
		addOrRemoveGroundTroothConstraint( groundTruth );
	}

	/**
	 *
	 */
	public void setGroundUntruth( final boolean groundUntruth ) {
		this.isGroundTruth = false;
		this.isGroundUntruth = groundUntruth;
		addOrRemoveGroundTroothConstraint( groundUntruth );
	}

	/**
	 *
	 */
	public void reoptimize() {
		try {
			ilp.model.update();
			System.out.print( "Running ILP with new ground-(un)truth knowledge in new thread!" );
			final Thread t = new Thread( new Runnable() {

				@Override
				public void run() {
					ilp.run();
				}
			} );
			t.start();
		} catch ( final GRBException e ) {
			e.printStackTrace();
		}
	}

	/**
	 *
	 */
	private void addOrRemoveGroundTroothConstraint( final boolean add ) {
		try {
			if ( add ) {
				final float value = ( this.isGroundUntruth ) ? 0f : 1f;

				final GRBLinExpr exprGroundTruth = new GRBLinExpr();
				exprGroundTruth.addTerm( 1.0, getGRBVar() );
				constrGroundTruth = ilp.model.addConstr( exprGroundTruth, GRB.EQUAL, value, "GroundTruthConstraint_" + getGRBVar().toString() );
			} else {
				if ( constrGroundTruth != null ) {
					ilp.model.remove( constrGroundTruth );
					constrGroundTruth = null;
				}
			}
		} catch ( final GRBException e ) {
			e.printStackTrace();
		}
	}

	/**
	 *
	 * @return null if not set, otherwise the GRBConstr.
	 */
	public GRBConstr getGroundTroothConstraint() {
		return constrGroundTruth;
	}

	/**
	 * @param value
	 */
	public void setPruned( final boolean value ) {
		this.isPruned = value;
	}

	/**
	 *
	 * @return
	 */
	public boolean isPruned() {
		return isPruned;
	}
}
