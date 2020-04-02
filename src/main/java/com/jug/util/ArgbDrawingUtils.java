package com.jug.util;

import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;

import java.util.Iterator;
import java.util.function.Function;

/**
 * @author jug
 */
public class ArgbDrawingUtils {

	/**
	 * @param ctn
	 * @param offsetX
	 * @param offsetY
	 */
	public static void taintOptimalComponentTreeNode(final Component< FloatType, ? > ctn, final RandomAccess< ARGBType > raArgbImg, final long offsetX, final long offsetY ) {
		assert ( ctn.iterator().hasNext() );
		Function<Integer, ARGBType> greenPixelOverlayCalculator = grayscaleValue -> calculateGreenPixelOverlayValue(grayscaleValue);
		drawSegmentColorOverlay( ctn, raArgbImg, offsetX, offsetY, greenPixelOverlayCalculator);
	}

	/**
	 * @param ctn
	 * @param offsetX
	 * @param offsetY
	 */
	public static void taintForcedComponentTreeNode( final Component< FloatType, ? > ctn, final RandomAccess< ARGBType > raArgbImg, final long offsetX, final long offsetY ) {
		assert ( ctn.iterator().hasNext() );
		Function<Integer, ARGBType> yellowPixelOverlayCalculator = grayscaleValue -> calculateYellowPixelOverlayValue(grayscaleValue);
		drawSegmentColorOverlay( ctn, raArgbImg, offsetX, offsetY, yellowPixelOverlayCalculator);
	}

	/**
	 * @param ctn
	 * @param offsetX
	 * @param offsetY
	 */
	public static void taintPrunedComponentTreeNode(final Component<FloatType, ?> ctn, final RandomAccess<ARGBType> raArgbImg, final long offsetX, final long offsetY) {
		assert ( ctn.iterator().hasNext() );
		Function<Integer,ARGBType> grayPixelOverlayCalculator = grayscaleValue -> calculateGrayPixelOverlayValue(grayscaleValue);
		drawSegmentColorOverlay( ctn, raArgbImg, offsetX, offsetY, grayPixelOverlayCalculator);
	}

	/**
	 * @param ctn
	 * @param offsetX
	 * @param offsetY
	 */
	public static void taintInactiveComponentTreeNode( final Component< FloatType, ? > ctn, final RandomAccess< ARGBType > raArgbImg, final long offsetX, final long offsetY ) {
		assert ( ctn.iterator().hasNext() );
		Function<Integer, ARGBType> redPixelOverlayCalculator = grayscaleValue -> calculateRedPixelOverlayValue(grayscaleValue);
		drawSegmentColorOverlay( ctn, raArgbImg, offsetX, offsetY, redPixelOverlayCalculator );
	}

	/**
	 * Draw {@param component} to gray-scale {@param ArgbImage} offsetting its
	 * position by {@param offsetX} and {@param offsetX}. The color of the
	 * component pixels in the image is calculated using
	 * the {@param pixelColorCalculator}.
	 *
	 * @param component component to draw to image
	 * @param ArgbImage image to draw to
	 * @param offsetX x-offset
	 * @param offsetY y-offset
	 * @param pixelColorCalculator lambda function to calculate the ARGB value
	 *                             of each component pixel based on the its previous
	 *                             grayscale value
	 */
	@SuppressWarnings( "unchecked" )
	private static void drawSegmentColorOverlay(final Component< FloatType, ? > component, final RandomAccess< ARGBType > ArgbImage, final long offsetX, final long offsetY, Function<Integer, ARGBType> pixelColorCalculator ) {
		Iterator< Localizable > componentIterator = component.iterator();
		while (componentIterator.hasNext()) {
			Localizable position = componentIterator.next();
			final int xpos = position.getIntPosition(0);
			final int ypos = position.getIntPosition(1);
			final Point p = new Point(xpos - offsetX, offsetY + ypos);
			final long[] imgPos = Util.pointLocation(p);
			ArgbImage.setPosition(imgPos);
			final int curCol = ArgbImage.get().get();
			ArgbImage.get().set(pixelColorCalculator.apply(curCol));
		}
	}

	/**
	 * Calculate green ARGB pixel value from current {@param grayscaleValue}.
	 *
	 * @param grayscaleValue current grayscale value.
	 * @return ARBG pixel value.
	 */
	private static ARGBType calculateGreenPixelOverlayValue(int grayscaleValue){
		final int redToUse = (int) (Math.min(10, (255 - ARGBType.red(grayscaleValue))) / 1.25);
		final int greenToUse = Math.min(35, (255 - ARGBType.green(grayscaleValue)));
		final int blueToUse = (int) (Math.min(10, (255 - ARGBType.blue(grayscaleValue))) / 1.25);
		return new ARGBType(ARGBType.rgba(ARGBType.red(grayscaleValue) + (redToUse), ARGBType.green(grayscaleValue) + (greenToUse), ARGBType.blue(grayscaleValue) + (blueToUse), ARGBType.alpha(grayscaleValue)));
	}

	/**
	 * Calculate red ARGB pixel value from current {@param grayscaleValue}.
	 *
	 * @param grayscaleValue current grayscale value.
	 * @return ARBG pixel value.
	 */
	private static ARGBType calculateRedPixelOverlayValue(int grayscaleValue){
		final int redToUse = Math.min(100, (255 - ARGBType.red(grayscaleValue)));
		final int greenToUse = Math.min( 10, ( 255 - ARGBType.green( grayscaleValue ) ) ) / 4;
		final int blueToUse = Math.min( 10, ( 255 - ARGBType.blue( grayscaleValue ) ) ) / 4;
		return new ARGBType(ARGBType.rgba(ARGBType.red(grayscaleValue) + (redToUse), ARGBType.green(grayscaleValue) + (greenToUse), ARGBType.blue(grayscaleValue) + (blueToUse), ARGBType.alpha(grayscaleValue)));
	}

	/**
	 * Calculate yellow ARGB pixel value from current {@param grayscaleValue}.
	 *
	 * @param grayscaleValue current grayscale value.
	 * @return ARBG pixel value.
	 */
	private static ARGBType calculateYellowPixelOverlayValue(int grayscaleValue){
		final int redToUse = Math.min(100, (255 - ARGBType.red(grayscaleValue)));
		final int greenToUse = ( int ) ( Math.min( 75, ( 255 - ARGBType.green( grayscaleValue ) ) ) / 1.25 );
		final int blueToUse = Math.min( 10, ( 255 - ARGBType.blue( grayscaleValue ) ) ) / 4;
		return new ARGBType(ARGBType.rgba(ARGBType.red(grayscaleValue) + (redToUse), ARGBType.green(grayscaleValue) + (greenToUse), ARGBType.blue(grayscaleValue) + (blueToUse), ARGBType.alpha(grayscaleValue)));
	}

	/**
	 * Calculate gray ARGB pixel value from current {@param grayscaleValue}.
	 *
	 * @param grayscaleValue current grayscale value.
	 * @return ARBG pixel value.
	 */
	private static ARGBType calculateGrayPixelOverlayValue(int grayscaleValue){
		int minHelper = 100;
		int bgHelper = 175;
		final int redToUse = ( Math.min( minHelper, ( bgHelper - ARGBType.red( grayscaleValue ) ) ) );
		final int greenToUse = ( Math.min( minHelper, ( bgHelper - ARGBType.green( grayscaleValue ) ) ) );
		final int blueToUse = ( Math.min( minHelper, ( bgHelper - ARGBType.blue( grayscaleValue ) ) ) );
		return new ARGBType(ARGBType.rgba(ARGBType.red(grayscaleValue) + (redToUse), ARGBType.green(grayscaleValue) + (greenToUse), ARGBType.blue(grayscaleValue) + (blueToUse), ARGBType.alpha(grayscaleValue)));
	}

}
