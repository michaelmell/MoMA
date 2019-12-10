package com.jug.export;

import com.jug.GrowthLineFrame;
import com.jug.MoMA;
import com.jug.gui.DialogCellStatsExportSetup;
import com.jug.gui.MoMAGui;
import com.jug.gui.OsDependentFileChooser;
import com.jug.gui.progress.DialogProgress;
import com.jug.lp.*;
import com.jug.util.ComponentTreeUtils;
import com.jug.util.Util;
import com.jug.util.componenttree.ComponentProperties;
import com.jug.util.componenttree.SimpleComponent;
import gurobi.GRBException;
import net.imglib2.IterableInterval;
import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import javax.swing.*;
import java.io.*;
import java.util.*;

/**
 * @author jug
 */
public class CellStatsExporter {

	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private ComponentProperties componentProperties = new ComponentProperties();

	private final MoMAGui gui;

	public CellStatsExporter( final MoMAGui gui ) {
		this.gui = gui;
	}

	private boolean showConfigDialog() {
		final DialogCellStatsExportSetup dialog =
				new DialogCellStatsExportSetup( gui, MoMA.EXPORT_USER_INPUTS, MoMA.EXPORT_DO_TRACK_EXPORT, MoMA.EXPORT_INCLUDE_HISTOGRAMS, MoMA.EXPORT_INCLUDE_QUANTILES, MoMA.EXPORT_INCLUDE_COL_INTENSITY_SUMS, MoMA.EXPORT_INCLUDE_PIXEL_INTENSITIES);
		dialog.ask();
		if ( !dialog.wasCanceled() ) {
			MoMA.EXPORT_DO_TRACK_EXPORT = dialog.doExportTracks;
			MoMA.EXPORT_USER_INPUTS = dialog.doExportUserInputs;
			MoMA.EXPORT_INCLUDE_HISTOGRAMS = dialog.includeHistograms;
			MoMA.EXPORT_INCLUDE_QUANTILES = dialog.includeQuantiles;
			MoMA.EXPORT_INCLUDE_COL_INTENSITY_SUMS = dialog.includeColIntensitySums;
			MoMA.EXPORT_INCLUDE_PIXEL_INTENSITIES = dialog.includePixelIntensities;
			return true;
		} else {
			return false;
		}
	}

	public void export() {
		if ( !MoMA.HEADLESS ) {
			if ( showConfigDialog() ) {
				final File folderToUse = OsDependentFileChooser.showSaveFolderChooser( gui, MoMA.STATS_OUTPUT_PATH, "Choose export folder..." );
				if ( folderToUse == null ) {
					JOptionPane.showMessageDialog(
							gui,
							"Illegal save location choosen!",
							"Error",
							JOptionPane.ERROR_MESSAGE );
					return;
				}
				if ( MoMA.EXPORT_DO_TRACK_EXPORT) {
					exportTracks( new File( folderToUse, "ExportedTracks_" + MoMA.getDefaultFilenameDecoration() + ".csv" ) );
				}
				if ( MoMA.EXPORT_USER_INPUTS) {
					final int tmin = MoMA.getMinTime();
					final int tmax = MoMA.getMaxTime();
					final File file =
							new File( folderToUse, String.format(
									"--[%d-%d]_%s.timm",
									tmin,
									tmax,
									MoMA.getDefaultFilenameDecoration() ) );
					MoMA.getGui().model.getCurrentGL().getIlp().saveState( file );
				}
				try {
					exportCellStats( new File( folderToUse, "ExportedCellStats_" + MoMA.getDefaultFilenameDecoration() + ".csv" ) );
				} catch ( final GRBException e ) {
					e.printStackTrace();
				}
				// always export mmproperties
				MoMA.instance.saveParams(new File( folderToUse, "mm.properties" ));
			}
		} else {
			if ( MoMA.EXPORT_DO_TRACK_EXPORT) {
				exportTracks( new File( MoMA.STATS_OUTPUT_PATH, "ExportedTracks_" + MoMA.getDefaultFilenameDecoration() + ".csv" ) );
			}
			if ( MoMA.EXPORT_USER_INPUTS) {
				final int tmin = MoMA.getMinTime();
				final int tmax = MoMA.getMaxTime();
				final File file =
						new File( MoMA.STATS_OUTPUT_PATH, String.format(
								"--[%d-%d]_%s.timm",
								tmin,
								tmax,
								MoMA.getDefaultFilenameDecoration() ) );
				MoMA.getGui().model.getCurrentGL().getIlp().saveState( file );
			}

			try {
				exportCellStats( new File( MoMA.STATS_OUTPUT_PATH, "ExportedCellStats_" + MoMA.getDefaultFilenameDecoration() + ".csv" ) );
			} catch ( final GRBException e ) {
				e.printStackTrace();
			}
			// always export mmproperties
			MoMA.instance.saveParams(new File( MoMA.STATS_OUTPUT_PATH, "mm.properties" ));
		}
	}

	/**
	 * @param file
	 * @throws GRBException
	 */
    private void exportCellStats(final File file) throws GRBException {

		// ------- THE MAGIC *** THE MAGIC *** THE MAGIC *** THE MAGIG -------
		final Vector< String > linesToExport = getCellStatsExportData();
		// -------------------------------------------------------------------

		System.out.println( "Exporting collected cell-statistics..." );
		Writer out;
		try {
			out = new OutputStreamWriter( new FileOutputStream( file ) );

			for ( final String line : linesToExport ) {
				out.write( line );
				out.write( "\n" );
			}
			out.close();
		} catch ( final FileNotFoundException e1 ) {
			JOptionPane.showMessageDialog( gui, "File not found!", "Error!", JOptionPane.ERROR_MESSAGE );
			e1.printStackTrace();
		} catch ( final IOException e1 ) {
			JOptionPane.showMessageDialog( gui, "Selected file could not be written!", "Error!", JOptionPane.ERROR_MESSAGE );
			e1.printStackTrace();
		}
		System.out.println( "...done!" );
	}

	private Vector< String > getCellStatsExportData() throws GRBException {
		// use US-style number formats! (e.g. '.' as decimal point)
		Locale.setDefault( new Locale( "en", "US" ) );

		final String loadedDataFolder = MoMA.props.getProperty( "import_path", "BUG -- could not get property 'import_path' while exporting cell statistics..." );
		final int numCurrGL = gui.sliderGL.getValue();
		final Vector< String > linesToExport = new Vector<>();

		final GrowthLineFrame firstGLF = gui.model.getCurrentGL().getFrames().get( 0 );
		final GrowthLineTrackingILP ilp = firstGLF.getParent().getIlp();
		final Vector< ValuePair< Integer, Hypothesis< Component< FloatType, ? > > > > segmentsInFirstFrameSorted =
				firstGLF.getSortedActiveHypsAndPos();
		final List< SegmentRecord > startingPoints = new ArrayList<>();

		int nextCellId = 0;
		final LinkedList< SegmentRecord > queue = new LinkedList<>();

		int cellNum = 0;
		for ( final ValuePair< Integer, Hypothesis< Component< FloatType, ? > > > valuePair : segmentsInFirstFrameSorted ) {

			cellNum++;
			final SegmentRecord point =
					new SegmentRecord(valuePair.b, nextCellId++, -1, -1, cellNum);
			startingPoints.add( point );

			final SegmentRecord prepPoint = new SegmentRecord(point, 1);
			prepPoint.hyp = point.hyp;

			if ( !prepPoint.hyp.isPruned() ) {
				queue.add( prepPoint );
			}
		}
		while ( !queue.isEmpty() ) {
			final SegmentRecord prepPoint = queue.poll();

			final AbstractAssignment< Hypothesis< Component< FloatType, ? >>> rightAssmt = ilp.getOptimalRightAssignment( prepPoint.hyp );

			if ( rightAssmt == null ) {
				continue;
			}
			// MAPPING -- JUST DROP SEGMENT STATS
			if ( rightAssmt.getType() == GrowthLineTrackingILP.ASSIGNMENT_MAPPING ) {
				final MappingAssignment ma = ( MappingAssignment ) rightAssmt;
				final SegmentRecord next = new SegmentRecord(prepPoint, 1);
				next.hyp = ma.getDestinationHypothesis();
				if ( !prepPoint.hyp.isPruned() ) {
					queue.add( next );
				}
			}
			// DIVISON -- NEW CELLS ARE BORN CURRENT ONE ENDS
			if ( rightAssmt.getType() == GrowthLineTrackingILP.ASSIGNMENT_DIVISION ) {
				final DivisionAssignment da = ( DivisionAssignment ) rightAssmt;

				prepPoint.pid = prepPoint.id;
				prepPoint.tbirth = prepPoint.frame;

				prepPoint.id = nextCellId;
				prepPoint.hyp = da.getLowerDesinationHypothesis();
				prepPoint.daughterTypeOrPosition = SegmentRecord.LOWER;
				if ( !prepPoint.hyp.isPruned() && !( prepPoint.tbirth > gui.sliderTime.getMaximum() ) ) {
					final SegmentRecord newPoint = new SegmentRecord(prepPoint, 0);
					newPoint.genealogy.add( SegmentRecord.LOWER );
					startingPoints.add( newPoint.clone() );
					newPoint.frame++;
					queue.add( newPoint );
					nextCellId++;
				}

				prepPoint.id = nextCellId;
				prepPoint.hyp = da.getUpperDesinationHypothesis();
				prepPoint.daughterTypeOrPosition = SegmentRecord.UPPER;
				if ( !prepPoint.hyp.isPruned() && !( prepPoint.tbirth > gui.sliderTime.getMaximum() ) ) {
					final SegmentRecord newPoint = new SegmentRecord(prepPoint, 0);
					newPoint.genealogy.add( SegmentRecord.UPPER );
					startingPoints.add( newPoint.clone() );
					newPoint.frame++;
					queue.add( newPoint );
					nextCellId++;
				}
			}
		}

		// INITIALIZE PROGRESS-BAR if not run headless
		final DialogProgress dialogProgress = new DialogProgress( gui, "Exporting selected cell-statistics...", startingPoints.size() );
		if ( !MoMA.HEADLESS ) {
			dialogProgress.setVisible( true );
		}

		// Line 1: import folder
		linesToExport.add( loadedDataFolder );

		// Line 2: GL-id
		linesToExport.add( "GLidx = " + numCurrGL );

		// Line 3: #cells
		linesToExport.add( "numCells = " + startingPoints.size() );

		// Line 4: #channels
		linesToExport.add( "numChannels = " + MoMA.instance.getRawChannelImgs().size() );

		// Line 5: imageHeight
		final long h = MoMA.instance.getImgRaw().dimension( 1 );
		linesToExport.add( "imageHeight = " + h + "\n" );

		// Line 6: bottomOffset
		linesToExport.add(
				"glHeight = " + ( h - MoMA.GL_OFFSET_BOTTOM ) + "\n" );

		// Line 7: track region (pixel row interval we perform tracking within -- this is all but top and bottom offset areas)
		linesToExport.add( String.format("trackRegionInterval = [%d]", h - 1 ) );

		// Export all cells (we found all their starting segments above)
		for (SegmentRecord segmentRecord : startingPoints) {
			linesToExport.add(segmentRecord.toString());
			do {
				SimpleComponent<?> currentComponent = (SimpleComponent<?>) segmentRecord.hyp.getWrappedComponent();
				ValuePair<Integer, Integer> limits =
						ComponentTreeUtils.getTreeNodeInterval(currentComponent);

				final GrowthLineFrame glf = gui.model.getCurrentGL().getFrames().get(segmentRecord.frame);

				final int numCells = glf.getSolutionStats_numCells();
				final int cellRank = glf.getSolutionStats_cellPos(segmentRecord.hyp);

				final String genealogy = segmentRecord.getGenealogyString();

				ValuePair<Double, Double> minorAndMajorAxis = componentProperties.getMinorMajorAxis(currentComponent);
				
				// WARNING -- if you change substring 'frame' you need also to change the last-row-deletion procedure below for the ENDOFTRACKING case... yes, this is not clean... ;)
				String outputString = "\t";
				outputString += String.format("frame=%d;", segmentRecord.frame);
				outputString += String.format("pos_in_GL=[%d,%d];", cellRank, numCells);
				outputString += String.format("pixel_limits=[%d,%d]; ", limits.getA(), limits.getB());
				outputString += String.format("cell_center=%f; ", componentProperties.getCentroid(currentComponent));
				outputString += String.format("cell_width=%f; ", minorAndMajorAxis.getA());
				outputString += String.format("cell_length=%f; ", minorAndMajorAxis.getB());
				outputString += String.format("cell_area=%d; ", componentProperties.getArea(currentComponent));
				for (int c = 0; c < MoMA.instance.getRawChannelImgs().size(); c++) {
					final IntervalView<FloatType> channelFrame = Views.hyperSlice(MoMA.instance.getRawChannelImgs().get(c), 2, segmentRecord.frame);
				}
				outputString += String.format("num_pixels_in_box=%d; ", Util.getSegmentBoxPixelCount(segmentRecord.hyp, firstGLF.getAvgXpos()));
				outputString += String.format("genealogy=%s; ", genealogy);
				linesToExport.add(outputString);

				// export info per image channel
				for (int c = 0; c < MoMA.instance.getRawChannelImgs().size(); c++) {
					final IntervalView<FloatType> channelFrame = Views.hyperSlice(MoMA.instance.getRawChannelImgs().get(c), 2, segmentRecord.frame);
					final IterableInterval<FloatType> segmentBoxInChannel = Util.getSegmentBoxInImg(channelFrame, segmentRecord.hyp, firstGLF.getAvgXpos());

					final FloatType min = new FloatType();
					final FloatType max = new FloatType();
					Util.computeMinMax(segmentBoxInChannel, min, max);

					if (MoMA.EXPORT_INCLUDE_HISTOGRAMS) {
						final long[] hist = segmentRecord.computeChannelHistogram(segmentBoxInChannel, min.get(), max.get());
						StringBuilder histStr = new StringBuilder(String.format("\t\tch=%d; output=HISTOGRAM", c));
						histStr.append(String.format("; min=%8.3f; max=%8.3f", min.get(), max.get()));
						for (final long value : hist) {
							histStr.append(String.format("; %5d", value));
						}
						linesToExport.add(histStr.toString());
					}

					if (MoMA.EXPORT_INCLUDE_QUANTILES) {
						final float[] percentile = segmentRecord.computeChannelPercentile(segmentBoxInChannel);
						StringBuilder percentileStr = new StringBuilder(String.format("\t\tch=%d; output=PERCENTILES", c));
						percentileStr.append(String.format("; min=%8.3f; max=%8.3f", min.get(), max.get()));
						for (final float value : percentile) {
							percentileStr.append(String.format("; %8.3f", value));
						}
						linesToExport.add(percentileStr.toString());
					}

					if (MoMA.EXPORT_INCLUDE_COL_INTENSITY_SUMS) {
						final IntervalView<FloatType> columnBoxInChannel = Util.getColumnBoxInImg(channelFrame, segmentRecord.hyp, firstGLF.getAvgXpos());
						final float[] column_intensities = segmentRecord.computeChannelColumnIntensities(columnBoxInChannel);
						StringBuilder colIntensityStr = new StringBuilder(String.format("\t\tch=%d; output=COLUMN_INTENSITIES", c));
						for (final float value : column_intensities) {
							colIntensityStr.append(String.format("; %.3f", value));
						}
						linesToExport.add(colIntensityStr.toString());
					}

					if (MoMA.EXPORT_INCLUDE_PIXEL_INTENSITIES) {
						final IntervalView<FloatType> intensityBoxInChannel = Util.getIntensityBoxInImg(channelFrame, segmentRecord.hyp, firstGLF.getAvgXpos());
						final float[][] intensities = segmentRecord.getIntensities(intensityBoxInChannel);
						StringBuilder intensityStr = new StringBuilder(String.format("\t\tch=%d; output=PIXEL_INTENSITIES", c));
						for (int y = 0; y < intensities[0].length; y++) {
							for (float[] intensity : intensities) {
								intensityStr.append(String.format(";%.3f", intensity[y]));
							}
							intensityStr.append(" ");
						}
						linesToExport.add(intensityStr.toString());
					}
				}
				segmentRecord = segmentRecord.nextSegmentInTime(ilp);
			}
			while (segmentRecord.exists());

			if (segmentRecord.terminated_by == GrowthLineTrackingILP.ASSIGNMENT_EXIT) {
				linesToExport.add("\tEXIT\n");
			} else if (segmentRecord.terminated_by == GrowthLineTrackingILP.ASSIGNMENT_DIVISION) {
				linesToExport.add("\tDIVISION\n");
			} else if (segmentRecord.terminated_by == SegmentRecord.USER_PRUNING) {
				linesToExport.add("\tUSER_PRUNING\n");
			} else if (segmentRecord.terminated_by == SegmentRecord.ENDOFTRACKING) {
//				// UGLY TRICK ALERT: remember the trick to fix the tracking towards the last frame?
//				// Yes, we double the last frame. This also means that we should not export this fake frame, ergo we remove it here!
				String deleted;
				do {
					deleted = linesToExport.remove(linesToExport.size() - 1);
				}
				while (!deleted.trim().startsWith("frame"));
				linesToExport.add("\tENDOFDATA\n");
			} else {
				linesToExport.add("\tGUROBI_EXCEPTION\n");
			}

			// REPORT PROGRESS if needbe
			if (!MoMA.HEADLESS) {
				dialogProgress.hasProgressed();
			}
		}

		// Dispose ProgressBar in needbe
		if ( !MoMA.HEADLESS ) {
			dialogProgress.setVisible( false );
			dialogProgress.dispose();
		}

		return linesToExport;
	}

	private void exportTracks(final File file) {

		final Vector< Vector< String >> dataToExport = getTracksExportData();

		System.out.println( "Exporting data..." );
		Writer out;
		try {
			out = new OutputStreamWriter( new FileOutputStream( file ) );

			for ( final Vector< String > rowInData : dataToExport ) {
				for ( final String datum : rowInData ) {
					out.write( datum + ",\t " );
				}
				out.write( "\n" );
			}
			out.close();
		} catch ( final FileNotFoundException e1 ) {
			if ( !MoMA.HEADLESS )
				JOptionPane.showMessageDialog( gui, "File not found!", "Error!", JOptionPane.ERROR_MESSAGE );
			System.err.println( "Export Error: File not found!" );
			e1.printStackTrace();
		} catch ( final IOException e1 ) {
			if ( !MoMA.HEADLESS )
				JOptionPane.showMessageDialog( gui, "Selected file could not be written!", "Error!", JOptionPane.ERROR_MESSAGE );
			System.err.println( "Export Error: Selected file could not be written!" );
			e1.printStackTrace();
		}
		System.out.println( "...done!" );
	}

	private Vector< Vector< String >> getTracksExportData() {

		// use US-style number formats! (e.g. '.' as decimal point)
		Locale.setDefault( new Locale( "en", "US" ) );

		final String loadedDataFolder = MoMA.props.getProperty( "import_path", "BUG -- could not get property 'import_path' while exporting tracks..." );
		final int numCurrGL = gui.sliderGL.getValue();
		final int numGLFs = gui.model.getCurrentGL().getFrames().size();
		final Vector< Vector< String >> dataToExport = new Vector<>();

		final Vector< String > firstLine = new Vector<>();
		firstLine.add( loadedDataFolder );
		dataToExport.add( firstLine );
		final Vector< String > secondLine = new Vector<>();
		secondLine.add( "" + numCurrGL );
		secondLine.add( "" + numGLFs );
		dataToExport.add( secondLine );

		int i = 0;
		for ( final GrowthLineFrame glf : gui.model.getCurrentGL().getFrames() ) {
			final Vector< String > newRow = new Vector<>();
			newRow.add( "" + i );

			final int numCells = glf.getSolutionStats_numCells();
			final Vector< ValuePair< ValuePair< Integer, Integer >, ValuePair< Integer, Integer > >> data = glf.getSolutionStats_limitsAndRightAssType();

			newRow.add( "" + numCells );
			for ( final ValuePair< ValuePair< Integer, Integer >, ValuePair< Integer, Integer > > elem : data ) {
				final int min = elem.a.a;
				final int max = elem.a.b;
				final int type = elem.b.a;
				final int user_touched = elem.b.b;
				newRow.add( String.format( "%3d, %3d, %3d, %3d", min, max, type, user_touched ) );
			}

			dataToExport.add( newRow );
			i++;
		}

		return dataToExport;
	}

}
