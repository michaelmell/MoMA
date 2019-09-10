package com.jug.util;

import ij.IJ;
import ij.Prefs;
import io.scif.img.ImgIOException;
import io.scif.img.ImgOpener;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.stats.Normalize;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

/**
 * @author jug
 * 
 */
class DoubleTypeImgLoader {

	/**
	 * Loads all files containing ".tif" from a folder given by foldername.
	 * 
	 * @param strFolder
	 *            String pointing to folder containing images (ending with
	 *            '.tif')
	 * @return list containing all loaded tiff files
	 * @throws ImgIOException
	 * @throws IncompatibleTypeException
	 * @throws Exception
	 */
	private static List< Img< DoubleType >> loadTiffsFromFolder(final String strFolder) throws ImgIOException, IncompatibleTypeException, Exception {
		return loadTiffsFromFolder( strFolder, -1, -1, ( String[] ) null );
	}

	private static List< Img< DoubleType >> loadTiffsFromFolder(final String strFolder, final int minTime, final int maxTime, final String... filterStrings) throws Exception {

		final File folder = new File( strFolder );
		final FilenameFilter filter = (dir, name) -> {
			boolean isMatching = name.contains( ".tif" );
			for ( final String filter1 : filterStrings ) {
				isMatching = isMatching && name.contains(filter1);
			}
			if (isMatching) {
				final String strTime = name.split( "_t" )[ 1 ].substring( 0, 4 );
				final int time = Integer.parseInt( strTime );
				if ( ( minTime != -1 && time < minTime ) || ( maxTime != -1 && time > maxTime ) ) {
					isMatching = false;
				}
			}
			return isMatching;
		};
		final File[] listOfFiles = folder.listFiles( filter );
		if ( listOfFiles == null ) { throw new Exception( "Given argument is not a valid folder!" ); }

		return loadTiffs( listOfFiles );
	}

	/**
	 * 
	 * @param strFolder
	 * @param minTime
	 * @param maxTime
	 * @param normalize
	 * @param filterStrings
	 * @return
	 * @throws ImgIOException
	 * @throws IncompatibleTypeException
	 * @throws Exception
	 */
	private static List< Img< DoubleType >> loadMMTiffsFromFolder(final String strFolder, final int minTime, final int maxTime, final boolean normalize, final String... filterStrings) throws ImgIOException, IncompatibleTypeException, Exception {

		final File folder = new File( strFolder );
		final FilenameFilter filter = (dir, name) -> {
			boolean isMatching = name.contains( ".tif" );
			for ( final String filter1 : filterStrings ) {
				isMatching = isMatching && name.contains(filter1);
			}
			if (isMatching) {
				final String strTime = name.split( "_t" )[ 1 ].substring( 0, 4 );
				final int time = Integer.parseInt( strTime );
				if ( ( minTime != -1 && time < minTime ) || ( maxTime != -1 && time > maxTime ) ) {
					isMatching = false;
				}
			}
			return isMatching;
		};
		final File[] listOfFiles = folder.listFiles( filter );
		if ( listOfFiles == null ) { throw new Exception( "Given argument is not a valid folder!" ); }

		return loadMMTiffSequence( listOfFiles, normalize );
	}

	/**
	 * @param listOfFiles
	 * @return
	 * @throws ImgIOException
	 */
	private static List< Img< DoubleType >> loadTiffs(final File[] listOfFiles) throws ImgIOException {
		final int numProcessors = Prefs.getThreads();
		final int numThreads = Math.min( listOfFiles.length, numProcessors );

		final List< Img< DoubleType > > images = new ArrayList<>(listOfFiles.length);
		for ( int i = 0; i < listOfFiles.length; i++ ) {
			images.add( null );
		}

		final ImgIOException ioe = new ImgIOException( "One of the image loading threads had a problem reading from file." );

		final Thread[] threads = new Thread[ numThreads ];

		class ImageProcessingThread extends Thread {

			final int numThread;
			final int numThreads;

			ImageProcessingThread(final int numThread, final int numThreads) {
				this.numThread = numThread;
				this.numThreads = numThreads;
			}

			@Override
			public void run() {

				for ( int t = numThread; t < listOfFiles.length; t += numThreads ) {
					try {
						images.set( t, loadTiff( listOfFiles[ t ] ) );
					} catch ( final ImgIOException e ) {
						ioe.setStackTrace( e.getStackTrace() );
					}
				}
			}
		}

		// start threads
		for ( int i = 0; i < numThreads; i++ ) {
			threads[ i ] = new ImageProcessingThread( i, numThreads );
			threads[ i ].start();
		}

		// wait for all threads to terminate
		for ( final Thread thread : threads ) {
			try {
				thread.join();
			} catch ( final InterruptedException ignored) {}
		}

		return images;
	}

	/**
	 * Load and selectively normalize channels.
	 * Assumptions: filename contains channel info in format "_c%04d".
	 * 
	 * @param listOfFiles
	 * @return
	 * @throws ImgIOException
	 */
	private static List< Img< DoubleType >> loadMMTiffSequence(final File[] listOfFiles, final boolean normalize) throws ImgIOException {
		final int numProcessors = Prefs.getThreads();
		final int numThreads = Math.min( listOfFiles.length, numProcessors );

		final List< Img< DoubleType > > images = new ArrayList<>(listOfFiles.length);
		for ( int i = 0; i < listOfFiles.length; i++ ) {
			images.add( null );
		}

		final ImgIOException ioe = new ImgIOException( "One of the image loading threads had a problem reading from file." );

		final Thread[] threads = new Thread[ numThreads ];

		class ImageProcessingThread extends Thread {

			final int numThread;
			final int numThreads;

			ImageProcessingThread(final int numThread, final int numThreads) {
				this.numThread = numThread;
				this.numThreads = numThreads;
			}

			@Override
			public void run() {

				for ( int t = numThread; t < listOfFiles.length; t += numThreads ) {
					try {
						images.set( t, loadTiff( listOfFiles[ t ] ) );
					} catch ( final ImgIOException e ) {
						ioe.setStackTrace( e.getStackTrace() );
					}
					// Selective Normalization!
					if ( normalize ) {
						Normalize.normalize( images.get( t ), new DoubleType( 0.0 ), new DoubleType( 1.0 ) );
					}
				}
			}
		}

		// start threads
		for ( int i = 0; i < numThreads; i++ ) {
			threads[ i ] = new ImageProcessingThread( i, numThreads );
			threads[ i ].start();
		}

		// wait for all threads to terminate
		for ( final Thread thread : threads ) {
			try {
				thread.join();
			} catch ( final InterruptedException ignored) {}
		}

		return images;
	}

	/**
	 * @return
	 * @throws ImgIOException
	 */
	private static Img< DoubleType > loadTiff(final File file) throws ImgIOException {
		final ImgFactory< DoubleType > imgFactory = new ArrayImgFactory<>();
		final ImgOpener imageOpener = new ImgOpener();

		System.out.print( "\n >> Loading file '" + file.getName() + "' ..." );
//		final List< SCIFIOImgPlus< DoubleType >> imgs = imageOpener.openImgs( file.getAbsolutePath(), imgFactory, new DoubleType() );
//		final Img< DoubleType > img = imgs.get( 0 ).getImg();
		//		ImageJFunctions.show( img );
		return (Img<DoubleType>) ImagePlusAdapter.wrapReal( IJ.openImage( file.getAbsolutePath() ) );
	}

	/**
	 * Loads all files containing "*.tif" from a given folder.
	 * 
	 * @param inFolder
	 *            Folder containing images (ending with '*.tif')
	 * @return 3d Img, normalized to [0,1]
	 * @throws ImgIOException
	 * @throws IncompatibleTypeException
	 * @throws Exception
	 */
	public static < T extends RealType< T > & NativeType< T > > Img< DoubleType > loadFolderAsStack( final File inFolder, final boolean normalize ) throws ImgIOException, IncompatibleTypeException, Exception {
		return loadPathAsStack( inFolder.getAbsolutePath(), normalize );
	}

	/**
	 * Loads all files containing "*.tif" from a given folder.
	 * 
	 * @param inFolder
	 *            Folder containing images (ending with '*.tif')
	 * @return 3d Img, normalized to [0,1]
	 * @throws ImgIOException
	 * @throws IncompatibleTypeException
	 * @throws Exception
	 */
	public static < T extends RealType< T > & NativeType< T > > Img< DoubleType > loadFolderAsChannelStack( final File inFolder ) throws ImgIOException, IncompatibleTypeException, Exception {
		return loadPathAsChannelStack( inFolder.getAbsolutePath() );
	}

	/**
	 * Loads all files containing ".tif" from a folder given by foldername.
	 * 
	 * @param strFolder
	 *            String pointing to folder containing images (ending with
	 *            '.tif')
	 * @return 3d Img, normalized to [0,1]
	 * @throws ImgIOException
	 * @throws IncompatibleTypeException
	 * @throws Exception
	 */
	private static < T extends RealType< T > & NativeType< T > > Img< DoubleType > loadPathAsChannelStack(final String strFolder) throws ImgIOException, IncompatibleTypeException, Exception {

		final List< Img< DoubleType >> imageList = loadTiffsFromFolder( strFolder );

		Img< DoubleType > stack;
		final long width = imageList.get( 0 ).dimension( 0 );
		final long height = imageList.get( 0 ).dimension( 1 );
		final long channels = imageList.get( 0 ).dimension( 2 );
		final long frames = imageList.size();

		stack = new ArrayImgFactory< DoubleType >().create( new long[] { width, height, channels, frames }, new DoubleType() );

		// Add images to stack...
		int i = 0;
		for ( final RandomAccessible< DoubleType > image : imageList ) {
			final RandomAccessibleInterval< DoubleType > viewZSlize = Views.hyperSlice( stack, 3, i );

			for ( int c = 0; c < channels; c++ ) {
				final RandomAccessibleInterval< DoubleType > viewChannel = Views.hyperSlice( viewZSlize, 2, c );
				final IterableInterval< DoubleType > iterChannel = Views.iterable( viewChannel );

				if ( image.numDimensions() < 3 ) {
					if ( c > 0 ) { throw new ImgIOException( "Not all images to be loaded contain the same number of color channels!" ); }
					DataMover.copy( image, iterChannel );
				} else {
					DataMover.copy( Views.hyperSlice( image, 2, c ), iterChannel );
				}
				Normalize.normalize( iterChannel, new DoubleType( 0.0 ), new DoubleType( 1.0 ) );
			}
			i++;
		}

//		ImageJFunctions.show( stack, "muh" );
		return stack;
	}

	/**
	 * Loads all files containing ".tif" from a folder given by foldername.
	 * 
	 * @param strFolder
	 *            String pointing to folder containing images (ending with
	 *            '.tif')
	 * @return 3d Img, normalized to [0,1]
	 * @throws ImgIOException
	 * @throws IncompatibleTypeException
	 * @throws Exception
	 */
	private static < T extends RealType< T > & NativeType< T > > Img< DoubleType > loadPathAsStack(final String strFolder, final boolean normalize) throws ImgIOException, IncompatibleTypeException, Exception {
		return loadPathAsStack( strFolder, -1, -1, normalize, ( String[] ) null );
	}

	private static < T extends RealType< T > & NativeType< T > > Img< DoubleType > loadPathAsStack(final String strFolder, final int minTime, final int maxTime, final boolean normalize, final String... filter) throws Exception {

		final List< Img< DoubleType >> imageList = loadTiffsFromFolder( strFolder, minTime, maxTime, filter );
		if ( imageList.size() == 0 ) return null;

		Img< DoubleType > stack;
		final long width = imageList.get( 0 ).dimension( 0 );
		final long height = imageList.get( 0 ).dimension( 1 );
		final long frames = imageList.size();

		stack = new ArrayImgFactory< DoubleType >().create( new long[] { width, height, frames }, new DoubleType() );

		// Add images to stack...
		int i = 0;
		for ( final Img< DoubleType > image : imageList ) {
			final RandomAccessibleInterval< DoubleType > viewZSlize = Views.hyperSlice( stack, 2, i );
			final IterableInterval< DoubleType > iterZSlize = Views.iterable( viewZSlize );

			DataMover.copy( Views.extendZero( image ), iterZSlize );
			if ( normalize ) {
				Normalize.normalize( iterZSlize, new DoubleType( 0.0 ), new DoubleType( 1.0 ) );
			}
			i++;
		}

		return stack;
	}

	public static < T extends RealType< T > & NativeType< T > > Img< DoubleType > loadMMPathAsStack( final String strFolder, final int minTime, final int maxTime, final boolean normalize, final String... filter ) throws Exception {

		final List< Img< DoubleType >> imageList = loadMMTiffsFromFolder( strFolder, minTime, maxTime, normalize, filter );
		if ( imageList.size() == 0 ) return null;

		Img< DoubleType > stack;
		final long width = imageList.get( 0 ).dimension( 0 );
		final long height = imageList.get( 0 ).dimension( 1 );
		final long frames = imageList.size();

		stack = new ArrayImgFactory< DoubleType >().create( new long[] { width, height, frames }, new DoubleType() );

		// Add images to stack...
		int i = 0;
		for ( final Img< DoubleType > image : imageList ) {
			final RandomAccessibleInterval< DoubleType > viewZSlize = Views.hyperSlice( stack, 2, i );
			final IterableInterval< DoubleType > iterZSlize = Views.iterable( viewZSlize );

			DataMover.copy( Views.extendZero( image ), iterZSlize );
			i++;
		}

		return stack;
	}

	/**
	 * Loads a tiff sequence from the given folder. Only those files that
	 * contain <code>filterString</code> as substring in their filename will be
	 * considered. This function tries to automatically determine the number of
	 * time-points and channels to be loaded.
	 * Note: the filename is expected to encode numbers with four digits, like
	 * t=13 would be "_t0013".
	 * 
	 * @param strFolder
	 * @param filterString
	 * @return a <code>List</code> of multi-channel images of type
	 *         <code>Img<DoubleType></code>.
	 * @throws Exception
	 */
	public static < T extends RealType< T > & NativeType< T > > List< Img< DoubleType >> load2DTiffSequenceAsListOfMultiChannelImgs( final String strFolder, final String filterString ) throws Exception {
		int t, c;
		try {
			t = figureMaxCounterFromFolder( strFolder, filterString, "_t" );
			c = figureMaxCounterFromFolder( strFolder, filterString, "_c" );
		} catch ( final Exception e ) {
			throw new Exception( "Files in this folder seem not to comply to required format... could not determine number of time-points and number of channels!" );
		}
		return load2DTiffSequenceAsListOfMultiChannelImgs( strFolder, filterString, 0, t - 1, 1, c, 4 );
	}

	/**
	 * Loads a tiff sequence from the given folder. Only those files that
	 * contain <code>filterString</code> as substring in their filename will be
	 * considered.
	 * Naming convention: "<some_name>_t####_c####.tif", where # are digits and
	 * <some_name> does NOT contain "_t" or "_c".
	 * 
	 * @param strFolder
	 * @param filterString
	 * @param tmin
	 *            lowest time-index to be loaded.
	 * @param tmax
	 *            highest time-index to be loaded.
	 * @param cmin
	 *            lowest channel-index to be loaded.
	 * @param cmax
	 *            highest channel-index to be loaded.
	 * @param numDigits
	 *            the number of digits used to express the time and channel
	 *            indices, e.g. 0013 would be 4.
	 * @return a <code>List</code> of multi-channel images of type
	 *         <code>Img<DoubleType></code>.
	 * @throws Exception
	 */
	private static < T extends RealType< T > & NativeType< T > > List< Img< DoubleType >> load2DTiffSequenceAsListOfMultiChannelImgs(final String strFolder, final String filterString, final int tmin, final int tmax, final int cmin, final int cmax, final int numDigits) throws Exception {
		final List< Img< DoubleType >> ret = new ArrayList<>();

		final File folder = new File( strFolder );

		for ( int t = tmin; t <= tmax; t++ ) {
			final String tString = String.format( "_t%0" + numDigits + "d", t );

			final List< Img< DoubleType > > channelImgs = new ArrayList<>();
			for ( int c = cmin; c <= cmax; c++ ) {
				final String cString = String.format( "_c%0" + numDigits + "d", c );

				final FilenameFilter filter = (dir, name) -> name.contains( ".tif" ) && ((filterString == null) || name.contains(filterString)) && name.contains( tString ) && name.contains( cString );
				final File[] listOfFiles = folder.listFiles( filter );
				if ( listOfFiles.length == 0 || listOfFiles == null ) { throw new Exception( String.format( "Missing file for t=%d and c=%d", t, c ) ); }
				if ( listOfFiles.length > 1 ) { throw new Exception( String.format( "Multiple matching files for t=%d and c=%d", t, c ) ); }

				channelImgs.add( loadTiff( listOfFiles[ 0 ] ) );
			}
			ret.add( makeMultiChannelImage( channelImgs ) );
		}

		return ret;
	}

	/**
	 * @return
	 * @throws ImgIOException
	 */
	private static Img< DoubleType > makeMultiChannelImage(final List<Img<DoubleType>> imageList) throws ImgIOException {

		if ( imageList.get( 0 ).numDimensions() != 2 ) { throw new ImgIOException( "MultiChannel image can only be composed out of 2d images (so far)." ); }

		Img< DoubleType > retImage;
		final long width = imageList.get( 0 ).dimension( 0 );
		final long height = imageList.get( 0 ).dimension( 1 );
		final long channels = imageList.size();

		retImage = new ArrayImgFactory< DoubleType >().create( new long[] { width, height, channels }, new DoubleType() );

		// Add channels to images to to be returned...
		for ( int c = 0; c < channels; c++ ) {
			final Img< DoubleType > image = imageList.get( c );

			final RandomAccessibleInterval< DoubleType > viewChannel = Views.hyperSlice( retImage, 2, c );
			final IterableInterval< DoubleType > iterChannel = Views.iterable( viewChannel );

			if ( image.numDimensions() == 2 ) {
				DataMover.copy( image, iterChannel );
			} else {
				throw new ImgIOException( "MultiChannel image can only be composed out of non 2d images." );
			}
			Normalize.normalize( iterChannel, new DoubleType( 0.0 ), new DoubleType( 1.0 ) );
		}

//		ImageJFunctions.show( retImage, "muh" );
		return retImage;
	}

	/**
	 * @param frameList
	 * @return
	 * @throws ImgIOException
	 */
	public static Img< DoubleType > makeMultiFrameFromChannelImages( final List< Img< DoubleType >> frameList ) throws ImgIOException {

		if ( frameList.get( 0 ).numDimensions() != 3 ) { throw new ImgIOException( "MultiChannel image can only be composed out of 2d images (so far)." ); }

		Img< DoubleType > retImage;
		final long width = frameList.get( 0 ).dimension( 0 );
		final long height = frameList.get( 0 ).dimension( 1 );
		final long channels = frameList.get( 0 ).dimension( 2 );
		final long frames = frameList.size();

		retImage = new ArrayImgFactory< DoubleType >().create( new long[] { width, height, channels, frames }, new DoubleType() );

		// Add frames to images to to be returned...
		for ( int f = ( int ) ( frames - 1 ); f >= 0; f-- ) {
			final Img< DoubleType > image = frameList.get( f );

			if ( image.numDimensions() == 3 ) {
				if ( channels != image.dimension( 2 ) ) { throw new ImgIOException( "Not all images to be loaded contain the same number of color channels!" ); }

				for ( int c = 0; c < channels; c++ ) {
					final RandomAccessibleInterval< DoubleType > sourceChannel = Views.hyperSlice( image, 2, c );
					final RandomAccessibleInterval< DoubleType > viewChannel = Views.hyperSlice( Views.hyperSlice( retImage, 3, f ), 2, c );
					final IterableInterval< DoubleType > iterChannel = Views.iterable( viewChannel );

					Normalize.normalize( iterChannel, new DoubleType( 0.0 ), new DoubleType( 1.0 ) );
					DataMover.copy( sourceChannel, iterChannel );
				}
				frameList.remove( f );
			} else {
				throw new ImgIOException( "MultiFrame image can only be composed out of non 3d images." );
			}
		}

//		ImageJFunctions.show( stack, "muh" );
		return retImage;
	}

	/**
	 * Parses all filenames found in given folder, filters them by given filter,
	 * and extracts the maximum int value that can be parsed after the given
	 * prefix. Note: the substring to be parsed an int must start right after
	 * the given prefix and MUST be terminated by either '_' or '.'!
	 * 
	 * @param strFolder
	 *            the folder to look into.
	 * @param filterString
	 *            only files containing this string as substring in their
	 *            filename will be considered.
	 * @param prefix
	 *            indicates the starting position to start parsing for an
	 *            int-value. (Note: the int must terminate with either '_' or
	 *            '.'!)
	 * @return The maximum int-value found.
	 * @throws Exception
	 */
	private static int figureMaxCounterFromFolder(final String strFolder, final String filterString, final String prefix) throws Exception {
		int max = -1;

		final File folder = new File( strFolder );
		final FilenameFilter filter = (dir, name) -> name.contains( ".tif" ) && ((filterString == null) || name.contains(filterString)) && name.contains( prefix );
		final File[] listOfFiles = folder.listFiles( filter );
		if ( listOfFiles == null ) return max;

        for (File listOfFile : listOfFiles) {
            String str = listOfFile.getName();
            str = str.substring(str.indexOf(prefix) + prefix.length());
            int muh = str.indexOf("_");
            int mah = str.indexOf(".");
            if (muh == -1) muh = Integer.MAX_VALUE;
            if (mah == -1) mah = Integer.MAX_VALUE;
            if (muh == Integer.MAX_VALUE && mah == Integer.MAX_VALUE) {
                throw new NumberFormatException();
            }
            str = str.substring(0, Math.min(muh, mah));

            int num;
            try {
                num = Integer.parseInt(str);
            } catch (final NumberFormatException nfe) {
                throw new Exception("Naming convention in given folder do not comply to rules... Bad user! ;)");
            }

            if (max < num) max = num;
        }

		return max;
	}

}
