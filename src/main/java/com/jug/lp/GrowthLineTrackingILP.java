package com.jug.lp;

import com.jug.GrowthLine;
import com.jug.GrowthLineFrame;
import com.jug.MoMA;
import com.jug.gui.progress.DialogGurobiProgress;
import com.jug.gui.progress.ProgressListener;
import com.jug.lp.costs.CostFactory;
import com.jug.util.ComponentTreeUtils;
import com.jug.util.componenttree.SimpleComponent;
import com.jug.util.componenttree.SimpleComponentTree;
import gurobi.*;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.algorithm.componenttree.ComponentForest;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.function.Function;

import static com.jug.util.ComponentTreeUtils.getComponentSize;
import static com.jug.util.ComponentTreeUtils.getLeafNodes;

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
	public static final int ASSIGNMENT_LYSIS = 3;

	public static final float CUTOFF_COST = Float.MAX_VALUE; // TODO-PARAMETRIZE: This value is critical(!): Assignments with costs higher than this value will be ignored. This should become a parameter at some point!
	public static final float LYSIS_ASSIGNMENT_COST = 10; // NOTE: This value is set so high, that it will not be considered for assignment during optimization. However, it can be forced during curation.

	private static GRBEnv env;

	// -------------------------------------------------------------------------------------
	// fields
	// -------------------------------------------------------------------------------------
	private final GrowthLine gl;

	public GRBModel model;
	private int status = OPTIMIZATION_NEVER_PERFORMED;

	private final AssignmentsAndHypotheses< AbstractAssignment< Hypothesis< Component< FloatType, ? > > >, Hypothesis< Component< FloatType, ? > > > nodes =
			new AssignmentsAndHypotheses<>();  // all variables of FG
	private final HypothesisNeighborhoods< Hypothesis< Component< FloatType, ? > >, AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > edgeSets =
			new HypothesisNeighborhoods<>();  // incoming and outgoing assignments per hypothesis

	private final HashMap< Hypothesis< Component< FloatType, ? > >, GRBConstr > ignoreSegmentConstraints =
			new HashMap<>(); // for user interaction: avoid node
	private final HashMap< Hypothesis< Component< FloatType, ? > >, GRBConstr > freezeSegmentConstraints =
			new HashMap<>(); // for user interaction: force node

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

	public AssignmentsAndHypotheses< AbstractAssignment< Hypothesis< Component< FloatType, ? > > >, Hypothesis< Component< FloatType, ? > > > getNodes(){
		return nodes;
	}

	public HypothesisNeighborhoods< Hypothesis< Component< FloatType, ? > >, AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > getEdgeSets(){
		return edgeSets;
	}

	// -------------------------------------------------------------------------------------
	// methods
	// -------------------------------------------------------------------------------------
	public void buildILP() {
		try {
			// add Hypothesis and Assignments
			createHypsAndAssignments();

			HypothesesAndAssignmentsSanityChecker sanityChecker = new HypothesesAndAssignmentsSanityChecker(gl, nodes, edgeSets);
			sanityChecker.checkIfAllComponentsHaveCorrespondingHypothesis();
			sanityChecker.checkIfAllComponentsHaveMappingAssignmentsBetweenThem();

			// UPDATE GUROBI-MODEL
			// - - - - - - - - - -
			model.update();

			// Iterate over all assignments and ask them to add their
			// constraints to the model
			// - - - - - - - - - - - - - - - - - - - - - - - - - - - -
			for ( final List< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > innerList : nodes.getAllAssignments() ) {
				for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> assignment : innerList ) {
					assignment.addConstraintsToLP();
				}
			}

			// Add the remaining ILP constraints
			// (those would be (i) and (ii) of 'Default Solution')
			// - - - - - - - - - - - - - - - - - - - - - - - - - -
			addPathBlockingConstraints();
			addContinuityConstraints();

			// UPDATE GUROBI-MODEL
			// - - - - - - - - - -
			model.update();
//			System.out.println( "Constraints added: " + model.getConstrs().length );

//			String modelPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/Dany_20190515/20190515_hi1_med1_med2_rpmB_glu_gly_7_chr_hi1_deepmoma_2020-01-06_d6845a45/20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos0_preproc_GL06_deepmoma/";
//			model.write(modelPath + "/gurobi_model.rew");

			/* Set Gurobi model parameters */
			int aggregateVal = model.get(GRB.IntParam.Aggregate);
			System.out.println(String.format("Aggregate old value: %d", aggregateVal));
			model.set(GRB.IntParam.Aggregate, 1);
			aggregateVal = model.get(GRB.IntParam.Aggregate);
			System.out.println(String.format("Aggregate new value: %d", aggregateVal));

			int scaleFlagVal = model.get(GRB.IntParam.ScaleFlag);
			System.out.println(String.format("scaleFlag old value: %d", scaleFlagVal));
			model.set(GRB.IntParam.ScaleFlag, 2);
			scaleFlagVal = model.get(GRB.IntParam.ScaleFlag);
			System.out.println(String.format("scaleFlag new value: %d", scaleFlagVal));

//			int numericFocusVal = model.get(GRB.IntParam.NumericFocus);
//			System.out.println(String.format("numericFocus old value: %d", numericFocusVal));
//			model.set(GRB.IntParam.NumericFocus, 3);
//			numericFocusVal = model.get(GRB.IntParam.NumericFocus);
//			System.out.println(String.format("numericFocus new value: %d", numericFocusVal));

//			int quadVal = model.get(GRB.IntParam.Quad);
//			System.out.println(String.format("Quad old value: %d", quadVal));
//			model.set(GRB.IntParam.Quad, 1);
//			quadVal = model.get(GRB.IntParam.Quad);
//			System.out.println(String.format("Quad new value: %d", quadVal));
//
//			double markowitzTolVal = model.get(GRB.DoubleParam.MarkowitzTol);
//			System.out.println(String.format("MarkowitzTol old value: %f", markowitzTolVal));
//			model.set(GRB.DoubleParam.MarkowitzTol, 0.5);
//			markowitzTolVal = model.get(GRB.DoubleParam.MarkowitzTol);
//			System.out.println(String.format("MarkowitzTol new value: %f", markowitzTolVal));

//			double objScaleVal = model.get(GRB.DoubleParam.ObjScale);
//			System.out.println(String.format("objScale old value: %f", objScaleVal));
//			model.set(GRB.DoubleParam.ObjScale, 0.5);
//			objScaleVal = model.get(GRB.DoubleParam.ObjScale);
//			System.out.println(String.format("objScale new value: %f", objScaleVal));

			model.update();

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
//		createSegmentationHypotheses( 0 );
		for ( int t = 1; t < gl.size(); t++ ) {
//			createSegmentationHypotheses( t );
			enumerateAndAddAssignments( t - 1 );
		}
		// add exit assignments to last (hidden/duplicated) timepoint					 - MM-2019-06-04: Apparently the duplicate frame that MoMA adds is on purpose!
		// in order have some right assignment for LP hypotheses variable substitution.
		final List< Hypothesis< Component< FloatType, ? > > > curHyps = nodes.getHypothesesAt( gl.size() - 1 );
		addExitAssignments( gl.size() - 1, curHyps ); // might be obsolete, because we already duplicate the last image and therefore do this in enumerateAndAddAssignments(t-1); CHECK THIS!
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
	 * @param component
	 *            a node in a <code>ComponentTree</code>.
	 * @param t
	 *            the time-index the ctNode comes from.
	 */
	private void recursivelyAddCTNsAsHypotheses( final int t, final Component< FloatType, ? > component ) { //, final boolean isForParaMaxFlowSumImg
		float componentCost = getComponentCost( t, component );
		nodes.addHypothesis( t, new Hypothesis<>(t, component, componentCost) );
		for ( final Component< FloatType, ? > ctChild : component.getChildren() ) {
			recursivelyAddCTNsAsHypotheses( t, ctChild );
		}
	}

	/**
	 * @param t
	 * @param ctNode
	 * @return
	 */
	public float getComponentCost(final int t, final Component< ?, ? > ctNode ) {
		RandomAccessibleInterval<FloatType> img = Views.hyperSlice( MoMA.instance.getImgProbs(), 2, t);
		return CostFactory.getComponentCost( ctNode, img );
	}

	/**
	 * For time-points t and t+1, enumerates all potentially
	 * interesting assignments using the <code>addXXXAsignment(...)</code>
	 * methods.
	 *
	 * @throws GRBException
	 */
	private void enumerateAndAddAssignments( final int timeStep ) throws GRBException {
		SimpleComponentTree<FloatType, SimpleComponent<FloatType>> sourceComponentTree =
				(SimpleComponentTree<FloatType, SimpleComponent<FloatType>>) gl.getFrames().get(timeStep).getComponentTree();
		SimpleComponentTree<FloatType, SimpleComponent<FloatType>> targetComponentTree =
				(SimpleComponentTree<FloatType, SimpleComponent<FloatType>>) gl.getFrames().get(timeStep + 1).getComponentTree();

		addMappingAssignments( timeStep, sourceComponentTree, targetComponentTree  );
		addDivisionAssignments( timeStep, sourceComponentTree, targetComponentTree );
		addExitAssignments( timeStep, nodes.getHypothesesAt( timeStep ) );
		addLysisAssignments( timeStep, nodes.getHypothesesAt( timeStep ) );
		this.reportProgress();
	}

	/**
	 * Add an lysis-assignment at time t to a bunch of segmentation hypotheses.
	 *
	 * @param t
	 *            the time-point.
	 * @param hyps
	 *            a list of hypothesis for which an <code>ExitAssignment</code>
	 *            should be added.
	 * @throws GRBException
	 */
	private void addLysisAssignments( final int t, final List< Hypothesis< Component< FloatType, ? >>> hyps ) throws GRBException {
		if ( hyps == null ) return; // edge case?!

		float cost;
		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			cost = LYSIS_ASSIGNMENT_COST;

			final GRBVar newLPVar = model.addVar( 0.0, 1.0, cost, GRB.BINARY, String.format( "a_%d^LYSIS--%d", t, hyp.getId() ) );
			final LysisAssignment ea = new LysisAssignment(newLPVar, this, nodes, edgeSets, hyp );
			nodes.addAssignment( t, ea );
			edgeSets.addToRightNeighborhood( hyp, ea ); // relevant for continuity constraint (and probably other things(?))
		}
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
		if ( hyps == null ) return; // edge case?!

		float cost;
		for ( final Hypothesis< Component< FloatType, ? >> hyp : hyps ) {
			cost = costModulationForSubstitutedILP( hyp.getCost() );

			final GRBVar newLPVar = model.addVar( 0.0, 1.0, cost, GRB.BINARY, String.format( "a_%d^EXIT--%d", t, hyp.getId() ) );
			final List< Hypothesis< Component< FloatType, ? >>> Hup = LpUtils.getHup( hyp, hyps ); // LpUtils.getHup: get all nodes above ctn-hyps. CTN: component-tree-node
			final ExitAssignment ea = new ExitAssignment(newLPVar, this, nodes, edgeSets, Hup, hyp );
			nodes.addAssignment( t, ea );
			edgeSets.addToRightNeighborhood( hyp, ea ); // relevant for continuity constraint (and probably other things(?))
		}
	}

	/**
	 * Add mapping-assignments between source components in {@param sourceComponentTree} and target components in
	 * {@param targetComponentTree}.
	 *
	 * @param t
	 *            the time-point from which the <code>curHyps</code> originate.
	 * @param sourceComponentTree
	 *            the component tree containing source components of the mapping-assignments.
	 * @param targetComponentTree
	 *            the component tree containing target components of the mapping-assignments.
	 * @throws GRBException
	 */
	private void addMappingAssignments(final int t,
									   SimpleComponentTree<FloatType, SimpleComponent<FloatType>> sourceComponentTree,
									   SimpleComponentTree<FloatType, SimpleComponent<FloatType>> targetComponentTree) throws GRBException {
		for ( final SimpleComponent< FloatType > sourceComponent : sourceComponentTree.getAllComponents() ) {

			if (t > 0) {
				if (nodes.findHypothesisContaining(sourceComponent) == null)
					continue; /* we only want to continue paths of previously existing hypotheses; this is to fulfill the continuity constraint */
			}

			float sourceComponentCost = getComponentCost( t, sourceComponent );

			for ( final SimpleComponent< FloatType > targetComponent : targetComponentTree.getAllComponents() ) {
				float targetComponentCost = getComponentCost( t + 1, targetComponent );

				if ( !( ComponentTreeUtils.isBelowByMoreThen( targetComponent, sourceComponent, MoMA.MAX_CELL_DROP ) ) ) {

					final Float compatibilityCostOfMapping = compatibilityCostOfMapping( sourceComponent, targetComponent );
					float cost = costModulationForSubstitutedILP( sourceComponentCost, targetComponentCost, compatibilityCostOfMapping );
					cost = scaleAssignmentCost(sourceComponent, targetComponent, cost);

					if ( cost <= CUTOFF_COST ) {
						final Hypothesis< Component< FloatType, ? >> to =
								nodes.getOrAddHypothesis( t + 1, new Hypothesis<>(t + 1, targetComponent, targetComponentCost) );
						final Hypothesis< Component< FloatType, ? >> from =
								nodes.getOrAddHypothesis( t, new Hypothesis<>(t, sourceComponent, sourceComponentCost) );

						final String name = String.format( "a_%d^MAPPING--(%d,%d)", t, from.getId(), to.getId() );
						final GRBVar newLPVar = model.addVar( 0.0, 1.0, cost, GRB.BINARY, name );

						final MappingAssignment ma = new MappingAssignment( t, newLPVar, this, nodes, edgeSets, from, to );
						nodes.addAssignment( t, ma );
						if (!edgeSets.addToRightNeighborhood(from, ma)) {
							System.err.println( "ERROR: Mapping-assignment could not be added to right neighborhood!" );
						}
						if (!edgeSets.addToLeftNeighborhood(to, ma)) {
							System.err.println( "ERROR: Mapping-assignment could not be added to left neighborhood!" );
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
	 * @param sourceComponent
	 *            the segmentation hypothesis from which the mapping originates.
	 * @param targetComponent
	 *            the segmentation hypothesis towards which the
	 *            mapping-assignment leads.
	 * @return the cost we want to set for the given combination of segmentation
	 *         hypothesis.
	 */
	public Float compatibilityCostOfMapping(
			final Component< FloatType, ? > sourceComponent,
			final Component< FloatType, ? > targetComponent ) {
		final long sourceComponentSize = getComponentSize(sourceComponent, 1);
		final long targetComponentSize = getComponentSize(targetComponent, 1);

		final ValuePair< Integer, Integer > sourceComponentBoundaries = ComponentTreeUtils.getComponentPixelLimits(sourceComponent, 1);
		final ValuePair< Integer, Integer > targetComponentBoundaries = ComponentTreeUtils.getComponentPixelLimits(targetComponent, 1);

		final float sourceUpperBoundary = sourceComponentBoundaries.getA();
		final float sourceLowerBoundary = sourceComponentBoundaries.getB();
		final float targetUpperBoundary = targetComponentBoundaries.getA();
		final float targetLowerBoundary = targetComponentBoundaries.getB();

		final Pair< Float, float[] > migrationCostOfUpperBoundary = CostFactory.getMigrationCost( sourceUpperBoundary, targetUpperBoundary );
		final Pair< Float, float[] > migrationCostOfLowerBoundary = CostFactory.getMigrationCost( sourceLowerBoundary, targetLowerBoundary );
		final float averageMigrationCost = 0.5f * migrationCostOfLowerBoundary.getA() + 0.5f * migrationCostOfUpperBoundary.getA();

		final Pair< Float, float[] > growthCost = CostFactory.getGrowthCost( sourceComponentSize, targetComponentSize );

		return growthCost.getA() + averageMigrationCost;
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
		return 0.1f * fromCost + 0.9f * toCost + mappingCosts; /* here again we fold the costs from the nodes into the corresponding assignment;
																  we should probably do 50%/50%, but we did different and it's ok */
	}

	/**
	 * This function scales the cost of given assignment by the number of leaves
	 * that are under the components participating in that assignment. The idea is
	 * that we a given component must have lower cost than the average cost of the
	 * leaves under it. This was suggested in Funke et. al., 2012, "Efficient Automatic
	 * 3D-Reconstruction of Branching Neurons from EM Data".
	 *
	 * @param sourceComponent source component of the mapping assignment
	 * @param targetComponent target component of the mapping assignment
	 * @param cost current cost, that will be rescaled
	 * @return rescaled cost
	 */
	public float scaleAssignmentCost(SimpleComponent sourceComponent,
									 SimpleComponent targetComponent,
									 float cost){
		int numberOfLeavesUnderSource = getLeafNodes(sourceComponent).size();
		int numberOfLeavesUnderTarget = getLeafNodes(targetComponent).size();
		if(numberOfLeavesUnderSource == 0)
			numberOfLeavesUnderSource = 1;
		if(numberOfLeavesUnderTarget == 0)
			numberOfLeavesUnderTarget = 1;
		return cost * (0.1f * numberOfLeavesUnderSource + 0.9f * numberOfLeavesUnderTarget);
	}

	/**
	 * This method defines how the segmentation costs are influencing the costs
	 * of division assignments during the ILP hypotheses substitution takes
	 * place.
	 *
	 * @param sourceComponentCost
	 * @param compatibilityCostOfDivision
	 * @return
	 */
	public float costModulationForSubstitutedILP(
			final float sourceComponentCost,
			final float upperTargetComponentCost,
			final float lowerTargetComponentCost,
			final float compatibilityCostOfDivision ) {
		return 0.1f * sourceComponentCost + 0.9f * ( upperTargetComponentCost + lowerTargetComponentCost ) / 2 + compatibilityCostOfDivision;
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
		return Math.min( 0.0f, fromCosts / 2f ); // NOTE: 0 or negative but only hyp/4 to prefer map or div if exists...
		// fromCosts/2: 1/2 has to do with the folding of the node-cost into the assignments (e.g. mapping: 1/2 to left und 1/2 to right)
		// Math.min: because exit assignment should never cost something
	}


	/**
	 * Add a division-assignment for given timestep between component in {@param sourceComponentTree} and
	 * {@param targetComponentTree}. This function also looks for suitable pairs of components in
	 * {@param targetComponentTree}, since division-assignments need two target component. The hypotheses of the
	 * components, which are needed for the assignments, are created on the fly as needed.
	 *
	 * @param timeStep
	 *            the time-point from which the <code>curHyps</code> originate.
	 * @param sourceComponentTree
	 *            the component tree containing source components of the division assignments.
	 * @param targetComponentTree
	 * 			  the component tree containing target components at the next time-point of the division assignments.
	 * @throws GRBException
	 */
	private void addDivisionAssignments(final int timeStep,
										SimpleComponentTree<FloatType, SimpleComponent<FloatType>> sourceComponentTree,
										SimpleComponentTree<FloatType, SimpleComponent<FloatType>> targetComponentTree)
			throws GRBException {

		for (final Component<FloatType, ?> sourceComponent : sourceComponentTree.getAllComponents()) {

			if (timeStep > 0) {
				if (nodes.findHypothesisContaining(sourceComponent) == null)
					continue; /* we only want to continue paths of previously existing hypotheses; this is to fulfill the continuity constraint */
			}

			float sourceComponentCost = getComponentCost(timeStep, sourceComponent);

			for (final Component<FloatType, ?> upperTargetComponent : targetComponentTree.getAllComponents()) {
				if (!(ComponentTreeUtils.isBelowByMoreThen(upperTargetComponent, sourceComponent, MoMA.MAX_CELL_DROP))) {

					float upperTargetComponentCost = getComponentCost(timeStep + 1, upperTargetComponent);
					final List<Component<FloatType, ?>> lowerNeighborComponents = ComponentTreeUtils.getLowerNeighbors(upperTargetComponent, targetComponentTree);

					for (final Component<FloatType, ?> lowerTargetComponent : lowerNeighborComponents) {
						@SuppressWarnings("unchecked")
						float lowerTargetComponentCost = getComponentCost(timeStep + 1, lowerTargetComponent);
						final Float compatibilityCostOfDivision = compatibilityCostOfDivision(sourceComponent,
								upperTargetComponent, lowerTargetComponent);

						float cost = costModulationForSubstitutedILP(
								sourceComponentCost,
								upperTargetComponentCost,
								lowerTargetComponentCost,
								compatibilityCostOfDivision);

						if (cost <= CUTOFF_COST) {
							final Hypothesis<Component<FloatType, ?>> to =
									nodes.getOrAddHypothesis(timeStep + 1, new Hypothesis<>(timeStep + 1, upperTargetComponent, upperTargetComponentCost));
							final Hypothesis<Component<FloatType, ?>> lowerNeighbor =
									nodes.getOrAddHypothesis(timeStep + 1, new Hypothesis<>(timeStep + 1, lowerTargetComponent, lowerTargetComponentCost));
							final Hypothesis<Component<FloatType, ?>> from =
									nodes.getOrAddHypothesis(timeStep, new Hypothesis<>(timeStep, sourceComponent, sourceComponentCost));

							final String name = String.format("a_%d^DIVISION--(%d,%d)", timeStep, from.getId(), to.getId());
							final GRBVar newLPVar = model.addVar(0.0, 1.0, cost, GRB.BINARY, name);

							final DivisionAssignment da = new DivisionAssignment(newLPVar, this, from, to, lowerNeighbor);
							nodes.addAssignment(timeStep, da);
							edgeSets.addToRightNeighborhood(from, da);
							edgeSets.addToLeftNeighborhood(to, da);
							edgeSets.addToLeftNeighborhood(lowerNeighbor, da);
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
	 * @param sourceComponent
	 *            the segmentation hypothesis from which the mapping originates.
	 * @return the cost we want to set for the given combination of segmentation
	 *         hypothesis.
	 */
	public Float compatibilityCostOfDivision(
			final Component< FloatType, ? > sourceComponent,
			final Component< FloatType, ? > upperTargetComponent,
			final Component< FloatType, ? > lowerTargetComponent ) {


		final ValuePair< Integer, Integer > sourceBoundaries = ComponentTreeUtils.getComponentPixelLimits(sourceComponent, 1);
		final ValuePair< Integer, Integer > upperTargetBoundaries = ComponentTreeUtils.getComponentPixelLimits(upperTargetComponent, 1);
		final ValuePair< Integer, Integer > lowerTargetBoundaries = ComponentTreeUtils.getComponentPixelLimits(lowerTargetComponent, 1);

		final long sourceSize = getComponentSize(sourceComponent, 1);
		final long upperTargetSize = getComponentSize(upperTargetComponent,1);
		final long lowerTargetSize = getComponentSize(lowerTargetComponent, 1);
		final long summedTargetSize = upperTargetSize + lowerTargetSize;

		final float sourceUpperBoundary = sourceBoundaries.getA();
		final float sourceLowerBoundary = sourceBoundaries.getB();
		final float upperTargetUpperBoundary = upperTargetBoundaries.getA();
		final float lowerTargetLowerBoundary = lowerTargetBoundaries.getB();

		final Pair< Float, float[] > migrationCostOfUpperBoundary = CostFactory.getMigrationCost( sourceUpperBoundary, upperTargetUpperBoundary );
		final Pair< Float, float[] > migrationCostOfLowerBoundary = CostFactory.getMigrationCost( sourceLowerBoundary, lowerTargetLowerBoundary );
		final float averageMigrationCost = .5f * migrationCostOfLowerBoundary.getA() + .5f * migrationCostOfUpperBoundary.getA();

		final Pair< Float, float[] > growthCost = CostFactory.getGrowthCost( sourceSize, summedTargetSize );
		final float unevenDivisionCost = CostFactory.getUnevenDivisionCost( upperTargetSize, lowerTargetSize );
		final float divisionLikelihoodCost = CostFactory.getDivisionLikelihoodCost( sourceComponent );

		return growthCost.getA() + averageMigrationCost + unevenDivisionCost + divisionLikelihoodCost;
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

	private < C extends Component< ?, C > > void recursivelyAddPathBlockingConstraints(
			final ComponentForest< C > ct,
			final int t )
			throws GRBException {
		for ( final C ctRoot : ct.roots() ) {
			// And call the function adding all the path-blocking-constraints...
			recursivelyAddPathBlockingConstraints( ctRoot, t );
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
				assert (hypothesis != null) : "WARNING: Hypothesis for a CTN was not found in GrowthLineTrackingILP -- this is an indication for some design problem of the system!";

				if ( edgeSets.getRightNeighborhood( hypothesis ) != null ) {
					for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> a : edgeSets.getRightNeighborhood( hypothesis ) ) {
						exprR.addTerm( 1.0, a.getGRBVar() );  // again we build the constraints for the assignments, because we do not optimize for nodes; we therefore need to add *all* right-assignments to the constraint of a given node
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

	/**
	 * This function generates and adds the explanation-continuity-constraints
	 * to the ILP model.
	 * Those constraints ensure that for each segmentation hypotheses at all
	 * time-points t we have the same number of active incoming and active
	 * outgoing edges from/to assignments.
	 * Intuitively speaking this means that each hypothesis that is chosen by an
	 * assignment coming from t-1 we need to continue its interpretation by
	 * finding an active assignment towards t+1.
	 */
	private void addContinuityConstraints() throws GRBException {
		int eccId = 0;

		// For each time-point
		for ( int t = 1; t < gl.size(); t++ ) {
			for ( final Hypothesis< Component< FloatType, ? >> hyp : nodes.getHypothesesAt( t ) ) {
				final GRBLinExpr expr = new GRBLinExpr();

				/* TODO-MM-2019-11-21: WARNING: The two separate null-checks below might cause problems in setting up ILP-constraint. If one is null and the other is not, we will have an asymmetric constraint.
				 * Additional note: While the above is true, we will have to find a solution for t=0/t=gl.size(), which do not have incoming/outgoing assignments.
				 */
				if ( edgeSets.getLeftNeighborhood( hyp ) != null ) {
					for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> a_j : edgeSets.getLeftNeighborhood( hyp ) ) {
						expr.addTerm( 1.0, a_j.getGRBVar() );
					}
				}
				else{
					System.out.println(String.format("addContinuityConstraints(): t=%d", t));
					System.out.println("edgeSets.getLeftNeighborhood( hyp ) == null");
				}
				if ( edgeSets.getRightNeighborhood( hyp ) != null ) {
					for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> a_j : edgeSets.getRightNeighborhood( hyp ) ) {
						expr.addTerm( -1.0, a_j.getGRBVar() );
					}
				}
				else{
					System.out.println(String.format("addContinuityConstraints(): t=%d", t));
					System.out.println("edgeSets.getRightNeighborhood( hyp ) == null");
				}

				// add the constraint for this hypothesis
				model.addConstr( expr, GRB.EQUAL, 0.0, "ecc_" + eccId );
				eccId++;
			}
		}
	}

	/**
	 * Performs autosave of current tracking interactions (if the checkbox in
	 * the MotherMachineGui is checked).
	 */
	public synchronized void autosave() {
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
	public synchronized void run() {
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

			new IlpSolutionSanityChecker(this, gl).CheckSolutionContinuityConstraintForAllTimesteps();
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
		return getOptimalHypotheses( t );
	}

	/**
	 * Returns the components of the optimal segmentation at time t.
	 * @param t time
	 * @return List<Component<FloatType, ?>> list of components at time t that are part of the optimal solution
	 */
	public List<Component<FloatType, ?>> getOptimalComponents(final int t){
		List<Hypothesis<Component<FloatType, ?>>> ilpSelectedHypotheses = getOptimalSegmentation(t);

		List<Component<FloatType, ?>> selectedComponents = new ArrayList<>();
		for (Hypothesis<Component<FloatType, ?>> hypothesis : ilpSelectedHypotheses) {
			selectedComponents.add(hypothesis.getWrappedComponent());
		}
		return selectedComponents;
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
					ComponentTreeUtils.getTreeNodeInterval( h.getWrappedComponent() );
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
				ComponentTreeUtils.getTreeNodeInterval( hyp.getWrappedComponent() );
		final int startpos = interval.getA();
		final int endpos = interval.getB();

		final List< Hypothesis< Component< FloatType, ? >>> hyps = getOptimalHypotheses( t );
		for ( final Hypothesis< Component< FloatType, ? >> h : hyps ) {
			final ValuePair< Integer, Integer > ctnLimits =
					ComponentTreeUtils.getTreeNodeInterval( h.getWrappedComponent() );
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
					ComponentTreeUtils.getTreeNodeInterval( h.getWrappedComponent() );
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
				System.err.println( "ERROR: It could not be determined if a certain assignment was chosen during the convex optimization! Hint: Maybe the ILP is infeasible and was therefore not solved?" );
				e.printStackTrace();
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
	public AbstractAssignment< Hypothesis< Component< FloatType, ? > > > getOptimalLeftAssignment( final Hypothesis< Component< FloatType, ? > > hypothesis ) throws GRBException {
		return findActiveAssignment( edgeSets.getLeftNeighborhood( hypothesis ) );
	}

	/**
	 * Get optimal assignments from timestep t to t+1
	 *
	 * @param t time step
	 * @return set of optimal assignments
	 */
	public Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> getOptimalAssignments(final int t) {
		return getOptimalAssignments(nodes.getAssignmentsAt(t));
	}

	/**
	 * Get all optimal assignments from an Iterable of {@link AbstractAssignment}
	 *
	 * @param assignments iterable of assignments
	 * @return set of optimal assignments
	 */
	public Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> getOptimalAssignments(Iterable<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> assignments) {
		HashSet<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> activeAssignments = new HashSet<>();
		try {
			for (AbstractAssignment<Hypothesis<Component<FloatType, ?>>> assignment : assignments) {
				if (assignment.isChoosen()) activeAssignments.add(assignment);
			}
		} catch (GRBException e) {
			e.printStackTrace();
		}
		return activeAssignments;
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
		assert ( t < nodes.getNumberOfTimeSteps() - 1 ): String.format("Assert failed: t<nodes.getNumberOfTimeSteps()-1, because t=%d and nodes.getNumberOfTimeSteps()-1=%d", t, nodes.getNumberOfTimeSteps()-1);

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
	private AbstractAssignment< Hypothesis< Component< FloatType, ? > > > findActiveAssignment( final Iterable< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > set ) throws GRBException {
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
	public HashMap< Hypothesis< Component< FloatType, ? > >, Set< AbstractAssignment< Hypothesis< Component< FloatType, ? > > > > > getAllRightAssignmentsThatStartFromOptimalHypothesesAt(final int t ) {
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
	 * Returns only the active assignments in this the data.
	 *
	 * @param data data to filter and keep only the active assignments
	 * @return
	 */
	public static HashMap<Hypothesis<Component<FloatType, ?>>, Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>>> getActiveAssignments(final HashMap<Hypothesis<Component<FloatType, ?>>, Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>>> data) {
		HashMap<Hypothesis<Component<FloatType, ?>>, Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>>> activeData = new HashMap<>();
		if (data != null) {
			for (final Hypothesis<Component<FloatType, ?>> hypo : data.keySet()) {
				final Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> activeSet = new HashSet<>();
				for (final AbstractAssignment<Hypothesis<Component<FloatType, ?>>> ass : data.get(hypo)) {
					try {
						if (ass.isChoosen() || ass.isGroundTruth()) {
							activeSet.add(ass);
						}
					} catch (final GRBException e) {
						e.printStackTrace();
					}
					activeData.put(hypo, activeSet);
				}
			}
		}
		return activeData;
	}

	/**
	 * Returns the assignments in {@param data}, which fulfill the condition defined in {@param predicate}.
	 *
	 * @param data data from which to get the assignments of correct type
	 * @param predicate predicate that the assignment must fulfill in order to be returned
	 * @return correct assignment types or null
	 */
	public static HashMap<Hypothesis<Component<FloatType, ?>>, Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>>> filterAssignmentsWithPredicate(final HashMap<Hypothesis<Component<FloatType, ?>>, Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>>> data, Function<AbstractAssignment, Boolean> predicate) {
		HashMap<Hypothesis<Component<FloatType, ?>>, Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>>> activeData = new HashMap<>();
		if (data != null) {
			for (final Hypothesis<Component<FloatType, ?>> hypo : data.keySet()) {
				final Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> activeSet = new HashSet<>();
				for (final AbstractAssignment<Hypothesis<Component<FloatType, ?>>> ass : data.get(hypo)) {
					if (predicate.apply(ass)) {
						activeSet.add(ass);
					}
					activeData.put(hypo, activeSet);
				}
			}
		}
		return activeData;
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
	 * Returns the hypothesis at a given position {@param testPosition}.
	 * If there are more then one hypothesis at given location only the lowest
	 * in the hypotheses tree will be returned.
	 * (This is also the "shortest" one!)
	 *
	 * @param t
	 * @param testPosition
	 * @return
	 */
	public Hypothesis<Component<FloatType, ?>> getLowestInTreeHypAt(final int t, final Point testPosition) {
		Hypothesis<Component<FloatType, ?>> ret = null;
		long min = Long.MAX_VALUE;
		final List<Hypothesis<Component<FloatType, ?>>> hyps = nodes.getHypothesesAt(t);
		for (final Hypothesis<Component<FloatType, ?>> hyp : hyps) {
			final Component<FloatType, ?> comp = hyp.getWrappedComponent();
			int componentSize = ComponentTreeUtils.getComponentSize(comp, 1);
			if (ComponentTreeUtils.componentContainsYPosition(comp, testPosition)) {
				if (componentSize < min) {
					min = componentSize;
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
	 * the second parameter can be the hypotheses at the same location for which
	 * such a constraint exists so far.
	 *
	 * @param hyp2add
	 *            the hypothesis for which the constraint should be installed.
	 * @throws GRBException
	 */
	public void addSegmentInSolutionConstraint( final Hypothesis< Component< FloatType, ? >> hyp2add, final List< Hypothesis< Component< FloatType, ? >>> hypothesesToRemove ) throws GRBException {
		final GRBLinExpr expr = new GRBLinExpr();

		// Remove constraints form all given hypotheses
		if ( hypothesesToRemove != null ) {
			removeSegmentConstraints(hypothesesToRemove);
		}

		final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > rightNeighbors = edgeSets.getRightNeighborhood( hyp2add );
		for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> assmnt : rightNeighbors ) {
			expr.addTerm( 1.0, assmnt.getGRBVar() );
		}

		// Store the newly created constraint in hyp2add
		hyp2add.setSegmentSpecificConstraint( model.addConstr( expr, GRB.EQUAL, 1.0, "sisc_" + hyp2add.hashCode() ) );
	}

	/**
	 * Remove constraints for the hypotheses in the provided list.
	 *
	 * @param hypothesesToRemove
	 */
	private void removeSegmentConstraints(List<Hypothesis<Component<FloatType, ?>>> hypothesesToRemove) {
		for (final Hypothesis<Component<FloatType, ?>> hyp2remove : hypothesesToRemove) {
			removeSegmentConstraints(hyp2remove);
		}
	}

	/**
	 * Remove constraints for the provided hypothesis.
	 *
	 * @param hypothesisToRemove
	 */
	private void removeSegmentConstraints(Hypothesis<Component<FloatType, ?>> hypothesisToRemove) {
		final GRBConstr oldConstr = hypothesisToRemove.getSegmentSpecificConstraint();
		if (oldConstr != null) {
			try {
				model.remove(oldConstr);
				hypothesisToRemove.setSegmentSpecificConstraint(null);
			} catch (final GRBException e) {
				e.printStackTrace();
			}
		}
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
	public synchronized void fixSegmentationAsIs( final int t ) {
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
	public synchronized void fixAssignmentsAsAre( final int t ) {
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
	public synchronized void removeAllSegmentConstraints( final int t ) {
		final List< Hypothesis< Component< FloatType, ? >>> hyps =
				nodes.getHypothesesAt( t );
		removeSegmentConstraints(hyps);
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



}
