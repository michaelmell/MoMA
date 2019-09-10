/**
 *
 */
package com.jug;

import java.util.*;

import com.jug.lp.AbstractAssignment;
import com.jug.lp.DivisionAssignment;
import com.jug.lp.ExitAssignment;
import com.jug.lp.GrowthLineTrackingILP;
import com.jug.lp.Hypothesis;
import com.jug.lp.MappingAssignment;
import com.jug.util.ArgbDrawingUtils;
import com.jug.util.SimpleFunctionAnalysis;
import com.jug.util.Util;

import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.algorithm.componenttree.ComponentForest;
import net.imglib2.img.Img;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * @author jug
 *         Represents one growth line (well) in which Bacteria can grow, at one
 *         instance in time.
 *         This corresponds to one growth line micrograph. The class
 *         representing an entire time
 *         series (2d+t) representation of an growth line is
 *         <code>GrowthLine</code>.
 */
public abstract class AbstractGrowthLineFrame< C extends Component< FloatType, C > > {

	// -------------------------------------------------------------------------------------
	// private fields
	// -------------------------------------------------------------------------------------
	/**
	 * Points at all the detected GrowthLine centers associated with this
	 * GrowthLine.
	 */
	private final List< Point > imgLocations;
	private float[] simpleSepValues; // lazy evaluation -- gets computed when
	// getAwesomeGapSeparationValues is called...
	private GrowthLine parent;
	private ComponentForest< C > componentTree;

	// -------------------------------------------------------------------------------------
	// setters and getters
	// -------------------------------------------------------------------------------------

	/**
	 * @return the location
	 */
	private List< Point > getMirroredImgLocations() {
		return flipAtCenter( imgLocations );
	}

	/**
	 * @return the growth line time series this one growth line is part of.
	 */
	public GrowthLine getParent() {
		return parent;
	}

	/**
	 * @param parent
	 *            - the growth line time series this one growth line is part of.
	 */
	public void setParent( final GrowthLine parent ) {
		this.parent = parent;
	}

	/**
	 * @return the componentTree
	 */
	public ComponentForest< C > getComponentTree() { // MM-2019-06-10: This should probably be called getComponentForest?!
		return componentTree;
	}

	/**
	 * @return the x-offset of the GrowthLineFrame given the original micrograph
	 */
	public long getOffsetX() {
		return getAvgXpos();
//		return getPoint( 0 ).getLongPosition( 0 );
	}

	/**
	 * @return the y-offset of the GrowthLineFrame given the original micrograph
	 */
	public long getOffsetY() {
		return 0;
	}

	/**
	 * @return the f-offset of the GrowthLineFrame given the original micrograph
	 *         (stack)
	 */
	public long getOffsetF() {
		return parent.getFrames().indexOf( this );
	}

	// -------------------------------------------------------------------------------------
	// constructors
	// -------------------------------------------------------------------------------------
	AbstractGrowthLineFrame() {
		imgLocations = new ArrayList<>();
	}

	// -------------------------------------------------------------------------------------
	// methods
	// -------------------------------------------------------------------------------------
	/**
	 * @return the number of points (the length) of this GrowthLine
	 */
	public int size() {
		return imgLocations.size();
	}

//	/**
//	 * Reads out all image intensities along the GrowthLine center (green
//	 * pixels).
//	 *
//	 * @param img
//	 *            - an Img.
//	 * @return a float array containing the image intensities in
//	 *         <code>img</code> at the points given in wellPoints.
//	 */
//	private float[] getInvertedIntensitiesAtImgLocations( final RandomAccessibleInterval< FloatType > img, final boolean imgIsPreCropped ) {
//		final float[] ret = new float[ imgLocations.size() ];
//
//		//here now a trick to make <3d images also comply to the code below
//		IntervalView< FloatType > ivImg = Views.interval( img, img );
//		for ( int i = 0; i < 3 - img.numDimensions(); i++ ) {
//			ivImg = Views.addDimension( ivImg, 0, 0 );
//		}
//		final RandomAccess< FloatType > raImg3d = ivImg.randomAccess();
//
//		int i = 0;
//		for ( final Point p_orig : imgLocations ) {
//			final Point p = new Point( p_orig );
//			if ( imgIsPreCropped ) {
//				p.setPosition( 0, 2 );
//				p.move( MoMA.GL_PIXEL_PADDING_IN_VIEWS + MoMA.GL_WIDTH_IN_PIXELS / 2 - getAvgXpos(), 0 );
//			}
//			raImg3d.setPosition( p );
//			ret[ i++ ] = 1.0f - raImg3d.get().get();
//		}
//		return ret;
//	}

	/**
	 * Adds a detected center point to a GrowthsLineFrame.
	 *
	 * @param point
	 */
	public void addPoint( final Point point ) {
		imgLocations.add( point );
	}

	/**
	 *
	 */
	public void sortPoints() {
		imgLocations.sort(Comparator.comparingInt(o -> o.getIntPosition(1)));
	}

	/**
	 * Gets the first detected center point of a GrowthsLine.
	 */
	public Point getFirstPoint() {
		return ( imgLocations.get( 0 ) );
	}

	/**
	 * Gets the last detected center point of a GrowthsLine.
	 */
	public Point getLastPoint() {
		return ( imgLocations.get( imgLocations.size() - 1 ) );
	}

	/**
	 * Using the imglib2 component tree to find the most stable components
	 * (bacteria).
	 *
	 * @param img
	 */
	public void generateSimpleSegmentationHypotheses( final Img< FloatType > img, int frameIndex ) {
//		Img<FloatType> imgTmp = runNetwork(img);

//		uiService.show(Views.hyperSlice(imgTmp, 2, 0));
//		ops.convert().imageType(out, in, typeConverter)
		
//		Img<FloatType> imgTmpNew  = ImgFaco
//		ops.convert().imageType(out, imgTmp, typeConverter)
//		
//		ImageJFunctions.C
		
//		final float[] fkt = getSimpleGapSeparationValues( img );
		
//		final float[] fkt = getSimpleGapSeparationValues( imgTmp );

//		Plotting.plotArray(fkt)
//		Plotting.plotArray(fkt, "Line Intensity Plot", "x [px]", "intensity [a.u.]");
//		ops.wrap
//		ops.math().
		
//		RandomAccessibleInterval imgTmpScaled = ops.transform().scaleView(imgTmp, new double[] {1.0,255.0}, new NLinearInterpolatorFactory());
//		Pair<FloatType, FloatType> res = ops.stats().minMax(imgTmp);
//		System.out.println(res.getA());
//		System.out.println(res.getB());
//		IterableInterval<FloatType> tmp = ops.image().normalize(imgTmp);
//		uiService.show(imgTmp); 
//		(RandomAccessibleIntveral) imgTmpNew;
//		ops.stats().minMax(imgTmp);

//		Plotting.surfacePlot(imgTmp, 2, 5);

//		if ( fkt.length > 0 ) {
//			final RandomAccessibleInterval< FloatType > raiFkt = new ArrayImgFactory< FloatType >().create( new int[] { fkt.length }, new FloatType() );
//			final RandomAccess< FloatType > ra = raiFkt.randomAccess();
//			for ( int i = 0; i < fkt.length; i++ ) {
//				ra.setPosition( i, 0 );
//				ra.get().set( fkt[ i ] );
//			}
//			isParaMaxFlowComponentTree = false;
//			componentTree = buildIntensityTree( raiFkt );
//		}

//		componentTree = buildIntensityTree( imgTmp );

//        FinalInterval roiForComponentGeneration = new FinalInterval(new long[]{0, MoMA.GL_OFFSET_BOTTOM}, new long[]{img.dimension(0), img.dimension(1) - MoMA.GL_OFFSET_TOP});
//        IntervalView<FloatType> intervalView = Views.interval(Views.hyperSlice(img, 2, frameIndex), roiForComponentGeneration);
//        componentTree = buildIntensityTree(intervalView);

        componentTree = buildIntensityTree( Views.hyperSlice(img, 2, frameIndex) );
//		Plotting.drawComponentTree(componentTree);

		//		FilteredComponentTree tmp2 = (FilteredComponentTree) componentTree;
//		tmp2.printPixelList(0);
//		printPixelList(tmp2.nodes[0].pixelList);
	}
	

	

	
//	public static < T extends Type< T > > void copy( final Img< T > source, final Img< T > target )
//	{
//		final RandomAccess< T > in = source.randomAccess();
//		final Cursor< T > out = target.localizingCursor();
//		while ( out.hasNext() )
//		{
//			out.fwd();
//			in.setPosition( out );
//			final T type = out.get();
//			type.set( in.get() );
//		}
//	}
	
//    public < T extends Type< T > > void copy( final RandomAccessible< T > source,
//            final IterableInterval< T > target )
//        {
//            // create a cursor that automatically localizes itself on every move
//            Cursor< T > targetCursor = target.localizingCursor();
//            RandomAccess< T > sourceRandomAccess = source.randomAccess();
//     
//            // iterate over the input cursor
//            while ( targetCursor.hasNext())
//            {
//                // move input cursor forward
//                targetCursor.fwd();
//     
//                // set the output cursor to the position of the input cursor
//                sourceRandomAccess.setPosition( targetCursor );
//     
//                // set the value of this pixel of the output image, every Type supports T.set( T type )
//                targetCursor.get().set( sourceRandomAccess.get() );
//            }
//        }
	
//	private Img<FloatType> AddOne(Img<FloatType> im){
//		ArrayImgFactory<T> factory = new ArrayImgFactory<>(Views.flatIterable(img).firstElement().copy());
//		ArrayImg<T, ?> out = factory.create(img);
//		LoopBuilder.setImages( img, out ).forEachPixel((x,y) -> y.set(x));
//		return out;
//		
//		throw new NotImplementedError();
//	}

	
	/**
	 * Using the imglib2 component tree to find the most stable components
	 * (bacteria).
	 *
	 */
//	public void generateAwesomeSegmentationHypotheses( final Img< FloatType > img ) {
//
//		final float[] fkt = getAwesomeGapSeparationValues( img );
//
//		if ( fkt.length > 0 ) {
//			final RandomAccessibleInterval< FloatType > raiFkt = new ArrayImgFactory< FloatType >().create( new int[] { fkt.length }, new FloatType() );
//			final RandomAccess< FloatType > ra = raiFkt.randomAccess();
//			for ( int i = 0; i < fkt.length; i++ ) {
//				ra.setPosition( i, 0 );
//				ra.get().set( fkt[ i ] );
//			}
//
//			isParaMaxFlowComponentTree = true;
//			componentTree = buildParaMaxFlowSumTree( raiFkt );
//		}
//	}

	protected abstract ComponentForest< C > buildIntensityTree( final RandomAccessibleInterval< FloatType > raiFkt );

//	protected abstract ComponentForest< C > buildParaMaxFlowSumTree( final RandomAccessibleInterval< FloatType > raiFkt );

	/**
	 * @param img
	 * @return
	 */
	public float[] getMirroredCenterLineValues( final Img< FloatType > img ) {
		final RandomAccess< FloatType > raImg = img.randomAccess();
		final List< Point > mirroredImgLocations = getMirroredImgLocations();
		final float[] dIntensity = new float[ mirroredImgLocations.size() ];
		for ( int i = 0; i < mirroredImgLocations.size(); i++ ) {
			raImg.setPosition( mirroredImgLocations.get( i ) );
			dIntensity[ i ] = raImg.get().get();
		}
		return dIntensity;
	}

	/**
	 * GapSep guesses based on the intensity image alone
	 *
	 * @param img
	 * @return
	 */
	public float[] getSimpleGapSeparationValues( final Img< FloatType > img ) {
		if ( simpleSepValues == null ) {
			if ( img == null ) return null;
			simpleSepValues = getMaxTiltedLineAveragesInRectangleAlongAvgCenter( img );
			simpleSepValues = avoidMotherCellSegmentationFlickering( simpleSepValues );
//			sepValues = getInvertedIntensities( img );
		}
		return simpleSepValues;
	}

	/**
	 * Bottom cell segments where often pretty bad. Why?
	 * Because the GL stops there and below come dark, dark pixels.
	 * This is a way out. (How well this does in cases where the bottom cell
	 * moves up considerably has to be seen...)
	 *
	 * @param fkt
	 * @return
	 */
	private float[] avoidMotherCellSegmentationFlickering( final float[] fkt ) {
		final int[] maximaLocations = SimpleFunctionAnalysis.getMaxima( fkt, 1, 1 );
		if ( maximaLocations.length > 0 ) {
			final int lastMaximaLoc = maximaLocations[ maximaLocations.length - 1 ];

			if ( fkt.length - lastMaximaLoc < MoMA.MOTHER_CELL_BOTTOM_TRICK_MAX_PIXELS ) {
				for ( int i = lastMaximaLoc; i < fkt.length; i++ ) {
					fkt[ i ] = Math.max( fkt[ i - 1 ] + 0.005f, fkt[ i ] );
				}
			}
		}
		return fkt;
	}

	/**
	 * GapSep guesses based on the awesome paramaxflow-sum-image...
	 *
	 * @param img
	 * @return
	 */
//	public float[] getAwesomeGapSeparationValues( final Img< FloatType > img ) {
//		if ( img == null ) return null;
//
//		// I will pray for forgiveness... in March... I promise... :(
//		IntervalView< FloatType > paramaxflowSumImageFloatTyped = getParamaxflowSumImageFloatTyped( null );
//		if ( paramaxflowSumImageFloatTyped == null ) {
//			final long left = getOffsetX() - MoMA.GL_PIXEL_PADDING_IN_VIEWS - MoMA.GL_WIDTH_IN_PIXELS / 2;
//			final long right = getOffsetX() + MoMA.GL_PIXEL_PADDING_IN_VIEWS + MoMA.GL_WIDTH_IN_PIXELS / 2 + MoMA.GL_WIDTH_IN_PIXELS % 2;
//			final long top = img.min( 1 );
//			final long bottom = img.max( 1 );
//			final IntervalView< FloatType > viewCropped = Views.interval( Views.hyperSlice( img, 2, getOffsetF() ), new long[] { left, top }, new long[] { right, bottom } );
//			paramaxflowSumImageFloatTyped = getParamaxflowSumImageFloatTyped( viewCropped );
//		}
//
//		awesomeSepValues = getInvertedIntensitiesAtImgLocations( paramaxflowSumImageFloatTyped, true );
//
//		// special case: simple value is better then trained random forest: leave some simple value in there and
//		// it might help to divide at right spots
//		if ( MoMA.SEGMENTATION_MIX_CT_INTO_PMFRF > 0.00001 ) {
//			final float percSimpleToStay = MoMA.SEGMENTATION_MIX_CT_INTO_PMFRF;
//			if ( simpleSepValues == null ) {
//				simpleSepValues = getSimpleGapSeparationValues( img );
//			}
//			for ( int i = 0; i < Math.min( simpleSepValues.length, awesomeSepValues.length ); i++ ) {
//				awesomeSepValues[ i ] = percSimpleToStay * simpleSepValues[ i ] + ( 1.0f - percSimpleToStay ) * awesomeSepValues[ i ];
//			}
//		}
//
//		return awesomeSepValues;
//	}

	/**
	 * Trying to look there a bit smarter... ;)
	 *
	 * @param img
	 * @return
	 */
	private float[] getMaxTiltedLineAveragesInRectangleAlongAvgCenter( final Img< FloatType > img ) {
		return getMaxTiltedLineAveragesInRectangleAlongAvgCenter( img, true );
	}

	/**
	 * This calculates the max intensities inside growthlane along the diagonals of a moving square subsection of the image.
	 * It does this along a single frame of {@link RandomAccessibleInterval img}, where the frame index and center-pixel of the current rectangle ROI
	 * is defined by the {@link Point} array {@link List<Point> imgLocations}.
	 *
	 * @param img: multidimensional image stack
	 * @return
	 */
	private float[] getMaxTiltedLineAveragesInRectangleAlongAvgCenter( final RandomAccessibleInterval< FloatType > img, final boolean imgIsPreCropped ) {
		// special case: growth line does not exist in this frame
		if ( imgLocations.size() == 0 ) return new float[ 0 ];

		final int maxOffsetX = 9; // half of the horizontal range of the rectangle 
		final int maxOffsetY = 9; // half of the vertical range of the rectangle

		int centerX = getAvgXpos();
		int centerZ = imgLocations.get( 0 ).getIntPosition( 2 );

		if ( imgIsPreCropped ) {
			centerX = MoMA.GL_PIXEL_PADDING_IN_VIEWS + MoMA.GL_WIDTH_IN_PIXELS / 2;
//			centerZ = 0;
		}

		//here now a trick to make <3d images also comply to the code below
		IntervalView< FloatType > ivImg = Views.interval( img, img );
		for ( int i = 0; i < 3 - img.numDimensions(); i++ ) {
			ivImg = Views.addDimension( ivImg, 0, 0 );
		}

		final RealRandomAccessible< FloatType > rrImg =
				Views.interpolate( Views.extendZero( Views.hyperSlice( ivImg, 2, centerZ ) ), new NLinearInterpolatorFactory<>() );
		final RealRandomAccess< FloatType > rraImg = rrImg.realRandomAccess();

//		FinalInterval interval = new FinalInterval(new long[] {0,0}, new long[] {31, 511});
//		ImageJFunctions.show( Views.interval(Views.raster(rrImg),interval));
		
		final float[] dIntensity = new float[ imgLocations.size() ]; //  + 1
		for ( int i = 0; i < imgLocations.size(); i++ ) {
			final int centerY = imgLocations.get( i ).getIntPosition( 1 );

			int nextAverageIdx = 0;
			final float[] diagonalAverages = new float[ maxOffsetY * 2 + 1 ];
			for ( int currentOffsetY = -maxOffsetY; currentOffsetY <= maxOffsetY; currentOffsetY++ ) {
				float summedIntensities = 0;
				int summands = 0;
				for ( int currentOffsetX = -maxOffsetX; currentOffsetX <= maxOffsetX; currentOffsetX++ ) {
					final float x = centerX + currentOffsetX;
					final float y = centerY + ( ( float ) currentOffsetY / maxOffsetX ) * currentOffsetX; // NOTE-MM-2019-05-27: ( float ) currentOffsetY * (( float )  currentOffsetX / maxOffsetX) is normalizing currentOffsetX to interval [-1,1]
					rraImg.setPosition( new float[] { x, y } );
					summedIntensities += rraImg.get().get();
					summands++;
				}
				diagonalAverages[ nextAverageIdx ] = summedIntensities / summands;
				nextAverageIdx++;
			}
			final float maxDiagonalAvg = SimpleFunctionAnalysis.getMax(diagonalAverages).b;

			// dIntensity[i] = maxDiagonalAvg - totalAverageIntensity;
			// dIntensity[i] = maxDiagonalAvg - minIntensity;
			dIntensity[ i ] = maxDiagonalAvg;
		}

//		System.out.println(Arrays.toString(dIntensity));
//		dIntensity = SimpleFunctionAnalysis.normalizeDoubleArray( dIntensity, 0.0, 1.0 );
		return dIntensity;
	}

	/**
	 * Draws the GrowthLine center line into the given annotation
	 * <code>Img</code>.
	 *
	 */
	public void drawCenterLine( final Img< ARGBType > imgAnnotated ) {
		drawCenterLine( imgAnnotated, null );
	}

	/**
	 * Draws the GrowthLine center line into the given annotation
	 * <code>Img</code>.
	 *
	 * @param img
	 *            the Img to draw into.
	 * @param view
	 *            the active view on that Img (in order to know the pixel
	 *            offsets)
	 */
	public void drawCenterLine( final Img< ARGBType > img, final IntervalView< FloatType > view ) {
		final RandomAccess< ARGBType > raAnnotationImg = img.randomAccess();

		long offsetX = 0;
		long offsetY = 0;
		if ( view != null ) {
			// Lord, forgive me!
			if ( view.min( 0 ) == 0 ) {
				// In case I give the cropped paramaxflow-baby I lost the offset and must do ugly shit...
				// I promise this is only done because I need to finish the f****** paper!
				offsetX = -( this.getAvgXpos() - MoMA.GL_WIDTH_IN_PIXELS / 2 - MoMA.GL_PIXEL_PADDING_IN_VIEWS );
				offsetY = view.min( 1 );
			} else {
				offsetX = view.min( 0 );
				offsetY = view.min( 1 );
			}
		}

		for ( final Point p : imgLocations ) { // getMirroredImgLocations()
			final long[] pos = Util.pointLocation( p );
			pos[ 0 ] += offsetX;
			pos[ 1 ] += offsetY;
			raAnnotationImg.setPosition( pos );
			raAnnotationImg.get().set( new ARGBType( ARGBType.rgba( 0, 255, 0, 255 ) ) );
		}
	}

	/**
	 * Draws the optimal segmentation (determined by the solved ILP) into the
	 * given <code>Img</code>.
	 *
	 * @param img
	 *            the Img to draw into.
	 * @param view
	 *            the active view on that Img (in order to know the pixel
	 *            offsets)
	 * @param optimalSegmentation
	 *            a <code>List</code> of the hypotheses containing
	 *            component-tree-nodes that represent the optimal segmentation
	 *            (the one returned by the solution to the ILP).
	 */
	public void drawOptimalSegmentation( final Img< ARGBType > img, final IntervalView< FloatType > view, final List< Hypothesis< Component< FloatType, ? >>> optimalSegmentation ) {
		final RandomAccess< ARGBType > raAnnotationImg = img.randomAccess();

		long offsetX = 0;
		long offsetY = 0;

		if ( view != null ) {
			// Lord, forgive me!
			if ( view.min( 0 ) == 0 ) {
				// In case I give the cropped paramaxflow-baby I lost the offset and must do ugly shit...
				// I promise this is only done because I need to finish the f****** paper!
				offsetX = -( this.getAvgXpos() - MoMA.GL_WIDTH_IN_PIXELS / 2 - MoMA.GL_PIXEL_PADDING_IN_VIEWS );
				offsetY = view.min( 1 );
			} else {
				offsetX = view.min( 0 );
				offsetY = view.min( 1 );
			}
		}

		for ( final Hypothesis< Component< FloatType, ? >> hyp : optimalSegmentation ) {
			final Component< FloatType, ? > ctn = hyp.getWrappedHypothesis();
			if ( hyp.isPruned() ) {
				ArgbDrawingUtils.taintPrunedComponentTreeNode(
						hyp.isPruneRoot(),
						ctn,
						raAnnotationImg,
						offsetX + getAvgXpos(),
						offsetY + MoMA.GL_OFFSET_TOP );
			} else if ( hyp.getSegmentSpecificConstraint() != null ) {
				ArgbDrawingUtils.taintForcedComponentTreeNode(
						ctn,
						raAnnotationImg,
						offsetX + getAvgXpos(),
						offsetY + MoMA.GL_OFFSET_TOP );
			} else {
				ArgbDrawingUtils.taintComponentTreeNode(
						ctn,
						raAnnotationImg,
						offsetX + getAvgXpos(),
						offsetY + MoMA.GL_OFFSET_TOP );
			}
		}
	}

	public void drawOptionalSegmentation( final Img< ARGBType > img, final IntervalView< FloatType > view, final Component< FloatType, ? > optionalSegmentation ) {
		final RandomAccess< ARGBType > raAnnotationImg = img.randomAccess();

		long offsetX = 0;
		long offsetY = 0;

		if ( view != null ) {
			// Lord, forgive me!
			if ( view.min( 0 ) == 0 ) {
				// In case I give the cropped paramaxflow-baby I lost the offset and must do ugly shit...
				// I promise this is only done because I need to finish the f****** paper!
				offsetX = -( this.getAvgXpos() - MoMA.GL_WIDTH_IN_PIXELS / 2 - MoMA.GL_PIXEL_PADDING_IN_VIEWS );
				offsetY = view.min( 1 );
			} else {
				offsetX = view.min( 0 );
				offsetY = view.min( 1 );
			}
		}

		ArgbDrawingUtils.taintInactiveComponentTreeNode( optionalSegmentation, raAnnotationImg, offsetX + getAvgXpos(), offsetY + MoMA.GL_OFFSET_TOP );
	}

	/**
	 * @return the average X coordinate of the center line of this
	 *         <code>GrowthLine</code>
	 */
	public int getAvgXpos() {
		int avg = 0;
		for ( final Point p : imgLocations ) {
			avg += p.getIntPosition( 0 );
		}
		if ( imgLocations.size() == 0 ) { return -1; }
		return avg / imgLocations.size();
	}

	/**
	 * @param locations
	 * @return
	 */
	private List< Point > flipAtCenter( final List< Point > locations ) {
		// System.out.println("FLIP FLIP FLIP FLIP FLIP FLIP FLIP FLIP");
		final ArrayList< Point > ret = new ArrayList<>(locations.size());

		final int centerInX = getAvgXpos();
		for ( final Point p : locations ) {
			final int newX = ( -1 * ( p.getIntPosition( 0 ) - centerInX ) ) + centerInX; // flip
																							// at
																							// center
			ret.add( new Point( newX, p.getIntPosition( 1 ), p.getIntPosition( 2 ) ) );
		}

		return ret;
	}

	/**
	 * @return the time-step this GLF corresponds to in the GL it is part of.
	 */
	public int getTime() {
		return this.getParent().getFrames().indexOf( this );
	}

	/**
	 * @return
	 */
//	public IntervalView< LongType > getParamaxflowSumImage( final IntervalView< FloatType > viewGLF ) {
//		if ( paramaxflowSumImage == null ) {
//			if ( viewGLF == null ) { return null; }
//			if ( !MoMA.USE_CLASSIFIER_FOR_PMF ) {
//				this.paramaxflowSumImage = GrowthLineSegmentationMagic.returnParamaxflowRegionSums( viewGLF );
//			} else {
//				this.paramaxflowSumImage = GrowthLineSegmentationMagic.returnClassificationBoostedParamaxflowRegionSums( viewGLF );
//			}
//			this.paramaxflowSolutions = GrowthLineSegmentationMagic.getNumSolutions();
//		}
//
//		return Views.interval( paramaxflowSumImage, Views.zeroMin( viewGLF ) );
//	}

//	public IntervalView< FloatType > getParamaxflowSumImageFloatTyped( final IntervalView< FloatType > viewGLF ) {
//		if ( paramaxflowSumImage == null ) {
//			if ( viewGLF == null ) { return null; }
//			getParamaxflowSumImage( viewGLF );
//		}
//
//		return Views.interval( Converters.convert( paramaxflowSumImage, new RealFloatNormalizeConverter( this.paramaxflowSolutions ), new FloatType() ), paramaxflowSumImage );
//	}
//
//	/**
//	 * @return the isParaMaxFlowComponentTree
//	 */
//	public boolean isParaMaxFlowComponentTree() {
//		return isParaMaxFlowComponentTree;
//	}

	/**
	 * Returns the number of cells in this GLF.
	 *
	 * @return
	 */
	public int getSolutionStats_numCells() {
		int cells = 0;
		final GrowthLineTrackingILP ilp = getParent().getIlp();
		for ( final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > set : ilp.getOptimalRightAssignments( this.getTime() ).values() ) {

			for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> ora : set ) {
				cells++;
			}
		}
		return cells;
	}

	/**
	 * Returns the position of the given hypothesis in the GL.
	 *
	 * @param hyp
	 * @return the uppermost segmented cell would return a 1. For each active
	 *         segmentation that is strictly above the given hypothesis the
	 *         return value is increased by 1.
	 */
	public int getSolutionStats_cellPos( final Hypothesis< Component< FloatType, ? >> hyp ) {
		int pos = 1;

		final GrowthLineTrackingILP ilp = getParent().getIlp();
		for ( final Set< AbstractAssignment< Hypothesis< Component< FloatType, ? >>> > optRightAssmnt : ilp.getOptimalRightAssignments(
				this.getTime() ).values() ) {

			for ( final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> ora : optRightAssmnt ) {
				Hypothesis< Component< FloatType, ? >> srcHyp = null;
				if ( ora instanceof MappingAssignment ) {
					srcHyp = ( ( MappingAssignment ) ora ).getSourceHypothesis();
				}
				if ( ora instanceof DivisionAssignment ) {
					srcHyp = ( ( DivisionAssignment ) ora ).getSourceHypothesis();
				}
				if ( ora instanceof ExitAssignment ) {
					srcHyp = ( ( ExitAssignment ) ora ).getAssociatedHypothesis();
				}
				if ( srcHyp != null ) {
					if ( srcHyp.getLocation().b < hyp.getLocation().a ) {
						pos++;
					}
				}
			}
		}
		return pos;
	}

	public Vector< ValuePair< ValuePair< Integer, Integer >, ValuePair< Integer, Integer > >> getSolutionStats_limitsAndRightAssType() {
		final Vector< ValuePair< ValuePair< Integer, Integer >, ValuePair< Integer, Integer > >> ret = new Vector<>();
		for ( final Hypothesis< Component< FloatType, ? > > hyp : getParent().getIlp().getOptimalRightAssignments( this.getTime() ).keySet() ) {

			final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> aa = getParent().getIlp().getOptimalRightAssignments( this.getTime() ).get( hyp ).iterator().next();

			int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
			for (Localizable localizable : hyp.getWrappedHypothesis()) {
				final int ypos = localizable.getIntPosition(0);
				min = Math.min(min, ypos);
				max = Math.max(max, ypos);
			}

			ret.add(new ValuePair<>(new ValuePair<>(min, max), new ValuePair<>(aa.getType(), (aa.isGroundTruth() || aa.isGroundUntruth()) ? 1 : 0)) );
		}

		ret.sort(Comparator.comparing(o -> o.a.a));
		return ret;
	}

	public Vector< ValuePair< Integer, Hypothesis< Component< FloatType, ? > >>> getSortedActiveHypsAndPos() {
		final Vector< ValuePair< Integer, Hypothesis< Component< FloatType, ? > >>> positionedHyps = new Vector<>();

		for ( final Hypothesis< Component< FloatType, ? > > hyp : getParent().getIlp().getOptimalRightAssignments( this.getTime() ).keySet() ) {
			// find out where this hypothesis is located along the GL
			int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
			for (Localizable localizable : hyp.getWrappedHypothesis()) {
				final int ypos = localizable.getIntPosition(0);
				min = Math.min(min, ypos);
				max = Math.max(max, ypos);
			}

			if ( !hyp.isPruned() ) {
				positionedHyps.add(new ValuePair<>(-max, hyp) );
			}
		}

		positionedHyps.sort(Comparator.comparing(o -> o.a));

		return positionedHyps;
	}
}
