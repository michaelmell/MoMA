/**
 *
 */
package com.jug.ops.cursor;

import java.util.ArrayList;
import java.util.List;

import org.scijava.plugin.Plugin;

import net.imagej.ops.Op;
import net.imagej.ops.special.hybrid.AbstractUnaryHybridCF;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.type.Type;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * @author jug
 *
 */

@Plugin(type = Op.class)
public class FindLocalMaxima<IMG_T extends Type< IMG_T > & Comparable< IMG_T >>
extends AbstractUnaryHybridCF<RandomAccessibleInterval<IMG_T>, List<Point>> {


	@Override
	public void compute(final RandomAccessibleInterval<IMG_T> input, final List<Point> output) {

		// Credits to Example 4b @ http://imglib2.net/ (Mr. Preibisch and Mr. Pietzsch)
		// ----------------------------------------------------------------------------
		// define an interval that is one pixel smaller on each side in each dimension,
		// so that the search in the 8-neighborhood (3x3x3...x3) never goes outside
		// of the defined interval
		final Interval interval = Intervals.expand( input, -1 );

		// create a view on the source with this interval
		final IntervalView< IMG_T > viewInput = Views.interval( input, interval );

		// create a Cursor that iterates over the source and checks in a 8-neighborhood
		// if it is a local maximum
		final Cursor< IMG_T > center = Views.iterable( viewInput ).cursor();

		// instantiate a RectangleShape to access rectangular local neighborhoods
		// of radius 1 (that is 3x3x...x3 neighborhoods), skipping the center pixel
		// (this corresponds to an 8-neighborhood in 2d or 26-neighborhood in 3d, etc.)
		final RectangleShape shape = new RectangleShape( 1, true );

		// iterate over the set of neighborhoods in the image
		for ( final Neighborhood< IMG_T > localNeighborhood : shape.neighborhoods( viewInput ) ) {
			// what is the value that we investigate?
			// (the center cursor runs over the image in the same iteration order as neighborhood)
			final IMG_T centerValue = center.next();

			// keep this boolean true as long as no other value in the local neighborhood
			// is larger or equal
			boolean isMaximum = true;

			// check if all pixels in the local neighborhood that are smaller
			for ( final IMG_T value : localNeighborhood ) {
				// test if the center is greater than the current pixel value
				if ( centerValue.compareTo( value ) <= 0 ) {
					isMaximum = false;
					break;
				}
			}

			if ( isMaximum ) {
				output.add( new Point( center ) );
			}
		}

	}


	@Override
	public List<Point> createOutput(final RandomAccessibleInterval<IMG_T> input) {
		return new ArrayList<>();
	}

}
