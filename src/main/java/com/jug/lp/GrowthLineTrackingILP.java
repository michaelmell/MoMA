package com.jug.lp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;

import com.jug.GrowthLine;
import com.jug.GrowthLineFrame;
import com.jug.MoMA;
import com.jug.export.FactorGraphFileBuilder_PASCAL;
import com.jug.export.FactorGraphFileBuilder_PAUL;
import com.jug.export.FactorGraphFileBuilder_SCALAR;
import com.jug.gui.progress.DialogGurobiProgress;
import com.jug.gui.progress.ProgressListener;
import com.jug.lp.costs.CostFactory;
import com.jug.lp.costs.CostManager;
import com.jug.util.ComponentTreeUtils;

import gurobi.GRB;
import gurobi.GRBConstr;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import net.imglib2.Localizable;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.algorithm.componenttree.ComponentForest;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

/**
 * @author jug
 */
@SuppressWarnings( "restriction" )
public class GrowthLineTrackingILP {

	// < H extends Hypothesis< Component< FloatType, ? > >, A extends AbstractAssignment< H > >

	// -------------------------------------------------------------------------------------
	// statics
	// -------------------------------------------------------------------------------------
	private static final int OPTIMIZATION_NEVER_PERFORMED = 0;
	private static final int OPTIMAL = 1;
	private static final int INFEASIBLE = 2;
	private static final int UNBOUNDED = 3;
	private static final int SUBOPTIMAL = 4;
	private static final int NUMERIC = 5;
	private static final int LIMIT_REACHED = 6;

	public static final int ASSIGNMENT_EXIT = 0;
	public static final int ASSIGNMENT_MAPPING = 1;
	public static final int ASSIGNMENT_DIVISION = 2;

	public static final float CUTOFF_COST = 3.0f; // MM: Assignments with costs higher than this value will be ignored

	private static GRBEnv env;
	private static CostManager costManager;

	// -------------------------------------------------------------------------------------
	// fields
	// -------------------------------------------------------------------------------------
	private final GrowthLine gl;

	public GRBModel model;
	private int status = OPTIMIZATION_NEVER_PERFORMED;

	public final AssignmentsAndHypotheses< AbstractAssignment< Hypothesis< Component< FloatType, ? > > >, Hypothesis< Component< FloatType, ? > > > nodes =
			new AssignmentsAndHypotheses<>();
	private final HypothesisNeighborhoods< Hypothesis< Component< FloatType, ? > >, AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > edgeSets =
			new HypothesisNeighborhoods<>();

	private final HashMap< Hypothesis< Component< FloatType, ? > >, GRBConstr > ignoreSegmentConstraints =
			new HashMap<>();
	private final HashMap< Hypothesis< Component< FloatType, ? > >, GRBConstr > freezeSegmentConstraints =
			new HashMap<>();

	private int pbcId = 0;

	private final GRBConstr[] segmentInFrameCountConstraint;

	private final List< ProgressListener > progressListener;

	// -------------------------------------------------------------------------------------
	// construction
	// -------------------------------------------------------------------------------------
	public GrowthLineTrackingILP( final GrowthLine gl ) {
		this.gl = gl;

		// Array to hold segment# constraints
		this.segmentInFrameCountConstraint = new GRBConstr[ gl.size() ];

		// Setting static stuff (this IS ugly!)
		if ( env == null ) {
			try {
				env = new GRBEnv( "MotherMachineILPs.log" );
			} catch ( final GRBException e ) {
				System.out.println( "GrowthLineTrackingILP::env could not be initialized!" );
				e.printStackTrace();
			}
		}

		if ( costManager == null ) {
			costManager = new CostManager( 6, 13 );
			costManager.setWeights( new double[] { 0.1, 0.9, 0.5, 0.5, 0, 1, 								// mapping
			                                       0.1, 0.9, 0.5, 0.5, 0, 1, 1, 0, 1, 1, 0, 0.1, 0.03 } );  // division
		}

		try {
			model = new GRBModel( env );
		} catch ( final GRBException e ) {
			System.out.println( "GrowthLineTrackingILP::model could not be initialized!" );
			e.printStackTrace();
		}

		this.progressListener = new ArrayList<>();
	}

	// -------------------------------------------------------------------------------------
	// getters & setters
	// -------------------------------------------------------------------------------------
	/**
	 * @return the status. This status returns one of the following values:
	 *         OPTIMIZATION_NEVER_PERFORMED, OPTIMAL, INFEASABLE, UNBOUNDED,
	 *         SUBOPTIMAL, NUMERIC, or LIMIT_REACHED. Values 2-6 correspond
	 *         directly to the ones from gurobi, the last one is set when none
	 *         of the others was actually returned by gurobi.
	 *         OPTIMIZATION_NEVER_PERFORMED shows, that the optimizer was never
	 *         started on this ILP setup.
	 */
	public int getStatus() {
		return status;
	}

	// -------------------------------------------------------------------------------------
	// methods
	// -------------------------------------------------------------------------------------
	public void buildILP() {
		try {
			// add Hypothesis and Assignments
			createHypsAndAssignments();

			// UPDATE GUROBI-MODEL
			// - - - - - - - - - -
			model.update();

			// Iterate over all assignments and ask them to add their
			// constraints to the model
			// - - - - - - - - - - - - - - - - - - - - - - - - - - - -
			int numHyp = 0;
			for ( final List< Hypothesis< Component< FloatType, ? >>> innerList : nodes.getAllHypotheses() ) {
				for ( @SuppressWarnings( "unused" )
				final Hypothesis< Component< FloatType, ? >> hypothesis : innerList ) {
					numHyp++;
				}
			}
			int numAss = 0;
			for ( final List< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > innerList : nodes.getAllAssignments() ) {
				for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> assignment : innerList ) {
					assignment.addConstraintsToLP();
					numAss++;
				}
			}
			System.out.println( "    Hypothesis count: " + numHyp );
			System.out.println( "    Assignment count: " + numAss );

			// Add the remaining ILP constraints
			// (those would be (i) and (ii) of 'Default Solution')
			// - - - - - - - - - - - - - - - - - - - - - - - - - -
			addPathBlockingConstraints();
			addExplainationContinuityConstraints();

			// UPDATE GUROBI-MODEL
			// - - - - - - - - - -
			model.update();
//			System.out.println( "Constraints added: " + model.getConstrs().length );

		} catch ( final GRBException e ) {
			System.out.println( "Could not fill data into GrowthLineTrackingILP!" );
			e.printStackTrace();
		}

	}

	/**
	 * @throws GRBException
	 *
	 */
	private void createHypsAndAssignments() throws GRBException {
		createSegmentationHypotheses( 0 );
		for ( int t = 1; t < gl.size(); t++ ) {
			createSegmentationHypotheses( t );
			enumerateAndAddAssignments( t - 1 );
		}
		// add exit essignments to last (hidden/duplicated) timepoint					 - MM-2019-06-04: Apparently we the duplicate frame in that MoMA adds is on purpose!
		// in order have some right assignment for LP hypotheses variable substitution.
		final List< Hypothesis< Component< FloatType, ? > > > curHyps = nodes.getHypothesesAt( gl.size() - 1 );
		addExitAssignments( gl.size() - 1, curHyps );
	}

	/**
	 * Writes the FactorGraph corresponding to the optimization problem of the
	 * given growth-line into a file (format as requested by Bogdan&Paul).
	 * Format is an extension of
	 * http://www.cs.huji.ac.il/project/PASCAL/fileFormat.php.
	 *
	 * @throws IOException
	 */
	public void exportFG_PASCAL( final File file ) {
		final FactorGraphFileBuilder_PASCAL fgFile = new FactorGraphFileBuilder_PASCAL();

		// FIRST RUN: set all varId's
		for ( int t = 0; t < nodes.getNumberOfTimeSteps(); t++ ) {

			final List< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > assmts_t = nodes.getAssignmentsAt( t );
			for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > assmt : assmts_t ) {

				// variables for assignments
				final int var_id = fgFile.addVar( 2 );
				assmt.setVarId( var_id );

				// unaries associated to assignments
				if ( assmt.getType() == GrowthLineTrackingILP.ASSIGNMENT_MAPPING ) {
					final MappingAssignment ma = ( MappingAssignment ) assmt;
				} else if ( assmt.getType() == GrowthLineTrackingILP.ASSIGNMENT_DIVISION ) {
					final DivisionAssignment da = ( DivisionAssignment ) assmt;
				} else if ( assmt.getType() == GrowthLineTrackingILP.ASSIGNMENT_EXIT ) {
					final ExitAssignment ea = ( ExitAssignment ) assmt;
				}
				fgFile.addFkt( assmt.getVarIdx() );
				fgFile.addFactor( 0f, assmt.getCost() );
			}
		}
		// SECOND RUN: export all the rest (now that we have the right varId's).
		fgFile.addConstraintComment( "--- EXIT CONSTRAINTS -----------------------------" );
		for ( int t = 0; t < nodes.getNumberOfTimeSteps(); t++ ) {

			final List< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > assmts_t = nodes.getAssignmentsAt( t );
			for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > assmt : assmts_t ) {

				fgFile.addConstraints( assmt.getConstraintsToSave_PASCAL() );
			}
		}
		fgFile.addConstraintComment( "--- UNIQUENESS CONSTRAINTS FOR PATHS -------------" );
		fgFile.addConstraints( getPathBlockingConstraints_PASCAL() );
		fgFile.addConstraintComment( "--- CONTINUATION CONSTRAINTS ---------------------" );
		fgFile.addConstraints( getExplainationContinuityConstraints_PASCAL() );

		// WRITE FILE
		fgFile.write( file );
	}

	/**
	 * Writes the FactorGraph corresponding to the optimization problem of the
	 * given growth-line into a file (format as the one requested by Jan and
	 * SCALAR).
	 *
	 * @throws IOException
	 */
	public void exportFG_SCALAR_style( final File file ) {
		// Here I collect all the lines I will eventually write into the FG-file...
		final FactorGraphFileBuilder_SCALAR fgFile = new FactorGraphFileBuilder_SCALAR();

		// FIRST RUN: we export all variables and set varId's for second run...
		for ( int t = 0; t < nodes.getNumberOfTimeSteps(); t++ ) {
			// TODO puke!
			final int regionId = ( t + 1 ) / 2;

			fgFile.addVarComment( "=== VAR-SECTION :: TimePoint t=" + ( t + 1 ) + " ================" );
			fgFile.addVarComment( "--- VAR-SECTION :: Assignment-variables ---------------" );

			fgFile.addFktComment( "=== FKT-SECTION :: TimePoint t=" + ( t + 1 ) + " ================" );
			fgFile.addFktComment( "--- FKT-SECTION :: Unary (Segmentation) Costs ---------" );

			fgFile.addFactorComment( "=== FAC-SECTION :: TimePoint t=" + ( t + 1 ) + " ================" );
			fgFile.addFactorComment( "--- FAC-SECTION :: Unary (Segmentation) Factors -------" );

			final List< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > assmts_t = nodes.getAssignmentsAt( t );
			for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > assmt : assmts_t ) {
				final int var_id = fgFile.addVar( 2 );
				assmt.setVarId( var_id );

				float cost = 0.0f;
				if ( assmt.getType() == GrowthLineTrackingILP.ASSIGNMENT_MAPPING ) {
					fgFile.addVarComment( "- - MAPPING (var: " + var_id + ") - - - - - " );
					fgFile.addFktComment( "- - MAPPING (var: " + var_id + ") - - - - - " );
					final MappingAssignment ma = ( MappingAssignment ) assmt;
					cost = ma.getSourceHypothesis().getCosts() + ma.getDestinationHypothesis().getCosts();
				} else if ( assmt.getType() == GrowthLineTrackingILP.ASSIGNMENT_DIVISION ) {
					fgFile.addVarComment( "- - DIVISION (var: " + var_id + ") - - - - - " );
					fgFile.addFktComment( "- - DIVISION (var: " + var_id + ") - - - - - " );
					final DivisionAssignment da = ( DivisionAssignment ) assmt;
					cost = da.getSourceHypothesis().getCosts() + da.getUpperDesinationHypothesis().getCosts() + da
							.getLowerDesinationHypothesis()
							.getCosts();
				} else if ( assmt.getType() == GrowthLineTrackingILP.ASSIGNMENT_EXIT ) {
					fgFile.addVarComment( "- - EXIT (var: " + var_id + ") - - - - - " );
					fgFile.addFktComment( "- - EXIT (var: " + var_id + ") - - - - - " );
					final ExitAssignment ea = ( ExitAssignment ) assmt;
					cost = ea.getAssociatedHypothesis().getCosts();
				}

				final int fkt_id = fgFile.addFkt( String.format( "table 1 2 0 %f", cost ) );
				fgFile.addFactor( fkt_id, var_id, regionId );
			}
		}
		// SECOND RUN: export all the rest (now that we have the right varId's).
		for ( int t = 0; t < nodes.getNumberOfTimeSteps(); t++ ) {
			// TODO puke!
			final int regionId = ( t + 1 ) / 2;

			fgFile.addFktComment( "=== FKT-SECTION :: TimePoint t=" + ( t + 1 ) + " ================" );
			fgFile.addFktComment( "--- FKT-SECTION :: Assignment Constraints (HUP-stuff for EXITs) -------------" );

			fgFile.addFactorComment( "=== FAC-SECTION :: TimePoint t=" + ( t + 1 ) + " ================" );
			fgFile.addFactorComment( "--- FAC-SECTION :: Assignment Factors ----------------" );

			final List< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > assmts_t = nodes.getAssignmentsAt( t );
			for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > assmt : assmts_t ) {
				final List< Integer > regionIds = new ArrayList<>();
				regionIds.add(regionId);
				assmt.addFunctionsAndFactors( fgFile, regionIds );
			}

			// NOTE: last time-point does not get Path-Blocking or Explanation-Continuity-Constraints!
			if ( t == nodes.getNumberOfTimeSteps() - 1 ) continue;

			fgFile.addFktComment( "--- FKT-SECTION :: Path-Blocking Constraints ------------" );
			fgFile.addFactorComment( "--- FAC-SECTION :: Path-Blocking Constraints ------------" );

			final ComponentForest< ? > ct = gl.get( t ).getComponentTree();
			recursivelyAddPathBlockingConstraints( ct, t, fgFile );

			if ( t > 0 && t < nodes.getNumberOfTimeSteps() ) {
				fgFile.addFktComment( "--- FKT-SECTION :: Explanation-Continuity Constraints ------" );
				fgFile.addFactorComment( "--- FAC-SECTION :: Explanation-Continuity Constraints ------" );

				for ( final Hypothesis< Component< FloatType, ? > > hyp : nodes.getHypothesesAt( t ) ) {
					final List< Integer > varIds = new ArrayList<>();
					final List< Integer > coeffs = new ArrayList<>();

					if ( edgeSets.getLeftNeighborhood( hyp ) != null ) {
						for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > a_j : edgeSets.getLeftNeighborhood( hyp ) ) {
							//expr.addTerm( 1.0, a_j.getGRBVar() );
							coeffs.add(1);
							varIds.add(a_j.getVarIdx());
						}
					}
					if ( edgeSets.getRightNeighborhood( hyp ) != null ) {
						for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > a_j : edgeSets.getRightNeighborhood( hyp ) ) {
							//expr.addTerm( -1.0, a_j.getGRBVar() );
							coeffs.add(-1);
							varIds.add(a_j.getVarIdx());
						}
					}

					// add the constraint for this hypothesis
					//model.addConstr( expr, GRB.EQUAL, 0.0, "ecc_" + eccId );
					final int fkt_id = fgFile.addConstraintFkt( coeffs, "==", 0 );
					fgFile.addFactor( fkt_id, varIds, regionId );
				}
			}
		}

		// WRITE FILE
		fgFile.write( file );
	}

	private < C extends Component< ?, C > > void recursivelyAddPathBlockingConstraints( final ComponentForest< C > ct, final int t, final FactorGraphFileBuilder_SCALAR fgFile ) {
		for ( final C ctRoot : ct.roots() ) {
			// And call the function adding all the path-blocking-constraints...
			recursivelyAddPathBlockingConstraints( ctRoot, t, fgFile );
		}
	}

	/**
	 * Adds all component-tree-nodes, wrapped in instances of
	 * <code>Hypothesis</code> at time-point t
	 * This method calls <code>recursivelyAddCTNsAsHypotheses(...)</code>.
	 */
	private void createSegmentationHypotheses( final int t ) {
		final GrowthLineFrame glf = gl.getFrames().get( t );

		for ( final Component< FloatType, ? > ctRoot : glf.getComponentTree().roots() ) {
			recursivelyAddCTNsAsHypotheses( t, ctRoot ); //, glf.isParaMaxFlowComponentTree()
		}

		this.reportProgress();
	}

	/**
	 * Adds all hypothesis given by the nodes in the component tree to
	 * <code>nodes</code>.
	 *
	 * @param ctNode
	 *            a node in a <code>ComponentTree</code>.
	 * @param t
	 *            the time-index the ctNode comes from.
	 */
	private void recursivelyAddCTNsAsHypotheses( final int t, final Component< FloatType, ? > ctNode ) { //, final boolean isForParaMaxFlowSumImg

		float cost;
//		if ( isForParaMaxFlowSumImg ) {
//			cost = localParamaxflowBasedCost( t, ctNode );
//		} else {
			cost = localIntensityBasedCost( t, ctNode );
//		}
		nodes.addHypothesis( t, new Hypothesis<>(t, ctNode, cost) );

		// do the same for all children
		for ( final Component< FloatType, ? > ctChild : ctNode.getChildren() ) {
			recursivelyAddCTNsAsHypotheses( t, ctChild ); //, isForParaMaxFlowSumImg
		}
	}

	/**
	 * @param t
	 * @param ctNode
	 * @return
	 */
	public float localIntensityBasedCost( final int t, final Component< ?, ? > ctNode ) {
		RandomAccessibleInterval<FloatType> img = Views.hyperSlice( MoMA.instance.getImgProbs(), 2, t);
		return CostFactory.getIntensitySegmentationCost( ctNode, img );
	}

	//	public float localParamaxflowBasedCost( final int t, final Component< ?, ? > ctNode ) {
//		//TODO kotz
//		final float[] gapSepFkt = gl.getFrames().get( t ).getAwesomeGapSeparationValues( MoMA.instance.getImgTemp() );
//		return CostFactory.getParamaxflowSegmentationCost( ctNode, gapSepFkt );
//	}

	/**
	 * For time-points t and t+1, enumerates all potentially
	 * interesting assignments using the <code>addXXXAsignment(...)</code>
	 * methods.
	 *
	 * @throws GRBException
	 */
	private void enumerateAndAddAssignments( final int timeStep ) throws GRBException {
		final List< Hypothesis< Component< FloatType, ? >>> currentHyps = nodes.getHypothesesAt( timeStep );
		final List< Hypothesis< Component< FloatType, ? >>> nextHyps = nodes.getHypothesesAt( timeStep + 1 );

		addExitAssignments( timeStep, currentHyps );
		addMappingAssignments( timeStep, currentHyps, nextHyps );
		addDivisionAssignments( timeStep, currentHyps, nextHyps );
		this.reportProgress();
	}

	/**
	 * Add an exit-assignment at time t to a bunch of segmentation hypotheses.
	 * Note: exit-assignments cost <code>0</code>, but they come with a
	 * non-trivial construction to enforce, that an exit-assignment can only be
	 * assigned by the solver iff all active segmentation hypotheses above one
	 * that has an active exit-assignment are also assigned with an
	 * exit-assignment.
	 *
	 * @param t
	 *            the time-point.
	 * @param hyps
	 *            a list of hypothesis for which an <code>ExitAssignment</code>
	 *            should be added.
	 * @throws GRBException
	 */
	private void addExitAssignments( final int t, final List< Hypothesis< Component< FloatType, ? >>> hyps ) throws GRBException {
		if ( hyps == null ) return;

		float cost;
		int i = 0;
		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			cost = costModulationForSubstitutedILP( hyp.getCosts() );

			final GRBVar newLPVar = model.addVar( 0.0, 1.0, cost, GRB.BINARY, String.format( "a_%d^EXIT--%d", t, hyp.getId() ) );
			final List< Hypothesis< Component< FloatType, ? >>> Hup = LpUtils.getHup( hyp, hyps );
			final ExitAssignment ea = new ExitAssignment(newLPVar, this, nodes, edgeSets, Hup, hyp );
			nodes.addAssignment( t, ea );
			edgeSets.addToRightNeighborhood( hyp, ea );
			i++;
		}
	}

	/**
	 * Add a mapping-assignment to a bunch of segmentation hypotheses.
	 *
	 * @param t
	 *            the time-point from which the <code>curHyps</code> originate.
	 * @param curHyps
	 *            a list of hypothesis for which a
	 *            <code>MappingAssignment</code> should be added.
	 * @param nxtHyps
	 *            a list of hypothesis at the next time-point at which the newly
	 *            added <code>MappingAssignments</code> should end at.
	 * @throws GRBException
	 */
	private void addMappingAssignments( final int t, final List< Hypothesis< Component< FloatType, ? >>> curHyps, final List< Hypothesis< Component< FloatType, ? >>> nxtHyps ) throws GRBException {
		if ( curHyps == null || nxtHyps == null ) return;

		float cost;

		int i = 0;
		for ( final Hypothesis< Component< FloatType, ? >> from : curHyps ) {
			int j = 0;
			final float fromCost = from.getCosts();

			for ( final Hypothesis< Component< FloatType, ? >> to : nxtHyps ) {
				final float toCost = to.getCosts();

				if ( !( ComponentTreeUtils.isBelowByMoreThen( to, from, MoMA.MAX_CELL_DROP ) ) ) {

					final Pair< Float, float[] > compatibilityCostOfMapping = compatibilityCostOfMapping( from, to );
					cost = costModulationForSubstitutedILP( fromCost, toCost, compatibilityCostOfMapping.getA() );

					final int numFeatures = 2 + compatibilityCostOfMapping.getB().length;
					final float[] featureValues = new float[ numFeatures ];
					int k = 0;
					featureValues[ k++ ] = fromCost;
					featureValues[ k++ ] = toCost;
					for ( final float f : compatibilityCostOfMapping.getB() ) {
						featureValues[ k++ ] = f;
					}

					// features = [ fromCost, toCost, HU, HL, L, onlyH ? 0 : L ]
					// weights = [ 0.1, 0.9, 0.5, 0.5, 0.0, 1.0 ]
					//             2.7, 2.7, 0.7, 0.6, 0.6, 0.2
					if ( cost <= CUTOFF_COST ) {
						final String name = String.format( "a_%d^MAPPING--(%d,%d)", t, from.getId(), to.getId() );
						final GRBVar newLPVar = model.addVar( 0.0, 1.0, cost, GRB.BINARY, name );

						costManager.addMappingVariable( newLPVar, featureValues );
						if ( Math.abs( cost - costManager.getCurrentCost( newLPVar ) ) > 0.00001 ) {
							System.err.println( "Mapping cost mismatch!" );
						}

						final MappingAssignment ma = new MappingAssignment( t, newLPVar, this, nodes, edgeSets, from, to );
						nodes.addAssignment( t, ma );
						if (!edgeSets.addToRightNeighborhood(from, ma)) {
							System.err.println( "ERROR: Mapping-assignment could not be added to right neighborhood!" );
						}
						if (!edgeSets.addToLeftNeighborhood(to, ma)) {
							System.err.println( "ERROR: Mapping-assignment could not be added to left neighborhood!" );
						}
						j++;
					}
				}
			}
			i++;
		}
	}

	/**
	 * Computes the compatibility-mapping-costs between the two given
	 * hypothesis.
	 *
	 * @param from
	 *            the segmentation hypothesis from which the mapping originates.
	 * @param to
	 *            the segmentation hypothesis towards which the
	 *            mapping-assignment leads.
	 * @return the cost we want to set for the given combination of segmentation
	 *         hypothesis (plus the vector of cost contributions/feature
	 *         values).
	 */
	public Pair< Float, float[] > compatibilityCostOfMapping(
			final Hypothesis< Component< FloatType, ? > > from,
			final Hypothesis< Component< FloatType, ? > > to ) {
		final long sizeFrom = from.getWrappedHypothesis().size();
		final long sizeTo = to.getWrappedHypothesis().size();

//		final float valueFrom = from.getWrappedHypothesis().value().get();
//		final float valueTo = to.getWrappedHypothesis().value().get();

		final ValuePair< Integer, Integer > intervalFrom = from.getLocation();
		final ValuePair< Integer, Integer > intervalTo = to.getLocation();

		final float oldPosU = intervalFrom.getA();
		final float newPosU = intervalTo.getA();
		final float oldPosL = intervalFrom.getB();
		final float newPosL = intervalTo.getB();

		final float glLength = gl.get( 0 ).size();

		// Finally the costs are computed...
		final Pair< Float, float[] > costDeltaHU = CostFactory.getMigrationCost( oldPosU, newPosU, glLength );
		final Pair< Float, float[] > costDeltaHL = CostFactory.getMigrationCost( oldPosL, newPosL, glLength );
//		final float costDeltaH = Math.max( costDeltaHL, costDeltaHU );
		final float costDeltaH = 0.5f * costDeltaHL.getA() + 0.5f * costDeltaHU.getA();

		final Pair< Float, float[] > costDeltaL = CostFactory.getGrowthCost( sizeFrom, sizeTo, glLength );
//		final float costDeltaV = CostFactory.getIntensityMismatchCost( valueFrom, valueTo );

		float cost = costDeltaL.getA() + costDeltaH; // + costDeltaV

		// Border case bullshit
		// if the target cell touches the upper or lower border (then don't count uneven and shrinking)
		// (It is not super obvious why this should be true for bottom ones... some data has shitty
		// contrast at bottom, hence we trick this condition in here not to loose the mother -- which would
		// mean to loose all future tracks!!!)
		boolean onlyH = false;
		if (intervalTo.getA() == 0 || intervalTo.getB() + 1 >= glLength ) {
			onlyH = true;
			cost = costDeltaH; // + costDeltaV;
		}

		final int numFeatures = costDeltaHU.getB().length + costDeltaHL.getB().length + 2 * costDeltaL.getB().length;
		final float[] featureValues = new float[ numFeatures ];
		int i = 0;
		for ( final float f : costDeltaHU.getB() ) {
			featureValues[ i++ ] = f;
		}
		for ( final float f : costDeltaHL.getB() ) {
			featureValues[ i++ ] = f;
		}
		for ( final float f : costDeltaL.getB() ) {
			featureValues[ i++ ] = f;
		}
		for ( final float f : costDeltaL.getB() ) {
			featureValues[ i++ ] = onlyH ? 0 : f;
		}

		// features = [ HU, HL, L, onlyH ? 0 : L ]
		// weights = [ 0.5, 0.5, 0, 1 ]

//		System.out.println( String.format( ">>> %f + %f + %f = %f", costDeltaL, costDeltaV, costDeltaH, cost ) );
		return new ValuePair<>(cost, featureValues);
	}

	/**
	 * This method defines how the segmentation costs are influencing the costs
	 * of mapping assignments during the ILP hypotheses substitution takes
	 * place.
	 *
	 * @param fromCost
	 * @param toCost
	 * @param mappingCosts
	 * @return
	 */
	public float costModulationForSubstitutedILP(
			final float fromCost,
			final float toCost,
			final float mappingCosts ) {
		return 0.1f * fromCost + 0.9f * toCost + mappingCosts;
	}

	/**
	 * This method defines how the segmentation costs are influencing the costs
	 * of division assignments during the ILP hypotheses substitution takes
	 * place.
	 *
	 * @param fromCost
	 * @param divisionCosts
	 * @return
	 */
	public float costModulationForSubstitutedILP(
			final float fromCost,
			final float toUpperCost,
			final float toLowerCost,
			final float divisionCosts ) {
		return 0.1f * fromCost + 0.9f * ( toUpperCost + toLowerCost ) + divisionCosts;
	}

	/**
	 * This method defines how the segmentation costs are influencing the costs
	 * of exit assignments during the ILP hypotheses substitution takes place.
	 *
	 * @param fromCosts
	 *            costs for the segment to exit
	 * @return the modulated costs.
	 */
	public float costModulationForSubstitutedILP( final float fromCosts ) {
		return Math.min( 0.0f, fromCosts / 4f ); // NOTE: 0 or negative but only hyp/4 to prefer map or div if exists...
	}


	/**
	 * Add a division-assignment to a bunch of segmentation hypotheses. Note
	 * that this function also looks for suitable pairs of hypothesis in
	 * nxtHyps, since division-assignments naturally need two right-neighbors.
	 *
	 * @param timeStep
	 *            the time-point from which the <code>curHyps</code> originate.
	 * @param curHyps
	 *            a list of hypothesis for which a
	 *            <code>DivisionAssignment</code> should be added.
	 * @param nxtHyps
	 *            a list of hypothesis at the next time-point at which the newly
	 *            added <code>DivisionAssignments</code> should end at.
	 * @throws GRBException
	 */
	private void addDivisionAssignments( final int timeStep, final List< Hypothesis< Component< FloatType, ? >>> curHyps, final List< Hypothesis< Component< FloatType, ? >>> nxtHyps ) throws GRBException {
		if ( curHyps == null || nxtHyps == null ) return;

		float cost;

		for ( final Hypothesis< Component< FloatType, ? >> from : curHyps ) {
			final float fromCost = from.getCosts();

			for ( final Hypothesis< Component< FloatType, ? >> to : nxtHyps ) {
				if ( !( ComponentTreeUtils.isBelowByMoreThen( to, from, MoMA.MAX_CELL_DROP ) ) ) {
					final List<Component<FloatType, ?>> lowerNeighborComponents = ComponentTreeUtils.getRightNeighbors(to.getWrappedHypothesis());
					for ( final Component< FloatType, ? > lowerNeighborComponent : lowerNeighborComponents) {
						@SuppressWarnings( "unchecked" )
						final Hypothesis< Component< FloatType, ? > > lowerNeighbor = ( Hypothesis< Component< FloatType, ? >> ) nodes.findHypothesisContaining( lowerNeighborComponent );
						if ( lowerNeighbor == null ) {
							System.out.println( "CRITICAL BUG!!!! Check GrowthLineTimeSeris::adDivisionAssignment(...)" );
						} else {
							final Pair< Float, float[] > compatibilityCostOfDivision = compatibilityCostOfDivision( from, to, lowerNeighbor );

							//TODO toCosts should be split and structSVM routines should acknowledge two separated features!!!
							final float toCost = to.getCosts() + lowerNeighbor.getCosts();
							cost = costModulationForSubstitutedILP(
									fromCost,
									to.getCosts(),
									lowerNeighbor.getCosts(),
									compatibilityCostOfDivision.getA() );

							final int numFeatures = 2 + compatibilityCostOfDivision.getB().length;
							final float[] featureValues = new float[ numFeatures ];
							int k = 0;
							featureValues[ k++ ] = fromCost;
							featureValues[ k++ ] = toCost;
							for ( final float f : compatibilityCostOfDivision.getB() ) {
								featureValues[ k++ ] = f;
							}

							// features = [ fromCost, toCost, HU, HL, L, c(L,0,0), c(0,LT,LT), S, c(S,0,S), cdl, c(1,0,0), c(0,1,0), c(0,0,1) ]
							// weights =  [ 0.1, 0.9, 0.5, 0.5, 0.0, 1.0, 1.0, 0.0, 1.0, 1.0, 0.0, 0.1, 0.03 ]
							//             -0.6, 1.1, 0.9, 0.6, 1.6, 1.1, 0.3, 0.4, 0.3, 0.8, 1.6, 1.3, 0.02
							if ( cost <= CUTOFF_COST ) {
								final String name = String.format( "a_%d^DIVISION--(%d,%d)", timeStep, from.getId(), to.getId() );
								final GRBVar newLPVar = model.addVar( 0.0, 1.0, cost, GRB.BINARY, name );

								costManager.addDivisionVariable( newLPVar, featureValues );
								if ( Math.abs( cost - costManager.getCurrentCost( newLPVar ) ) > 0.00001 ) {
									System.err.println( "Division cost mismatch!" );
								}

								final DivisionAssignment da = new DivisionAssignment(newLPVar, this, from, to, lowerNeighbor );
								nodes.addAssignment( timeStep, da );
								edgeSets.addToRightNeighborhood( from, da );
								edgeSets.addToLeftNeighborhood( to, da );
								edgeSets.addToLeftNeighborhood( lowerNeighbor, da );
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Computes the compatibility-mapping-costs between the two given
	 * hypothesis.
	 *
	 * @param from
	 *            the segmentation hypothesis from which the mapping originates.
	 * @return the cost we want to set for the given combination of segmentation
	 *         hypothesis (plus the vector of cost contributions/feature
	 *         values).
	 */
	public Pair< Float, float[] > compatibilityCostOfDivision(
			final Hypothesis< Component< FloatType, ? > > from,
			final Hypothesis< Component< FloatType, ? > > toUpper,
			final Hypothesis< Component< FloatType, ? > > toLower ) {
		final ValuePair< Integer, Integer > intervalFrom = from.getLocation();
		final ValuePair< Integer, Integer > intervalToU = toUpper.getLocation();
		final ValuePair< Integer, Integer > intervalToL = toLower.getLocation();

		final long sizeFrom = from.getWrappedHypothesis().size();
		final long sizeToU = toUpper.getWrappedHypothesis().size();
		final long sizeToL = toLower.getWrappedHypothesis().size();
		final long sizeTo = sizeToU + sizeToL;
//		final long sizeToPlusGap = intervalToU.a - intervalToL.b;

		final float oldPosU = intervalFrom.getA();
		final float newPosU = intervalToU.getA();
		final float oldPosL = intervalFrom.getB();
		final float newPosL = intervalToL.getB();

		final float glLength = gl.get( 0 ).size();

		// Finally the costs are computed...
		final Pair< Float, float[] > costDeltaHU = CostFactory.getMigrationCost( oldPosU, newPosU, glLength );
		final Pair< Float, float[] > costDeltaHL = CostFactory.getMigrationCost( oldPosL, newPosL, glLength );
		final float costDeltaH = .5f * costDeltaHL.getA() + .5f * costDeltaHU.getA();
		final Pair< Float, float[] > costDeltaL = CostFactory.getGrowthCost( sizeFrom, sizeTo, glLength );
		final Pair< Float, float[] > costDeltaL_ifAtTop = CostFactory.getGrowthCost( sizeFrom, sizeToL * 2, glLength );
//		final float costDeltaV = CostFactory.getIntensityMismatchCost( valueFrom, valueTo );
		final float costDeltaS = CostFactory.getUnevenDivisionCost( sizeToU, sizeToL );
		final float costDivisionLikelihood = CostFactory.getDivisionLikelihoodCost( from ); //TODO: parameterize me!

		float cost = costDeltaL.getA() + costDeltaH + costDeltaS + costDivisionLikelihood; // + costDeltaV

		// Border case bullshit
		// if the upper cell touches the upper border (then don't count shrinking and be nicer to uneven)
		int c = 0;
		if (intervalToU.getA() == 0 || intervalToL.getB() + 1 >= glLength ) {
			// In case the upper cell is still at least like 1/2 in
			if ( ( 1.0 * sizeToU ) / ( 1.0 * sizeToL ) > 0.5 ) {
				c = 1;
				// don't count uneven div cost (but pay a bit to avoid exit+division instead of two mappings)
				cost = costDeltaL_ifAtTop.getA() + costDeltaH + 0.1f + costDivisionLikelihood; // + costDeltaV
			} else {
				c = 2;
				// otherwise do just leave out shrinking cost alone - yeah!
				cost =
						costDeltaL_ifAtTop.getA() + costDeltaH + costDeltaS + 0.03f + costDivisionLikelihood; // + costDeltaV
			}
		}

		final int numFeatures =
				costDeltaHU.getB().length +
				costDeltaHL.getB().length +
				2 * costDeltaL.getB().length +
				costDeltaL_ifAtTop.getB().length +
				2 +     // two times getUnevenDivisionCost
				1 + 	// costDivisionLikelihood
				3; 		// constants in if
		final float[] featureValues = new float[ numFeatures ];
		int i = 0;
		for ( final float f : costDeltaHU.getB() ) {
			featureValues[ i++ ] = f;
		}
		for ( final float f : costDeltaHL.getB() ) {
			featureValues[ i++ ] = f;
		}
		for ( final float f : costDeltaL.getB() ) {
			featureValues[ i++ ] = f;
		}
		for ( final float f : costDeltaL.getB() ) {
			switch ( c ) {
			case 0:
				featureValues[ i++ ] = f;
				break;
			case 1:
				case 2:
					featureValues[ i++ ] = 0;
				break;
			}
		}
		for ( final float f : costDeltaL_ifAtTop.getB() ) {
			switch ( c ) {
			case 0:
				featureValues[ i++ ] = 0;
				break;
			case 1:
				case 2:
					featureValues[ i++ ] = f;
				break;
			}
		}
		featureValues[ i++ ] = costDeltaS;
		switch ( c ) {
		case 0:
			case 2:
				featureValues[ i++ ] = costDeltaS;
			break;
		case 1:
			featureValues[ i++ ] = 0;
			break;
		}
		featureValues[ i++ ] = costDivisionLikelihood;
		switch ( c ) {
		case 0:
			featureValues[ i++ ] = 1;
			featureValues[ i++ ] = 0;
			featureValues[ i++ ] = 0;
			break;
		case 1:
			featureValues[ i++ ] = 0;
			featureValues[ i++ ] = 1;
			featureValues[ i++ ] = 0;
			break;
		case 2:
			featureValues[ i++ ] = 0;
			featureValues[ i++ ] = 0;
			featureValues[ i++ ] = 1;
			break;
		}

		// features = [ HU, HL, L, c(L,0,0), c(0,LT,LT), S, c(S,0,S), cdl, c(1,0,0), c(0,1,0), c(0,0,1) ]
		// weights = [ 0.5, 0.5, 0, 1, 1, 0, 1, 1, 0, 0.1, 0.03 ]

//		System.out.println( String.format( ">>> %f + %f + %f + %f = %f", costDeltaL, costDeltaV, costDeltaH, costDeltaS, cost ) );
		return new ValuePair<>(cost, featureValues);
	}

	/**
	 * This function traverses all time points of the growth-line
	 * <code>gl</code>, retrieves the full component tree that has to be built
	 * beforehand, and calls the private method
	 * <code>recursivelyAddPathBlockingConstraints</code> on all those root
	 * nodes. This function adds one constraint for each path starting at a leaf
	 * node in the tree up to the root node itself.
	 * Those path-blocking constraints ensure, that only 0 or 1 of the
	 * segmentation hypothesis along such a path can be chosen during the convex
	 * optimization.
	 *
	 * @throws GRBException
	 *
	 */
	private void addPathBlockingConstraints() throws GRBException {
		// For each time-point
		for ( int t = 0; t < gl.size(); t++ ) {
			// Get the full component tree
			final ComponentForest< ? > ct = gl.get( t ).getComponentTree();
			// And call the function adding all the path-blocking-constraints...
			recursivelyAddPathBlockingConstraints( ct, t );
		}
	}

	private List< String > getPathBlockingConstraints_PASCAL() {
		final ArrayList< String > ret = new ArrayList<>();

		// For each time-point
		for ( int t = 0; t < gl.size(); t++ ) {
			// Get the full component tree
			final ComponentForest< ? > ct = gl.get( t ).getComponentTree();
			// And call the function adding all the path-blocking-constraints...
			recursivelyAddPathBlockingConstraints( ret, ct, t );
		}

		return ret;
	}

	private < C extends Component< ?, C > > void recursivelyAddPathBlockingConstraints(
			final ComponentForest< C > ct,
			final int t )
			throws GRBException {
		for ( final C ctRoot : ct.roots() ) {
			// And call the function adding all the path-blocking-constraints...
			recursivelyAddPathBlockingConstraints( ctRoot, t );
		}
	}

	private < C extends Component< ?, C > > void recursivelyAddPathBlockingConstraints(
			final List< String > constraints,
			final ComponentForest< C > ct,
			final int t ) {
		for ( final C ctRoot : ct.roots() ) {
			// And call the function adding all the path-blocking-constraints...
			recursivelyAddPathBlockingConstraints( constraints, ctRoot, t );
		}
	}

	/**
	 * Generates path-blocking constraints for each path from the given
	 * <code>ctNode</code> to a leaf in the tree.
	 * Those path-blocking constraints ensure, that only 0 or 1 of the
	 * segmentation hypothesis along such a path can be chosen during the convex
	 * optimization.
	 *
	 * @param t
	 * @throws GRBException
	 */
	private < C extends Component< ?, C > > void recursivelyAddPathBlockingConstraints(
			final C ctNode,
			final int t ) throws GRBException {

		// if ctNode is a leave node -> add constraint (by going up the list of
		// parents and building up the constraint)
		if ( ctNode.getChildren().size() == 0 ) {
			C runnerNode = ctNode;

			final GRBLinExpr exprR = new GRBLinExpr();
			while ( runnerNode != null ) {
				@SuppressWarnings( "unchecked" )
				final Hypothesis< Component< FloatType, ? > > hypothesis = ( Hypothesis< Component< FloatType, ? >> ) nodes.findHypothesisContaining( runnerNode );
				if ( hypothesis == null ) {
					System.err.println( "WARNING: Hypothesis for a CTN was not found in GrowthLineTrackingILP -- this is an indication for some design problem of the system!" );
				}

				if ( edgeSets.getRightNeighborhood( hypothesis ) != null ) {
					for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> a : edgeSets.getRightNeighborhood( hypothesis ) ) {
						exprR.addTerm( 1.0, a.getGRBVar() );
					}
				}
				runnerNode = runnerNode.getParent();
			}
			pbcId++;
			final String name = "pbc_r_t_" + t + "_" + pbcId;
			model.addConstr( exprR, GRB.LESS_EQUAL, 1.0, name );
		} else {
			// if ctNode is a inner node -> recursion
			for ( final C ctChild : ctNode.getChildren() ) {
				recursivelyAddPathBlockingConstraints( ctChild, t );
			}
		}
	}

	private < C extends Component< ?, C > > void recursivelyAddPathBlockingConstraints(
			final List< String > constraints,
			final C ctNode,
			final int t ) {

		// if ctNode is a leave node -> add constraint (by going up the list of
		// parents and building up the constraint)
		if ( ctNode.getChildren().size() == 0 ) {
			C runnerNode = ctNode;

			StringBuilder constraint = new StringBuilder();
			while ( runnerNode != null ) {
				@SuppressWarnings( "unchecked" )
				final Hypothesis< Component< FloatType, ? > > hypothesis =
						( Hypothesis< Component< FloatType, ? > > ) nodes.findHypothesisContaining( runnerNode );
				if ( hypothesis == null ) {
					System.err.println(
							"WARNING: Hypothesis for a CTN was not found in GrowthLineTrackingILP -- this is an indication for some design problem of the system!" );
				}

				if ( edgeSets.getRightNeighborhood( hypothesis ) != null ) {
					for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > a : edgeSets.getRightNeighborhood( hypothesis ) ) {
						constraint.append(String.format("(%d,1)+", a.getVarIdx()));
					}
				}
				runnerNode = runnerNode.getParent();
			}
			if ( constraint.length() > 0 ) {
				constraint = new StringBuilder(constraint.substring(0, constraint.length() - 1));
				constraint.append(" <= 1");
				constraints.add(constraint.toString());
			}
		} else {
			// if ctNode is a inner node -> recursion
			for ( final C ctChild : ctNode.getChildren() ) {
				recursivelyAddPathBlockingConstraints( constraints, ctChild, t );
			}
		}
	}

	/**
	 *
	 * @param ctNode
	 * @param t
	 */
	private < C extends Component< ?, C > > void recursivelyAddPathBlockingConstraints( final C ctNode, final int t, final FactorGraphFileBuilder_SCALAR fgFile ) {

		// if ctNode is a leave node -> add constraint (by going up the list of
		// parents and building up the constraint)
		if ( ctNode.getChildren().size() == 0 ) {
			final List< Integer > varIds = new ArrayList<>();
			final List< Integer > coeffs = new ArrayList<>();

			C runnerNode = ctNode;

			// final GRBLinExpr exprR = new GRBLinExpr();
			while ( runnerNode != null ) {
				@SuppressWarnings( "unchecked" )
				final Hypothesis< Component< FloatType, ? > > hypothesis = ( Hypothesis< Component< FloatType, ? >> ) nodes.findHypothesisContaining( runnerNode );
				if ( hypothesis == null ) {
					System.err.println( "WARNING: Hypothesis for a CTN was not found in GrowthLineTrackingILP -- this is an indication for some design problem of the system!" );
				}

				if ( edgeSets.getRightNeighborhood( hypothesis ) != null ) {
					for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> a : edgeSets.getRightNeighborhood( hypothesis ) ) {
						// exprR.addTerm( 1.0, a.getGRBVar() );
						coeffs.add(1);
//						varIds.add( new Integer( a.getVarIdx() ) );
					}
				}
				runnerNode = runnerNode.getParent();
			}
			// model.addConstr( exprR, GRB.LESS_EQUAL, 1.0, name );
			final int fkt_id = fgFile.addConstraintFkt( coeffs, "<=", 1 );
			// TODO puke!
//			fgFile.addFactor( fkt_id, varIds, ( t + 1 ) / 2 );
		} else {
			// if ctNode is a inner node -> recursion
			for ( final C ctChild : ctNode.getChildren() ) {
				recursivelyAddPathBlockingConstraints( ctChild, t, fgFile );
			}
		}
	}

	/**
	 * This function generated and adds the explanation-continuity-constraints
	 * to the ILP model.
	 * Those constraints ensure that for each segmentation hypotheses at all
	 * time-points t we have the same number of active incoming and active
	 * outgoing edges from/to assignments.
	 * Intuitively speaking this means that each hypothesis that is chosen by an
	 * assignment coming from t-1 we need to continue its interpretation by
	 * finding an active assignment towards t+1.
	 */
	private void addExplainationContinuityConstraints() throws GRBException {
		int eccId = 0;

		// For each time-point
		for ( int t = 1; t < gl.size(); t++ ) {

			for ( final Hypothesis< Component< FloatType, ? >> hyp : nodes.getHypothesesAt( t ) ) {
				final GRBLinExpr expr = new GRBLinExpr();

				if ( edgeSets.getLeftNeighborhood( hyp ) != null ) {
					for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> a_j : edgeSets.getLeftNeighborhood( hyp ) ) {
						expr.addTerm( 1.0, a_j.getGRBVar() );
					}
				}
				if ( edgeSets.getRightNeighborhood( hyp ) != null ) {
					for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> a_j : edgeSets.getRightNeighborhood( hyp ) ) {
						expr.addTerm( -1.0, a_j.getGRBVar() );
					}
				}

				// add the constraint for this hypothesis
				model.addConstr( expr, GRB.EQUAL, 0.0, "ecc_" + eccId );
				eccId++;
			}
		}
	}

	private List< String > getExplainationContinuityConstraints_PASCAL() {
		final ArrayList< String > ret = new ArrayList<>();

		// For each time-point
		for ( int t = 1; t < gl.size() - 1; t++ ) { // !!! sparing out the border !!!

			for ( final Hypothesis< Component< FloatType, ? > > hyp : nodes.getHypothesesAt( t ) ) {
				StringBuilder constraint = new StringBuilder();

				if ( edgeSets.getLeftNeighborhood( hyp ) != null ) {
					for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > a_j : edgeSets.getLeftNeighborhood( hyp ) ) {
						constraint.append(String.format("(%d,1)+", a_j.getVarIdx()));
					}
				}
				if ( constraint.length() > 0 ) {
					constraint = new StringBuilder(constraint.substring(0, constraint.length() - 1)); //remove last '+' sign
				}
				if ( edgeSets.getRightNeighborhood( hyp ) != null ) {
					for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > a_j : edgeSets.getRightNeighborhood( hyp ) ) {
						constraint.append(String.format("-(%d,1)", a_j.getVarIdx()));
					}
				}

				constraint.append(" == 0");
				ret.add(constraint.toString());
			}
		}
		return ret;
	}

	/**
	 * Performs autosave of current tracking interactions (if the checkbox in
	 * the MotherMachineGui is checked).
	 */
	public void autosave() {
		if ( !MoMA.HEADLESS && MoMA.getGui().isAutosaveRequested() ) {
			final File autosaveFile =
					new File( MoMA.props.getProperty( "import_path" ) + "/--autosave.timm" );
			saveState( autosaveFile );
			System.out.println( "Autosave to: " + autosaveFile.getAbsolutePath() );
		}
	}

	/**
	 * This function takes the ILP (hopefully) built up in <code>model</code>
	 * and starts the convex optimization procedure. This is actually the step
	 * that will find the MAP in the given model and hence the solution to our
	 * segmentation and tracking problem.
	 */
	public void run() {
		try {
			// Set maximum time Gurobi may use!
//			model.getEnv().set( GRB.DoubleParam.TimeLimit, MotherMachine.GUROBI_TIME_LIMIT ); // now handled by callback!
			model.getEnv().set( GRB.IntParam.OutputFlag, 0 );

			final DialogGurobiProgress dialog = new DialogGurobiProgress( MoMA.getGuiFrame() );
			final GurobiCallback gcb = new GurobiCallback( dialog );
			model.setCallback( gcb );
			if ( !MoMA.HEADLESS ) {
				dialog.setVisible( true );
			}

			// RUN + return true if solution is feasible
			// - - - - - - - - - - - - - - - - - - - - -
			model.optimize();
			dialog.notifyGurobiTermination();
			if ( MoMA.getGui() != null ) {
				MoMA.getGui().dataToDisplayChanged();
			}

			// Relaxation run-test for Paul and Bogdan
			// - - - - - - - - - - - - - - - - - - - -
//			System.out.println( ">> Relaxing problem..." );
//			final GRBModel r = model.relax();
//			System.out.println( ">> Solving relaxed problem..." );
//			r.optimize();
//			System.out.println( ">> Counting integral variables..." );
//			int integral = 0;
//			int matching = 0;
//			int numvars = 0;
//			for ( int idx = 0; idx < r.getVars().length; idx++ ) {
//				final GRBVar var = model.getVars()[ idx ];
//				final GRBVar varRelaxed = r.getVars()[ idx ];
//				final double x = var.get( GRB.DoubleAttr.X );
//				final double xRelaxed = varRelaxed.get( GRB.DoubleAttr.X );
//				if ( xRelaxed == 0.0 || xRelaxed == 1.0 ) integral++;
//				if ( x == xRelaxed ) matching++;
//				numvars++;
//			}
//			System.out.println( String.format( ">> %d, %d, %d", numvars, integral, matching ) );

			// Read solution and extract interpretation
			// - - - - - - - - - - - - - - - - - - - - -
			if ( model.get( GRB.IntAttr.Status ) == GRB.Status.OPTIMAL ) {
				status = OPTIMAL;
				if ( !MoMA.HEADLESS ) {
					dialog.pushStatus( "Optimum was found!" );
					if ( MoMA.getGui() != null ) {
						MoMA.getGui().focusOnSliderTime();
					}
					dialog.setVisible( false );
					dialog.dispose();
				}
			} else if ( model.get( GRB.IntAttr.Status ) == GRB.Status.INFEASIBLE ) {
				status = INFEASIBLE;
				if ( !MoMA.HEADLESS ) {
					dialog.pushStatus( "ILP now infeasible. Please reoptimize!" );
				}
			} else if ( model.get( GRB.IntAttr.Status ) == GRB.Status.UNBOUNDED ) {
				status = UNBOUNDED;
			} else if ( model.get( GRB.IntAttr.Status ) == GRB.Status.SUBOPTIMAL ) {
				status = SUBOPTIMAL;
			} else if ( model.get( GRB.IntAttr.Status ) == GRB.Status.NUMERIC ) {
				status = NUMERIC;
			} else {
				status = LIMIT_REACHED;
				if ( !MoMA.HEADLESS ) {
					dialog.pushStatus( String.format( "Timelimit reached, rel. optimality gap: %.2f%%", gcb.getLatestGap() * 100.0 ) );
				}
			}
		} catch ( final GRBException e ) {
			System.out.println( "Could not run the generated ILP!" );
			e.printStackTrace();
		}
	}

	/**
	 * Returns the optimal segmentation at time t, given by a list of non
	 * conflicting component-tree-nodes.
	 * Calling this function makes only sense if the <code>run</code>-method was
	 * called and the convex optimizer could find a optimal feasible solution.
	 *
	 * @param t
	 *            the time-point at which to look for the optimal segmentation.
	 * @return a list of <code>Hypothesis</code> containting
	 *         <code>ComponentTreeNodes</code> that correspond to the
	 *         active segmentation hypothesis (chosen by the optimization
	 *         procedure).
	 */
	public List< Hypothesis< Component< FloatType, ? >>> getOptimalSegmentation( final int t ) {
//		final ArrayList< Hypothesis< Component< FloatType, ? >>> ret = new ArrayList< Hypothesis< Component< FloatType, ? >>>();
//
//		final List< Hypothesis< Component< FloatType, ? >>> hyps = getOptimalHypotheses( t );
//		for ( final Hypothesis< Component< FloatType, ? >> h : hyps ) {
//			ret.add( h );
//		}
//
//		return ret;
		return getOptimalHypotheses( t );
	}

	/**
	 * Returns the active segmentation at time t and the given y-location along
	 * the gap-separation function of the corresponding GLF.
	 * Calling this function makes only sense if the <code>run</code>-method was
	 * called and the convex optimizer could find a optimal feasible solution.
	 *
	 * @param t
	 *            the time-point at which to look for the optimal segmentation.
	 * @param gapSepYPos
	 *            the position along the gap-separation-function you want to
	 *            receive the active segmentation hypothesis for.
	 * @return a <code>Hypothesis< Component< FloatType, ? >></code> that
	 *         correspond to the active segmentation hypothesis at the
	 *         requested location.
	 *         Note: this function might return <code>null</code> since not all
	 *         y-locations are occupied by active segmentation hypotheses!
	 */
	public Hypothesis< Component< FloatType, ? >> getOptimalSegmentationAtLocation( final int t, final int gapSepYPos ) {
		final List< Hypothesis< Component< FloatType, ? >>> hyps = getOptimalHypotheses( t );
		for ( final Hypothesis< Component< FloatType, ? >> h : hyps ) {
			final ValuePair< Integer, Integer > ctnLimits =
					ComponentTreeUtils.getTreeNodeInterval( h.getWrappedHypothesis() );
			if (ctnLimits.getA() <= gapSepYPos && ctnLimits.getB() >= gapSepYPos ) { return h; }
		}
		return null;
	}

	/**
	 * Returns all active segmentations at time t that conflict with the given
	 * hypothesis.
	 *
	 * @param t
	 *            the time-point at which to look for the optimal segmentation.
	 * @param hyp
	 *            another hypothesis conflicts have to be queried for.
	 * @return a list of <code>Hypothesis< Component< FloatType, ? >></code>
	 *         that
	 *         conflict with the given hypothesis. (Overlap in space!)
	 */
	public List< Hypothesis< Component< FloatType, ? >>> getOptimalSegmentationsInConflict( final int t, final Hypothesis< Component< FloatType, ? >> hyp ) {
		final List< Hypothesis< Component< FloatType, ? >>> ret = new ArrayList<>();

		final ValuePair< Integer, Integer > interval =
				ComponentTreeUtils.getTreeNodeInterval( hyp.getWrappedHypothesis() );
		final int startpos = interval.getA();
		final int endpos = interval.getB();

		final List< Hypothesis< Component< FloatType, ? >>> hyps = getOptimalHypotheses( t );
		for ( final Hypothesis< Component< FloatType, ? >> h : hyps ) {
			final ValuePair< Integer, Integer > ctnLimits =
					ComponentTreeUtils.getTreeNodeInterval( h.getWrappedHypothesis() );
			if ( (ctnLimits.getA() <= startpos && ctnLimits.getB() >= startpos ) || // overlap at top
			(ctnLimits.getA() <= endpos && ctnLimits.getB() >= endpos ) ||    // overlap at bottom
			(ctnLimits.getA() >= startpos && ctnLimits.getB() <= endpos ) ) {  // fully contained inside
				ret.add( h );
			}
		}
		return ret;
	}

	/**
	 * @param t
	 * @param gapSepYPos
	 * @return
	 */
	public List< Hypothesis< Component< FloatType, ? >>> getSegmentsAtLocation( final int t, final int gapSepYPos ) {
		final List< Hypothesis< Component< FloatType, ? >>> ret = new ArrayList<>();

		final List< Hypothesis< Component< FloatType, ? >>> hyps = nodes.getHypothesesAt( t );
		for ( final Hypothesis< Component< FloatType, ? >> h : hyps ) {
			final ValuePair< Integer, Integer > ctnLimits =
					ComponentTreeUtils.getTreeNodeInterval( h.getWrappedHypothesis() );
			if (ctnLimits.getA() <= gapSepYPos && ctnLimits.getB() >= gapSepYPos ) {  // fully contained inside
				ret.add( h );
			}
		}
		return ret;
	}

	/**
	 * Returns the optimal segmentation at time t, given by a list of non
	 * conflicting segmentation hypothesis.
	 * Calling this function makes only sense if the <code>run</code>-method was
	 * called and the convex optimizer could find a optimal feasible solution.
	 *
	 * @param t
	 *            the time-point at which to look for the optimal segmentation.
	 * @return a list of <code>Hypothesis< Component< FloatType, ? > ></code>
	 *         that correspond to the active segmentation hypothesis (chosen by
	 *         the optimization procedure).
	 */
	private List< Hypothesis< Component< FloatType, ? > > > getOptimalHypotheses(final int t) {
		final ArrayList< Hypothesis< Component< FloatType, ? > > > ret = new ArrayList<>();

		final List< Hypothesis< Component< FloatType, ? >>> hyps = nodes.getHypothesesAt( t );

		if ( hyps == null ) return ret;

		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > nh;
			if ( t > 0 ) {
				nh = edgeSets.getLeftNeighborhood( hyp );
			} else {
				nh = edgeSets.getRightNeighborhood( hyp );
			}

			try {
				final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> aa = findActiveAssignment( nh );
				if ( aa != null ) {
					ret.add( hyp );
				}
			} catch ( final GRBException e ) {
//				System.err.println( "It could not be determined of a certain assignment was choosen during the convex optimization!" );
//				e.printStackTrace();
			}
		}

		return ret;
	}

	public boolean isSelected( final Hypothesis< Component< FloatType, ? > > hyp ) {
		Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > nh;
		if ( hyp.getTime() > 0 ) {
			nh = edgeSets.getLeftNeighborhood( hyp );
		} else {
			nh = edgeSets.getRightNeighborhood( hyp );
		}

		try {
			final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> aa =
					findActiveAssignment( nh );
			if ( aa != null ) { return true; }
		} catch ( final GRBException e ) {
//			System.err.println( "It could not be determined of a certain assignment was choosen during the convex optimization!" );
//			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Finds and returns the optimal left (to t-1) assignments at time-point t.
	 * For each segmentation hypothesis at t we collect all active assignments
	 * coming in from the left (from t-1).
	 * Calling this function makes only sense if the <code>run</code>-method was
	 * called and the convex optimizer could find a optimal feasible solution.
	 *
	 * @param t
	 *            the time at which to look for active left-assignments.
	 *            Values for t make only sense if <code>>=1</code> and
	 *            <code>< nodes.getNumberOfTimeSteps().</code>
	 * @return a hash-map that maps from segmentation hypothesis to sets
	 *         containing ONE assignment that (i) are active, and (ii) come in
	 *         from the left (from t-1).
	 *         Note that segmentation hypothesis that are not active will NOT be
	 *         included in the hash-map.
	 */
	public HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > > getOptimalLeftAssignments( final int t ) {
		assert ( t >= 1 );
		assert ( t < nodes.getNumberOfTimeSteps() );

		final HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > > ret = new HashMap<>();

		final List< Hypothesis< Component< FloatType, ? >>> hyps = nodes.getHypothesesAt( t );

		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			try {
				final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> ola = getOptimalLeftAssignment( hyp );
				if ( ola != null ) {
					final HashSet< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > oneElemSet = new HashSet<>();
					oneElemSet.add( ola );
					ret.put( hyp, oneElemSet );
				}
			} catch ( final GRBException e ) {
				System.err.println( "An optimal left assignment could not be determined!" );
				e.printStackTrace();
			}
		}

		return ret;
	}

	/**
	 * Finds and returns the optimal left (to t-1) assignment given a
	 * segmentation hypothesis.
	 * For each segmentation hypothesis we know a set of outgoing edges
	 * (assignments) that describe the interpretation (fate) of this segmented
	 * cell. The ILP is set up such that only 1 such assignment can be chosen by
	 * the convex optimizer during the computation of the optimal MAP
	 * assignment.
	 *
	 * @return the optimal (choosen by the convex optimizer) assignment
	 *         describing the most likely data interpretation (MAP) towards the
	 *         previous time-point.
	 * @throws GRBException
	 */
	private AbstractAssignment< Hypothesis< Component< FloatType, ? > > > getOptimalLeftAssignment( final Hypothesis< Component< FloatType, ? > > hypothesis ) throws GRBException {
		return findActiveAssignment( edgeSets.getLeftNeighborhood( hypothesis ) );
	}

	/**
	 * Finds and returns the optimal right (to t+1) assignments at time-point t.
	 * For each segmentation hypothesis at t we collect all active assignments
	 * going towards the right (to t+1).
	 * Calling this function makes only sense if the <code>run</code>-method was
	 * called and the convex optimizer could find a optimal feasible solution.
	 *
	 * @param t
	 *            the time at which to look for active right-assignments.
	 *            Values for t make only sense if <code>>=0</code> and
	 *            <code>< nodes.getNumberOfTimeSteps() - 1.</code>
	 * @return a hash-map that maps from segmentation hypothesis to a sets
	 *         containing ONE assignment that (i) are active, and (i) go towards
	 *         the right (to t+1).
	 *         Note that segmentation hypothesis that are not active will NOT be
	 *         included in the hash-map.
	 */
	public HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > > getOptimalRightAssignments( final int t ) {
		assert ( t >= 0 );
		assert ( t < nodes.getNumberOfTimeSteps() - 1 );

		final HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > > ret = new HashMap<>();

		final List< Hypothesis< Component< FloatType, ? >>> hyps = nodes.getHypothesesAt( t );

		if ( hyps == null ) return ret;

		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			try {
				final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> ora = getOptimalRightAssignment( hyp );
				if ( ora != null ) {
					final HashSet< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > oneElemSet = new HashSet<>();
					oneElemSet.add( ora );
					ret.put( hyp, oneElemSet );
				}
			} catch ( final GRBException e ) {
				System.err.println( "An optimal right assignment could not be determined!" );
				e.printStackTrace();
			}
		}

		return ret;
	}

	/**
	 * Finds and returns the optimal right (to t+1) assignment given a
	 * segmentation hypothesis.
	 * For each segmentation hypothesis we know a set of outgoing edges
	 * (assignments) that describe the interpretation (fate) of this segmented
	 * cell. The ILP is set up such that only 1 such assignment can be chosen by
	 * the convex optimizer during the computation of the optimal MAP
	 * assignment.
	 *
	 * @return the optimal (choosen by the convex optimizer) assignment
	 *         describing the most likely data interpretation (MAP) towards the
	 *         next time-point.
	 * @throws GRBException
	 */
	public AbstractAssignment< Hypothesis< Component< FloatType, ? > > > getOptimalRightAssignment( final Hypothesis< Component< FloatType, ? > > hypothesis ) throws GRBException {
		return findActiveAssignment( edgeSets.getRightNeighborhood( hypothesis ) );
	}

	/**
	 * Finds the active assignment in a set of assignments.
	 * This method is thought to be called given a set that can only contain at
	 * max 1 active assignment. (It will always and exclusively return the first
	 * active assignment in the iteration order of the given set!)
	 *
	 * @return the one (first) active assignment in the given set of
	 *         assignments. (An assignment is active iff the binary ILP variable
	 *         associated with the assignment was set to 1 by the convex
	 *         optimizer!)
	 * @throws GRBException
	 */
	private AbstractAssignment< Hypothesis< Component< FloatType, ? > > > findActiveAssignment( final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > set ) throws GRBException {
		if ( set == null ) return null;

		for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > a : set ) {
			if ( a.isChoosen() ) { return a; }
		}
		return null;
	}

	/**
	 * Collects and returns all inactive left-assignments given the optimal
	 * segmentation.
	 * An assignment in inactive, when it was NOT chosen by the ILP.
	 * Only those assignments are collected that are left-edges from one of the
	 * currently chosen (optimal) segmentation-hypotheses.
	 *
	 * @param t
	 *            the time at which to look for inactive left-assignments.
	 *            Values for t make only sense if <code>>=1</code> and
	 *            <code>< nodes.getNumberOfTimeSteps().</code>
	 * @return a hash-map that maps from segmentation hypothesis to a set of
	 *         assignments that (i) are NOT active, and (ii) come in from the
	 *         left (from t-1).
	 */
	public HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > > getInactiveLeftAssignments( final int t ) {
		assert ( t >= 1 );
		assert ( t < nodes.getNumberOfTimeSteps() );

		final HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > >> ret = new HashMap<>();

		final List< Hypothesis< Component< FloatType, ? >>> hyps = this.getOptimalHypotheses( t );

		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			try {
				final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > set = edgeSets.getLeftNeighborhood( hyp );

				if ( set == null ) continue;

				for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > a : set ) {
					if ( !a.isChoosen() ) {
						Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > innerSet = ret.get( hyp );
						if ( innerSet == null ) {
							innerSet = new HashSet<>();
							innerSet.add( a );
							ret.put( hyp, innerSet );
						} else {
							innerSet.add( a );
						}
					}
				}
			} catch ( final GRBException e ) {
				System.err.println( "Gurobi problem at getInactiveLeftAssignments(t)!" );
				e.printStackTrace();
			}
		}

		return ret;
	}

	/**
	 * @return the GrowthLine this is the ILP for.
	 */
	protected GrowthLine getGrowthLine() {
		return gl;
	}

	/**
	 * Collects and returns all inactive right-assignments given the optimal
	 * segmentation.
	 * An assignment in inactive, when it was NOT chosen by the ILP.
	 * Only those assignments are collected that are right-edges from one of the
	 * currently chosen (optimal) segmentation-hypotheses.
	 *
	 * @param t
	 *            the time at which to look for inactive right-assignments.
	 *            Values for t make only sense if <code>>=0</code> and
	 *            <code>< nodes.getNumberOfTimeSteps()-1.</code>
	 * @return a hash-map that maps from segmentation hypothesis to a set of
	 *         assignments that (i) are NOT active, and (ii) come in from the
	 *         right (from t+1).
	 */
	public HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > > getInactiveRightAssignments( final int t ) {
		assert ( t >= 0 );
		assert ( t < nodes.getNumberOfTimeSteps() - 1 );

		final HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > > ret = new HashMap<>();

		final List< Hypothesis< Component< FloatType, ? >>> hyps = this.getOptimalHypotheses( t );

		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			try {
				final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > set = edgeSets.getRightNeighborhood( hyp );

				if ( set == null ) continue;

				for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > a : set ) {
					if ( !a.isChoosen() ) {
						Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > innerSet = ret.get( hyp );
						if ( innerSet == null ) {
							innerSet = new HashSet<>();
							innerSet.add( a );
							ret.put( hyp, innerSet );
						} else {
							innerSet.add( a );
						}
					}
				}
			} catch ( final GRBException e ) {
				System.err.println( "Gurobi problem at getInactiveRightAssignments(t)!" );
				e.printStackTrace();
			}
		}

		return ret;
	}

	/**
	 * Collects and returns all left-assignments given the optimal segmentation.
	 * Only those assignments are collected that are left-edges from one of the
	 * currently chosen (optimal) segmentation-hypotheses.
	 *
	 * @param t
	 *            the time at which to look for inactive left-assignments.
	 *            Values for t make only sense if <code>>=1</code> and
	 *            <code>< nodes.getNumberOfTimeSteps().</code>
	 * @return a hash-map that maps from segmentation hypothesis to a set of
	 *         assignments that come in from the left (from t-1).
	 */
	public HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > > getAllCompatibleLeftAssignments( final int t ) {
		assert ( t >= 1 );
		assert ( t < nodes.getNumberOfTimeSteps() );

		final HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > >> ret = new HashMap<>();

		final List< Hypothesis< Component< FloatType, ? >>> hyps = this.getOptimalHypotheses( t );

		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > set = edgeSets.getLeftNeighborhood( hyp );

			if ( set == null ) continue;

			for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > a : set ) {
				Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > innerSet = ret.get( hyp );
				if ( innerSet == null ) {
					innerSet = new HashSet<>();
					innerSet.add( a );
					ret.put( hyp, innerSet );
				} else {
					innerSet.add( a );
				}
			}
		}

		return ret;
	}

	/**
	 * Collects and returns all right-assignments given the optimal
	 * segmentation.
	 * Only those assignments are collected that are right-edges from one of the
	 * currently chosen (optimal) segmentation-hypotheses.
	 *
	 * @param t
	 *            the time at which to look for inactive right-assignments.
	 *            Values for t make only sense if <code>>=0</code> and
	 *            <code>< nodes.getNumberOfTimeSteps()-1.</code>
	 * @return a hash-map that maps from segmentation hypothesis to a set of
	 *         assignments that come in from the right (from t+1).
	 */
	public HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > > getAllCompatibleRightAssignments( final int t ) {
		assert ( t >= 0 );
		assert ( t < nodes.getNumberOfTimeSteps() - 1 );

		final HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > > ret = new HashMap<>();

		final List< Hypothesis< Component< FloatType, ? >>> hyps = this.getOptimalHypotheses( t );

		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > set = edgeSets.getRightNeighborhood( hyp );

			if ( set == null ) continue;

			for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > a : set ) {
				Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > innerSet = ret.get( hyp );
				if ( innerSet == null ) {
					innerSet = new HashSet<>();
					innerSet.add( a );
					ret.put( hyp, innerSet );
				} else {
					innerSet.add( a );
				}
			}
		}

		return ret;
	}

	/**
	 * One of the powerful user interaction constraints.
	 * This method constraints a frame to contain a given number of segments
	 * (cells).
	 *
	 * @param t
	 *            The time-index. Must be in [0,nodes.getNumberOfTimeSteps()-2]
	 * @param numCells
	 *            the right hand side of the constraint.
	 * @throws GRBException
	 */
	public void addSegmentsInFrameCountConstraint( final int t, final int numCells ) throws GRBException {
		final GRBLinExpr expr = new GRBLinExpr();

		final List< Hypothesis< Component< FloatType, ? >>> hyps = nodes.getHypothesesAt( t );
		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > rightNeighbors = edgeSets.getRightNeighborhood( hyp );
			for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> assmnt : rightNeighbors ) {
				expr.addTerm( 1.0, assmnt.getGRBVar() );
			}
		}

		segmentInFrameCountConstraint[ t ] = model.addConstr( expr, GRB.EQUAL, numCells, "sifcc_" + t );
	}

	/**
	 * Removes an constraint on the number of cells at a given time-point (in
	 * case such a constraint was ever added).
	 *
	 * @param t
	 */
	public void removeSegmentsInFrameCountConstraint( final int t ) {
		if ( segmentInFrameCountConstraint[ t ] != null ) {
			try {
				model.remove( segmentInFrameCountConstraint[ t ] );
				segmentInFrameCountConstraint[ t ] = null;
			} catch ( final GRBException e ) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Returns the right hand side of the segment-count constraint the given
	 * time-point.
	 *
	 * @param t
	 *            time-point index.
	 * @return the RHS of the constraint if one such constraint is set, -1
	 *         otherwise.
	 */
	public int getSegmentsInFrameCountConstraintRHS( final int t ) {
		if ( segmentInFrameCountConstraint[ t ] != null ) {
			try {
				return ( int ) segmentInFrameCountConstraint[ t ].get( GRB.DoubleAttr.RHS );
			} catch ( final GRBException e ) {
				e.printStackTrace();
			}
		}
		return -1;
	}

	/**
	 * Returns the hypothesis at a given position.
	 * If there are more then one hypothesis at given location only the lowest
	 * in the hypotheses tree will be returned.
	 * (This is also the "shortest" one!)
	 *
	 * @param t
	 * @param gapSepYPos
	 * @return
	 */
	public Hypothesis< Component< FloatType, ? >> getLowestInTreeHypAt( final int t, final int gapSepYPos ) {
		Hypothesis< Component< FloatType, ? >> ret = null;
		long min = Long.MAX_VALUE;

		final List< Hypothesis< Component< FloatType, ? >>> hyps = nodes.getHypothesesAt( t );
		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			final Component< FloatType, ? > comp = hyp.getWrappedHypothesis();
			final long s = comp.size();
			if ( isComponentContainingYpos( comp, gapSepYPos ) ) {
				if ( s < min ) {
					min = s;
					ret = hyp;
				}
			}
		}
		return ret;
	}

	/**
	 * @param comp
	 * @param gapSepYPos
	 * @return
	 */
	private boolean isComponentContainingYpos( final Component< FloatType, ? > comp, final int gapSepYPos ) {
		for (Localizable localizable : comp) {
			if (gapSepYPos == localizable.getIntPosition(0)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Adds a constraint that forces a solution of this ILP to contain a certain
	 * segment hypothesis.
	 * To avoid requesting solutions that conflict with the tree constraints,
	 * the second parameter can be the hypothesis at the same location for which
	 * such a constraint exists so far.
	 *
	 * @param hyp2add
	 *            the hypothesis for which the constraint should be installed.
	 * @throws GRBException
	 */
	public void addSegmentInSolutionConstraint( final Hypothesis< Component< FloatType, ? >> hyp2add, final List< Hypothesis< Component< FloatType, ? >>> hyps2remove ) throws GRBException {
		final GRBLinExpr expr = new GRBLinExpr();

		// Remove constraints form all given hypotheses
		if ( hyps2remove != null ) {
			for ( final Hypothesis< Component< FloatType, ? >> hyp2remove : hyps2remove ) {
				final GRBConstr oldConstr = hyp2remove.getSegmentSpecificConstraint();
				if ( oldConstr != null ) {
					try {
						model.remove( oldConstr );
						hyp2remove.setSegmentSpecificConstraint( null );
					} catch ( final GRBException e ) {
						e.printStackTrace();
					}
				}
			}
		}

		final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > rightNeighbors = edgeSets.getRightNeighborhood( hyp2add );
		for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> assmnt : rightNeighbors ) {
			expr.addTerm( 1.0, assmnt.getGRBVar() );
		}

		// Store the newly created constraint in hyp2add
		hyp2add.setSegmentSpecificConstraint( model.addConstr( expr, GRB.EQUAL, 1.0, "sisc_" + hyp2add.hashCode() ) );
	}

	/**
	 * Adds a constraint that forces any solution of this ILP to avoid a certain
	 * segment hypothesis.
	 *
	 * @param hyp2avoid
	 * @throws GRBException
	 */
	public void addSegmentNotInSolutionConstraint( final Hypothesis< Component< FloatType, ? >> hyp2avoid ) throws GRBException {
		final GRBLinExpr expr = new GRBLinExpr();

		final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > rightNeighbors = edgeSets.getRightNeighborhood( hyp2avoid );
		for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> assmnt : rightNeighbors ) {
			expr.addTerm( 1.0, assmnt.getGRBVar() );
		}

		hyp2avoid.setSegmentSpecificConstraint( model.addConstr( expr, GRB.EQUAL, 0.0, "snisc_" + hyp2avoid.hashCode() ) );
	}

	public void addProgressListener( final ProgressListener pl ) {
		if ( pl != null ) {
			this.progressListener.add( pl );
		}
	}

	private void reportProgress() {
		for ( final ProgressListener pl : this.progressListener ) {
			pl.hasProgressed();
		}
	}

	/**
	 * @param file
	 */
	public void saveState( final File file ) {
		BufferedWriter out;
		try {
			out = new BufferedWriter( new FileWriter( file ) );
			out.write( "# " + MoMA.VERSION_STRING );
			out.newLine();
			out.newLine();

			// Write characteristics of dataset
			// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
			final int numT = gl.size() - 1;
			int numH = 0;
			for ( final List< Hypothesis< Component< FloatType, ? >>> innerList : nodes.getAllHypotheses() ) {
				for ( @SuppressWarnings( "unused" )
				final Hypothesis< Component< FloatType, ? >> hypothesis : innerList ) {
					numH++;
				}
			}
			int numA = 0;
			for ( final List< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > innerList : nodes.getAllAssignments() ) {
				for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> assignment : innerList ) {
					numA++;
				}
			}
			out.write( String.format( "TIME, %d, %d, %d\n", numT,
					MoMA.getMinTime(), MoMA.getMaxTime() ) );
			out.write( String.format( "SIZE, %d, %d\n", numH, numA ) );
			out.write( String.format( "BOTTOM_OFFSET, %d\n", MoMA.GL_OFFSET_BOTTOM ) );
			out.newLine();

			final int timeOffset = MoMA.getMinTime();

			// SegmentsInFrameCountConstraints
			// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
			out.write( "# SegmentsInFrameCountConstraints\n" );
			for ( int t = 0; t < gl.size(); t++ ) {
				final int value = getSegmentsInFrameCountConstraintRHS( t );
				if ( value >= 0 ) {
					out.write( String.format( "\tSIFCC, %d, %d\n", t + timeOffset, value ) );
				}
			}

			// Include/Exclude Segment Constraints
			// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
			out.write( "# SegmentSelectionConstraints (SSC)\n" );
			for ( int t = 0; t < gl.size(); t++ ) {
				final List< Hypothesis< Component< FloatType, ? >>> hyps =
						nodes.getHypothesesAt( t );
				for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
					if ( hyp.getSegmentSpecificConstraint() != null ) {
						double rhs;
						try {
							rhs = hyp.getSegmentSpecificConstraint().get( GRB.DoubleAttr.RHS );
							out.write( String.format(
									"\tSSC, %d, %d, %s\n",
									t + timeOffset,
									hyp.getId(),
									rhs ) );
						} catch ( final GRBException e ) {
//							out.write( String.format( "\tSSC, %d, %d, GUROBI_ERROR\n", t + timeOffset, hyp.getId() ) );
						}
					}
				}
			}

			// Include/Exclude Assignment Constraints
			// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
			out.write( "# AssignmentSelectionConstraints (ASC)\n" );
			for ( int t = 0; t < gl.size(); t++ ) {
				final List< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > assmnts =
						nodes.getAssignmentsAt( t );
				for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> assmnt : assmnts ) {
					if ( assmnt.getGroundTroothConstraint() != null ) {
						double rhs;
						try {
							rhs = assmnt.getGroundTroothConstraint().get( GRB.DoubleAttr.RHS );
							out.write( String.format(
									"\tASC, %d, %d, %s\n",
									t + timeOffset,
									assmnt.getId(),
									rhs ) );
						} catch ( final GRBException e ) {
//							out.write( String.format("\tASC, %d, %d, GUROBI_ERROR\n", t + timeOffset, assmnt.getId() ) );
						}
					}
				}
			}

			// Pruning Roots
			// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
			out.write( "# PruningRoots (PR)\n" );
			for ( int t = 0; t < gl.size(); t++ ) {
				final List< Hypothesis< Component< FloatType, ? >>> hyps =
						nodes.getHypothesesAt( t );
				for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
					if ( hyp.isPruneRoot() ) {
						out.write( String.format( "\tPR, %d, %d\n", t + timeOffset, hyp.getId() ) );
					}
				}
			}

			out.close();
		} catch ( final IOException e ) {
			e.printStackTrace();
		}
	}

	/**
	 * @param file
	 * @throws IOException
	 */
	public void loadState( final File file ) throws IOException {
		final BufferedReader reader = new BufferedReader( new FileReader( file ) );

		final List< Hypothesis< ? >> pruneRoots = new ArrayList<>();

		final int timeOffset = MoMA.getMinTime();

		String line;
		while ( ( line = reader.readLine() ) != null ) {
			// ignore comments and empty lines
			if ( line.trim().startsWith( "#" ) || line.trim().length() == 0 ) continue;

			final String[] columns = line.split( "," );
			if ( columns.length > 1 ) {
				final String constraintType = columns[ 0 ].trim();

				// DataProperties (to see if this load makes any sense)
				// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
				if ( constraintType.equals( "TIME" ) ) {
					final int readNumT = Integer.parseInt( columns[ 1 ].trim() );
					final int readTmin = Integer.parseInt( columns[ 2 ].trim() );
					final int readTmax = Integer.parseInt( columns[ 3 ].trim() );

					if ( MoMA.getMinTime() != readTmin || MoMA.getMaxTime() != readTmax ) {
						if ( !MoMA.HEADLESS ) {
							JOptionPane.showMessageDialog(
									MoMA.getGui(),
									"Tracking to be loaded is at best a partial fit.\nMatching data will be loaded whereever possible...",
									"Warning",
									JOptionPane.WARNING_MESSAGE );
						} else {
							System.out.println( "Tracking to be loaded is at most a partial fit. Continue to load matching data..." );
							System.exit( 946 );
						}
					}
				}
				if ( constraintType.equals( "SIZE" ) ) {
					final int readNumH = Integer.parseInt( columns[ 1 ].trim() );
					final int readNumA = Integer.parseInt( columns[ 2 ].trim() );
				}

				// SegmentsInFrameCountConstraints
				// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
				if ( constraintType.equals( "SIFCC" ) ) {
					try {
						final int t = Integer.parseInt( columns[ 1 ].trim() ) - timeOffset;
						final int numCells = Integer.parseInt( columns[ 2 ].trim() );
						try {
							System.out.println( String.format( "SIFCC %d %d", t, numCells ) );
							this.addSegmentsInFrameCountConstraint( t, numCells );
						} catch ( final GRBException e ) {
							e.printStackTrace();
						}
					} catch ( final NumberFormatException e ) {
						e.printStackTrace();
					}
				}
				// SegmentationConstraints
				// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
				if ( constraintType.equals( "SSC" ) ) {
					try {
						final int t = Integer.parseInt( columns[ 1 ].trim() ) - timeOffset;
						final int id = Integer.parseInt( columns[ 2 ].trim() );
						final double rhs = Double.parseDouble( columns[ 3 ].trim() );
						try {
							System.out.println( String.format( "SSC %d %d %f", t, id, rhs ) );
							final List< Hypothesis< Component< FloatType, ? >>> hyps =
									nodes.getHypothesesAt( t );
							for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
								if ( hyp.getId() == id ) {
									if ( 1 == ( int ) rhs ) {
										addSegmentInSolutionConstraint( hyp, null );
									} else {
										addSegmentNotInSolutionConstraint( hyp );
									}
								}
							}
						} catch ( final GRBException e ) {
							e.printStackTrace();
						}
					} catch ( final NumberFormatException e ) {
						e.printStackTrace();
					}
				}
				// AssignmentConstraints
				// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
				if ( constraintType.equals( "ASC" ) ) {
					try {
						final int t = Integer.parseInt( columns[ 1 ].trim() ) - timeOffset;
						final int id = Integer.parseInt( columns[ 2 ].trim() );
						final double rhs = Double.parseDouble( columns[ 3 ].trim() );
						System.out.println( String.format( "ASC %d %d %f", t, id, rhs ) );
						final List< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > assmnts =
								nodes.getAssignmentsAt( t );
						for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> assmnt : assmnts ) {
							if ( assmnt.getId() == id ) {
								if ( 1 == ( int ) rhs ) {
									assmnt.setGroundTruth( true );
								} else {
									assmnt.setGroundUntruth( true );
								}
							}
						}
					} catch ( final NumberFormatException e ) {
						e.printStackTrace();
					}
				}
				// Pruning Roots
				// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
				if ( constraintType.equals( "PR" ) ) {
					try {
						final int t = Integer.parseInt( columns[ 1 ].trim() ) - timeOffset;
						final int id = Integer.parseInt( columns[ 2 ].trim() );
						System.out.println( String.format( "PR %d %d", t, id ) );
						final List< Hypothesis< Component< FloatType, ? >>> hyps =
								nodes.getHypothesesAt( t );
						for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
							if ( hyp.getId() == id ) {
								pruneRoots.add( hyp );
							}
						}
					} catch ( final NumberFormatException e ) {
						e.printStackTrace();
					}
				}
			}
		}
		reader.close();

		try {
			model.update();
			run();
		} catch ( final GRBException e ) {
			e.printStackTrace();
		}

		// Activate all PruneRoots
		for ( final Hypothesis< ? > hyp : pruneRoots ) {
			hyp.setPruneRoot( true, this );
		}
		MoMA.getGui().dataToDisplayChanged();
	}

	/**
	 * @param t
	 */
	public void fixSegmentationAsIs( final int t ) {
		final List< Hypothesis< Component< FloatType, ? >>> hyps =
				nodes.getHypothesesAt( t );
		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			// only if hypothesis is not already clamped
			if ( hyp.getSegmentSpecificConstraint() == null ) {
				Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > nh;
				nh = edgeSets.getRightNeighborhood( hyp );

				try {
					final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> aa =
							findActiveAssignment( nh );
					if ( aa != null ) {
						// fix this segment
						addSegmentInSolutionConstraint( hyp, null );
					} else {
						// avoid this segment
						addSegmentNotInSolutionConstraint( hyp );
					}
				} catch ( final GRBException e ) {
					//				e.printStackTrace();
				}
			}
		}
	}

	/**
	 * @param t
	 */
	public void fixAssignmentsAsAre( final int t ) {
		// TODO: don't forget that assignment constraints removal kills also fixed segmentation
		final List< Hypothesis< Component< FloatType, ? >>> hyps =
				nodes.getHypothesesAt( t );
		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > nh;
			nh = edgeSets.getRightNeighborhood( hyp );
			if ( nh == null ) continue;
			for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> assmnt : nh ) {
				if ( assmnt.getGroundTroothConstraint() == null ) {
					try {
						if ( assmnt.isChoosen() ) {
							assmnt.setGroundTruth( true );
						} else {
							assmnt.setGroundUntruth( true );
						}
					} catch ( final GRBException e ) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	/**
	 * @param t
	 */
	public void removeAllSegmentConstraints( final int t ) {
		final List< Hypothesis< Component< FloatType, ? >>> hyps =
				nodes.getHypothesesAt( t );
		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			final GRBConstr oldConstr = hyp.getSegmentSpecificConstraint();
			// remove all existing
			if ( oldConstr != null ) {
				try {
					model.remove( oldConstr );
					hyp.setSegmentSpecificConstraint( null );
				} catch ( final GRBException e ) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * @param t
	 */
	public void removeAllAssignmentConstraints( final int t ) {
		final List< Hypothesis< Component< FloatType, ? >>> hyps =
				nodes.getHypothesesAt( t );
		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > nh;
			nh = edgeSets.getRightNeighborhood( hyp );
			if ( nh == null ) continue;
			for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> assmnt : nh ) {
				if ( assmnt.getGroundTroothConstraint() != null ) {
					assmnt.setGroundTruth( false );
				}
			}
		}
	}

	/**
	 */
	public void ignoreBeyond( final int t ) {
		if ( t + 1 >= gl.size() ) {
			// remove ignore-constraints altogether
			for ( int i = 0; i < gl.size(); i++ ) {
				unignoreSegmentsAt( i );
			}
		} else {
			// remove ignore-constraints at [0,t]
			for ( int i = 0; i <= t; i++ ) {
				unignoreSegmentsAt( i );
			}
			// add ignore-constraints at [t+1,T]
			for ( int i = t + 1; i < gl.size(); i++ ) {
				ignoreSegmentsAt( i );
			}
		}
	}

	/**
	 * @param t
	 */
	private void unignoreSegmentsAt( final int t ) {
		final List< Hypothesis< Component< FloatType, ? >>> hyps =
				nodes.getHypothesesAt( t );
		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			final GRBConstr constr = ignoreSegmentConstraints.get( hyp );
			if ( constr != null ) {
				try {
					model.remove( constr );
					ignoreSegmentConstraints.remove( hyp );
				} catch ( final GRBException e ) {
//					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * @param t
	 */
	private void ignoreSegmentsAt( final int t ) {
		final List< Hypothesis< Component< FloatType, ? >>> hyps =
				nodes.getHypothesesAt( t );
		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			if ( ignoreSegmentConstraints.get( hyp ) == null ) {
				try {
					final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > rightNeighbors =
							edgeSets.getRightNeighborhood( hyp );
					final GRBLinExpr expr = new GRBLinExpr();
					if ( rightNeighbors != null ) {
						for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> assmnt : rightNeighbors ) {
							expr.addTerm( 1.0, assmnt.getGRBVar() );
						}
						final GRBConstr constr =
								model.addConstr( expr, GRB.EQUAL, 0.0, "ignore_" + hyp.hashCode() );
						ignoreSegmentConstraints.put( hyp, constr );
					}
				} catch ( final GRBException e ) {
//					e.printStackTrace();
				}
			}
		}
	}

	/**
	 */
	public void freezeBefore( final int t ) {
		for ( int i = 0; i <= t; i++ ) {
			freezeAssignmentsAsAre( i );
		}
		for ( int i = t + 1; i < gl.size(); i++ ) {
			unfreezeAssignmentsFor( i );
		}
	}

	/**
	 */
	public void freezeAssignmentsAsAre( final int t ) {
		final List< Hypothesis< Component< FloatType, ? >>> hyps =
				nodes.getHypothesesAt( t );
		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			if ( freezeSegmentConstraints.get( hyp ) == null ) {
				try {
					final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > rightNeighbors =
							edgeSets.getRightNeighborhood( hyp );
					final GRBLinExpr expr = new GRBLinExpr();
					if ( rightNeighbors != null ) {
						double rhs = 0.0;
						for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> assmnt : rightNeighbors ) {
							if ( assmnt.isChoosen() ) {
								expr.addTerm( 1.0, assmnt.getGRBVar() );
								rhs = 1.0;
							} else {
								expr.addTerm( 2.0, assmnt.getGRBVar() );
							}
						}
						final GRBConstr constr =
								model.addConstr( expr, GRB.EQUAL, rhs, "freeze_" + hyp.hashCode() );
						freezeSegmentConstraints.put( hyp, constr );
					}
				} catch ( final GRBException e ) {
//					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * @param t
	 */
	private void unfreezeAssignmentsFor( final int t ) {
		final List< Hypothesis< Component< FloatType, ? >>> hyps =
				nodes.getHypothesesAt( t );
		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			final GRBConstr constr = freezeSegmentConstraints.get( hyp );
			if ( constr != null ) {
				try {
					model.remove( constr );
					freezeSegmentConstraints.remove( hyp );
				} catch ( final GRBException e ) {
//					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Returns the CostManager set here.
	 */
	public CostManager getCostManager() {
		return costManager;
	}

	/**
	 * Stores the tracking problem according to the format designed with Paul
	 * Swoboda (IST).
	 * See also: https://docs.google.com/document/d/1f_L3PF8WQZdLZsQZb7xb_Z7GwZ9RN1_yotGeWjb-ihU/edit
	 *
	 * @param file
	 */
	public void exportFG_PAUL( final File file ) {

		FactorGraphFileBuilder_PAUL fgFile;
		try {
			fgFile = new FactorGraphFileBuilder_PAUL( model.get( GRB.DoubleAttr.ObjVal ) );
			System.out.println( "Exporting also LP file (since model is optimized)." );
			model.write( file.getPath() + ".lp" );
		} catch ( final GRBException e ) {
			fgFile = new FactorGraphFileBuilder_PAUL();
		}

		// HYPOTHESES SECTION
		for ( int t = 0; t < nodes.getNumberOfTimeSteps(); t++ ) {

			fgFile.markNextTimepoint();

			final List< Hypothesis< Component< FloatType, ? > > > hyps_t = nodes.getAllHypotheses().get( t );
			for ( final Hypothesis< Component< FloatType, ? > > hyp : hyps_t ) {

				// variables for assignments
				final int hyp_id = fgFile.addHyp( this, hyp );
			}

			// Get the full component tree
			final ComponentForest< ? > ct = gl.get( t ).getComponentTree();
			// And call the function adding all the path-blocking-constraints...
			for ( final Component< ?, ? > ctRoot : ct.roots() ) {
				// And call the function adding all the path-blocking-constraints...
				recursivelyAddPathBlockingHypotheses( fgFile, ctRoot, t );
			}
		}

		// HYPOTHESES SECTION
		fgFile.addLine( "\n# ASSIGNMENTS ASSIGNMENTS ASSIGNMENTS ASSIGNMENTS ASSIGNMENTS ASSIGNMENTS ASSIGNMENTS" );

		fgFile.addLine( "\n# MAPPINGS" );
		for ( int t = 0; t < nodes.getNumberOfTimeSteps(); t++ ) {
			final List< Hypothesis< Component< FloatType, ? > > > hyps_t = nodes.getAllHypotheses().get( t );
			for ( final Hypothesis< Component< FloatType, ? > > hyp : hyps_t ) {
				final HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > > mapRightNeighbors =
						this.getAllCompatibleRightAssignments( t );
				final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > assmnts = mapRightNeighbors.get( hyp );
				if ( assmnts != null ) {
					for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > assmnt : assmnts ) {
						if ( assmnt instanceof MappingAssignment ) {
							fgFile.addMapping( this, t, ( MappingAssignment ) assmnt );
						}
					}
				}
			}
		}

		fgFile.addLine( "\n# DIVISIONS" );
		for ( int t = 0; t < nodes.getNumberOfTimeSteps(); t++ ) {
			final List< Hypothesis< Component< FloatType, ? > > > hyps_t = nodes.getAllHypotheses().get( t );
			for ( final Hypothesis< Component< FloatType, ? > > hyp : hyps_t ) {
				final HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > > mapRightNeighbors =
						this.getAllCompatibleRightAssignments( t );
				final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > assmnts = mapRightNeighbors.get( hyp );
				if ( assmnts != null ) {
					for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? > > > assmnt : assmnts ) {
						if ( assmnt instanceof DivisionAssignment ) {
							fgFile.addDivision( this, t, ( DivisionAssignment ) assmnt );
						}
					}
				}
			}
		}

		// WRITE FILE
		fgFile.write( file );
	}

	private void recursivelyAddPathBlockingHypotheses(
			final FactorGraphFileBuilder_PAUL fgFile,
			final Component< ?, ? > ctNode,
			final int t ) {

		// if ctNode is a leave node -> add constraint (by going up the list of
		// parents and building up the constraint)
		if ( ctNode.getChildren().size() == 0 ) {
			Component< ?, ? > runnerNode = ctNode;

			final List< Hypothesis< Component< FloatType, ? > > > hyps = new ArrayList<>();
			while ( runnerNode != null ) {
				@SuppressWarnings( "unchecked" )
				final Hypothesis< Component< FloatType, ? > > hypothesis =
						( Hypothesis< Component< FloatType, ? > > ) nodes.findHypothesisContaining( runnerNode );
				if ( hypothesis == null ) {
					System.err.println(
							"A WARNING: Hypothesis for a CTN was not found in GrowthLineTrackingILP -- this is an indication for some design problem of the system!" );
				} else {
					hyps.add( hypothesis );
				}

				runnerNode = runnerNode.getParent();
			}
			// Add the Exclusion Constraint (finally)
			fgFile.addPathBlockingConstraint( hyps );
		} else {
			// if ctNode is a inner node -> recursion
			for ( final Component< ?, ? > ctChild : ctNode.getChildren() ) {
				recursivelyAddPathBlockingHypotheses( fgFile, ctChild, t );
			}
		}
	}

}
