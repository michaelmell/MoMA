/**
 *
 */
package com.jug.export;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.jug.lp.DivisionAssignment;
import com.jug.lp.GrowthLineTrackingILP;
import com.jug.lp.Hypothesis;
import com.jug.lp.MappingAssignment;

import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.type.numeric.real.FloatType;

/**
 * @author jug
 */
public class FactorGraphFileBuilder_PAUL {

	private int next_t = 0;
	private int next_hyp_id = -1; //will be set to 0 in markNextTimepoint()

	private final List< String > lines = new ArrayList<>();

	private final HashMap< Hypothesis< Component< FloatType, ? > >, Integer > mapHypId;

	public FactorGraphFileBuilder_PAUL() {
		mapHypId = new HashMap<>();
		lines.add( "# EXPORTED MM-TRACKING (jug@mpi-cbg.de)\n" );
		lines.add( "# objective_value = NOT_COMPUTED" );
		lines.add( "# SEGMENTS" );
		lines.add( "# Note: ids must be given such that hypotheses are ordered from top to bottom" );
		lines.add( "# (in order to implicitly know about exit constraints)\n" );
	}

	public FactorGraphFileBuilder_PAUL( final double optimal_energy ) {
		mapHypId = new HashMap<>();
		lines.add( "# EXPORTED MM-TRACKING (jug@mpi-cbg.de)\n" );
		lines.add( "# objective_value = " + optimal_energy );
		lines.add( "# SEGMENTS" );
		lines.add( "# Note: ids must be given such that hypotheses are ordered from top to bottom" );
		lines.add( "# (in order to implicitly know about exit constraints)\n" );
	}

	/**
	 * writes a time-point tag into <code>lines</code>.
	 */
	public void markNextTimepoint() {
		if ( next_hyp_id == -1 ) {
			lines.add( "# #### SEGMENTS (HYPOTHESES) ###################################" );
		}

		lines.add( "\n# t=" + next_t + "\n" );
		next_t++;
		next_hyp_id = 0;
	}

	/**
	 * Adds an exclusion constraint (of hyps that cannot be turned on
	 * simultaneously)
	 *
	 * @param hyps
	 */
	public void addPathBlockingConstraint( final List< Hypothesis< Component< FloatType, ? > > > hyps ) {
		String str = "CONFSET ";// + hyps.get( 0 ).getTime();
		boolean first = true;
		for ( final Hypothesis< Component< FloatType, ? > > hyp : hyps ) {
			if ( first ) {
				first = false;
			} else {
				str += " + ";
			}
			str += String.format( "%d %d", hyp.getTime(), mapHypId.get( hyp ) );
		}
		str += " <= 1";
		lines.add( str );
	}

	/**
	 * @param file
	 */
	public void write( final File file ) {
		BufferedWriter out;
		try {
			out = new BufferedWriter( new FileWriter( file ) );

			for ( final String line : lines ) {
				out.write( line );
				out.newLine();
			}
			out.close();
		}
		catch ( final IOException e ) {
			e.printStackTrace();
		}
	}

	/**
	 * @param line
	 */
	public void addLine( final String line ) {
		lines.add( line );
	}

	/**
	 * Adds a hypotheses.
	 *
	 * @param hyp
	 *
	 * @return the id of the added hypothesis.
	 */
	public int addHyp( final GrowthLineTrackingILP ilp, final Hypothesis< Component< FloatType, ? > > hyp ) {
		mapHypId.put( hyp, next_hyp_id );
		final double exitCost = ilp.costModulationForSubstitutedILP( hyp.getCosts() );
		lines.add( String.format( "H %d %d %.16f (%d,%d)", next_hyp_id, hyp.getId(), 0f, hyp.getLocation().a, hyp.getLocation().b ) );
																			// the hypcosts are all 0 because we fold them into
																			// the assignments according to the way we substitute
																			// the corresponding variable for the ILP anyways.
		lines.add( String.format( "APP    %.16f", 0f ) );
		lines.add( String.format( "DISAPP %.16f", exitCost ) );
		next_hyp_id++;
		return next_hyp_id - 1;
	}

	/**
	 * @param ilp
	 * @param t
	 * @param assmnt
	 */
	public void addMapping( final GrowthLineTrackingILP ilp, final int t, final MappingAssignment assmnt ) {
		final Hypothesis< Component< FloatType, ? > > sourceHypothesis = assmnt.getSourceHypothesis();
		final Hypothesis< Component< FloatType, ? > > destinationHypothesis = assmnt.getDestinationHypothesis();
		final float mappingCost = ilp.compatibilityCostOfMapping( sourceHypothesis, destinationHypothesis ).getA();
		final double cost = ilp.costModulationForSubstitutedILP( sourceHypothesis.getCosts(), destinationHypothesis.getCosts(), mappingCost );
		if ( cost <= GrowthLineTrackingILP.CUTOFF_COST ) {
			lines.add(
					String.format(
							"MOVE %d %d %d %d %.16f",
							t,
							mapHypId.get( sourceHypothesis ),
							t + 1,
							mapHypId.get( destinationHypothesis ),
							cost ) );
		}
	}

	/**
	 * @param ilp
	 * @param t
	 * @param assmnt
	 */
	public void addDivision( final GrowthLineTrackingILP ilp, final int t, final DivisionAssignment assmnt ) {
		final Hypothesis< Component< FloatType, ? > > sourceHypothesis = assmnt.getSourceHypothesis();
		final Hypothesis< Component< FloatType, ? > > destinationHypothesisUpper = assmnt.getUpperDesinationHypothesis();
		final Hypothesis< Component< FloatType, ? > > destinationHypothesisLower = assmnt.getLowerDesinationHypothesis();
		final float divisionCost = ilp.compatibilityCostOfDivision( sourceHypothesis, destinationHypothesisUpper, destinationHypothesisLower ).getA();
		final double cost = ilp.costModulationForSubstitutedILP(
				sourceHypothesis.getCosts(),
				destinationHypothesisUpper.getCosts(),
				destinationHypothesisLower.getCosts(),
				divisionCost );
		if ( cost <= GrowthLineTrackingILP.CUTOFF_COST ) {
			lines.add(
				String.format(
						"DIV %d %d %d %d %d %d %.16f",
						t,
						mapHypId.get( sourceHypothesis ),
						t + 1,
						mapHypId.get( destinationHypothesisUpper ),
						t + 1,
						mapHypId.get( destinationHypothesisLower ),
						cost ) );
		}
	}
}
