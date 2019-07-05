/**
 *
 */
package com.jug.lp.costs;

import java.util.List;

import com.jug.MoMA;
import com.jug.lp.Hypothesis;
import com.jug.util.ComponentTreeUtils;
import com.jug.util.SimpleFunctionAnalysis;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

/**
 * @author jug
 */
public class CostFactory {

//	public static String latestCostEvaluation = "";

	public static Pair< Float, float[] > getMigrationCost( final float oldPosition, final float newPosition, final float normalizer ) {
		float deltaH = ( oldPosition - newPosition ) / normalizer;
		float power = 0.0f;
		float costDeltaH = 0.0f;
		if ( deltaH > 0 ) { // upward migration
			deltaH = Math.max( 0, deltaH - 0.05f ); // going upwards for up to 5% is for free...
			power = 3.0f;
		} else { // downward migration
			Math.max( 0, deltaH - 0.01f );  // going downwards for up to 1% is for free...
			power = 6.0f;
		}
		deltaH = Math.abs( deltaH );
		costDeltaH = deltaH * ( float ) Math.pow( 1 + deltaH, power );
//		latestCostEvaluation = String.format( "c_h = %.4f * %.4f^%.1f = %.4f", deltaH, 1 + deltaH, power, costDeltaH );
		return new ValuePair< Float, float[] >( costDeltaH, new float[] { costDeltaH } );
	}

	public static Pair< Float, float[] > getGrowthCost( final float oldSize, final float newSize, final float normalizer ) {
		float deltaL = ( newSize - oldSize ) / normalizer;
		float power = 0.0f;
		float costDeltaL = 0.0f;
		if ( deltaL > 0 ) { // growth
			deltaL = Math.max( 0, deltaL - 0.05f ); // growing up 5% is free
			power = 4.0f;
		} else { // shrinkage
			power = 40.0f;
//			costDeltaL += ( newSize - oldSize ) * 0.00;
		}
		deltaL = Math.abs( deltaL );
		costDeltaL += deltaL * ( float ) Math.pow( 1 + deltaL, power );
//		latestCostEvaluation = String.format( "c_l = %.4f * %.4f^%.1f = %.4f", deltaL, 1 + deltaL, power, costDeltaL );
		return new ValuePair< Float, float[] >( costDeltaL, new float[] { costDeltaL } );
	}

	public static float getIntensityMismatchCost( final float oldIntensity, final float newIntensity ) {
//		latestCostEvaluation = String.format( "c_v = 0.0" );
		return 0f;
//		final float deltaV = Math.max( 0.0f, newIntensity - oldIntensity ); // nur heller werden wird bestraft!
//		final float power = 1.0f;
//		final float freeUntil = 0.1f;
//		float costDeltaV = 0.0f;
//		if ( deltaV > freeUntil ) { // significant jump
//			costDeltaV = deltaV * ( float ) Math.pow( 1.0 + ( deltaV - freeUntil ), power );
//		}
//		latestCostEvaluation = String.format( "c_v = %.4f * %.4f^%.1f = %.4f", deltaV, 1 + deltaV, power, costDeltaV );
//		return costDeltaV;
	}

	public static float getUnevenDivisionCost( final float sizeFirstChild, final float sizeSecondChild ) {
		final float deltaS = Math.abs( sizeFirstChild - sizeSecondChild ) / Math.min( sizeFirstChild, sizeSecondChild );
		float power = 2.0f;
		float costDeltaL = 0.0f;
		if ( deltaS > 1.15 ) {
			power = 7.0f;
		}
		costDeltaL = ( float ) Math.pow( deltaS, power );

//		latestCostEvaluation = String.format( "c_d = %.4f^%.1f = %.4f", deltaS, power, costDeltaL );
		return costDeltaL;
	}

	/**
	 * @param ctNode
	 * @param gapSepFkt
	 * @return
	 */
	public static float getIntensitySegmentationCost( final Component< ?, ? > ctNode, final RandomAccessibleInterval<FloatType> imageProbabilities ) {
        final float minPixelProbability =
                ComponentTreeUtils.getTreeNodeMinIntensity( ctNode, imageProbabilities );

		float cost = - 2.0f * (float) Math.pow( minPixelProbability, 2.0f ); // take minimum probability to the power of 2

//		float cost;
//        if(minPixelProbability>0.2){
//        	cost = 100;
//		}
//        else{
//			cost = - 2.0f * (float) Math.pow( minPixelProbability, 2.0f ); // take minimum probability to the power of 2
//		}

        final ValuePair< Integer, Integer > segInterval =
				ComponentTreeUtils.getTreeNodeInterval( ctNode );
		final int a = segInterval.getA().intValue();
		final int b = segInterval.getB().intValue();

		// cell is too small
		if ( b - a < MoMA.MIN_CELL_LENGTH ) { // if a==0 or b==gapSepFkt.len, only a part of the cell is seen!
			cost = 100;
		}
//		return cost * 2f;
        return cost;
    }

	/**
	 * @param ctNode
	 * @param gapSepFkt
	 * @return
	 */
	public static float getParamaxflowSegmentationCost( final Component< ?, ? > ctNode, final float[] gapSepFkt ) {
		final ValuePair< Integer, Integer > segInterval =
				ComponentTreeUtils.getTreeNodeInterval( ctNode );
		final int a = segInterval.getA().intValue();
		final int b = segInterval.getB().intValue();

		final float plateauDerivativeThreshold = 0.0000f; //some epsilon
		int aReduced = SimpleFunctionAnalysis.getRighthandLocalMinOrPlateau( gapSepFkt, a, plateauDerivativeThreshold ).a.intValue();
		int bReduced = SimpleFunctionAnalysis.getLefthandLocalMinOrPlateau( gapSepFkt, b, plateauDerivativeThreshold ).a.intValue();
		if ( aReduced > bReduced ) {
			aReduced = bReduced = SimpleFunctionAnalysis.getMin( gapSepFkt, a, b ).a.intValue();
		}

		final float avgBottomVal = SimpleFunctionAnalysis.getAvg( gapSepFkt, a, b );
		float maxReduced = SimpleFunctionAnalysis.getMax( gapSepFkt, aReduced, bReduced ).b.floatValue();
		maxReduced = Math.max( maxReduced, avgBottomVal ); // tricky but I like it!

		final float segmentLengthInPercentGL = ( b - a ) / ( ( float ) gapSepFkt.length );

		// Special case: min-value is above average gap-sep-fkt value (happens often at the very top)
		final float avgFktValue = SimpleFunctionAnalysis.getAvg( gapSepFkt );
		final float distAboveAvg = Math.max( 0.0f, avgBottomVal - avgFktValue );
		final float penaltyHeight = distAboveAvg * ( float ) Math.pow( 1.0 + distAboveAvg, 5 );
		final float incentiveHeight = ( 1.0f - maxReduced );

		float cost = ( penaltyHeight * segmentLengthInPercentGL ) - ( incentiveHeight * segmentLengthInPercentGL );

		// cell is too small
		if ( a > 0 && b + 1 < gapSepFkt.length && b - a < MoMA.MIN_CELL_LENGTH ) { // if a==0 or b==gapSepFkt.len, only a part of the cell is seen!
			cost = 100;
		}
		return cost;
	}

	/**
	 * @param from
	 * @return
	 */
	public static float getDivisionLikelihoodCost( final Hypothesis< Component< FloatType, ? >> from ) {
		if ( from.getWrappedHypothesis().getChildren().size() > 2 ) { return 1.5f; }
		if ( from.getWrappedHypothesis().getChildren().size() <= 1 ) { return 1.5f; }

		// if two children, eveluate likelihood of being pre-division
		final List< Component< FloatType, ? > > children = ( List< Component< FloatType, ? >> ) from.getWrappedHypothesis().getChildren();
//		final float valA = children.get( 0 ).value().get();
//		final float valB = children.get( 1 ).value().get();
		final long sizeA = children.get( 0 ).size();
		final long sizeB = children.get( 1 ).size();

//		final float valParent = from.getWrappedHypothesis().value().get();
		final long sizeParent = from.getWrappedHypothesis().size();

		final long deltaSizeAtoB = Math.abs( sizeA - sizeB ) / Math.min( sizeA, sizeB ); // in multiples of smaller one
		final long deltaSizeABtoP = Math.abs( sizeA + sizeB - sizeParent ) / ( sizeA + sizeB ); // in multiples of A+B

		return 0.1f * deltaSizeAtoB + 0.1f * deltaSizeABtoP;
	}
}
