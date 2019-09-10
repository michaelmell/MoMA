package com.jug.ops.numerictype;

import net.imagej.ops.Op;
import net.imagej.ops.special.hybrid.AbstractUnaryHybridCF;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.Views;

import org.scijava.plugin.Plugin;

/**
 * @author jug
 *
 */
@Plugin(type = Op.class)
public class SumOfRai<T extends NumericType< T >> 
extends AbstractUnaryHybridCF<RandomAccessibleInterval<T>, T> {

	@Override
	public void compute(final RandomAccessibleInterval<T> input, final T output) {
		output.setZero();
		for (T el : Views.iterable(input)) {
		    output.add(el);
		}		
	}


	@Override
	public T createOutput(RandomAccessibleInterval<T> input) {
		return input.randomAccess().get().createVariable();
	}

}
