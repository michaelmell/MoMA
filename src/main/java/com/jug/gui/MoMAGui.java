package com.jug.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.jug.lp.*;
import com.moma.auxiliary.Plotting;
import org.math.plot.Plot2DPanel;

import com.jug.GrowthLine;
import com.jug.GrowthLineFrame;
import com.jug.MoMA;
import com.jug.export.CellStatsExporter;
import com.jug.export.HtmlOverviewExporter;
import com.jug.gui.progress.DialogProgress;
import com.jug.gui.slider.RangeSlider;
import com.jug.util.ComponentTreeUtils;
import com.jug.util.SimpleFunctionAnalysis;
import com.jug.util.Util;
import com.jug.util.converter.RealFloatNormalizeConverter;
import com.jug.util.filteredcomponents.FilteredComponent;

import gurobi.GRBException;
import ij.ImageJ;
import net.imglib2.Localizable;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.algorithm.componenttree.ComponentForest;
import net.imglib2.converter.Converters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.miginfocom.swing.MigLayout;
import weka.gui.ExtensionFileFilter;

/**
 * @author jug
 */
public class MoMAGui extends JPanel implements ChangeListener, ActionListener {

	private static final long serialVersionUID = -1008974839249784873L;

	// -------------------------------------------------------------------------------------
	// fields
	// -------------------------------------------------------------------------------------
	public final MoMAModel model;

	// show helper lines in IntervalViews?
	private boolean showSegmentationAnnotations = true;


	// -------------------------------------------------------------------------------------
	// gui-fields
	// -------------------------------------------------------------------------------------
	private Viewer2DCanvas imgCanvasActiveLeft;
	public Viewer2DCanvas imgCanvasActiveCenter;
	private Viewer2DCanvas imgCanvasActiveRight;

	public JSlider sliderGL;
	public JSlider sliderTime;
	private RangeSlider sliderTrackingRange;
	private JLabel lblCurrentTime;

	private JTabbedPane tabsViews;
	private CountOverviewPanel panelCountingView;
	private JScrollPane panelSegmentationAndAssignmentView;
	private JPanel panelDetailedDataView;
	private Plot2DPanel plot;

	private AssignmentViewer leftAssignmentViewer;
	public AssignmentViewer rightAssignmentViewer;

	private JCheckBox cbAutosave;
//	private JButton btnRedoAllHypotheses;
//	private JButton btnExchangeSegHyps;
	private JButton btnRestart;
	private JButton viewSegmentsButton;
	private JButton btnOptimizeMore;
	private JButton btnExportHtml;
	private JButton btnExportData;

	private final String itemChannel0 = "Raw Channel 0";
	private final String itemChannel1 = "Raw Channel 1";
	private final String itemChannel2 = "Raw Channel 2";
//	String itemPMFRF = "PMFRF Sum Image";
//	String itemClassified = "RF BG Probability";
//	String itemSegmented = "RF Cell Segmentation";
	private JComboBox cbWhichImgToShow;

	// REMOVED because load/save does not go easy with this shit!
//	private JLabel lActiveHyps;

	private JTextField txtNumCells;

	// Batch interaction panels
	private JCheckBox cbSegmentationOkLeft;
	private JCheckBox cbSegmentationOkCenter;
	private JCheckBox cbSegmentationOkRight;

	private JCheckBox cbAssignmentsOkLeft;
	private JCheckBox cbAssignmentsOkRight;

	private JButton bFreezeHistory;
	private JButton bCheckBoxLineSet;
	private JButton bCheckBoxLineReset;

	// Menu-items
	private MenuItem menuViewShowConsole;
	private MenuItem menuShowImgRaw;
	private MenuItem menuShowImgTemp;

	private MenuItem menuProps;
	private MenuItem menuLoad;
	private MenuItem menuSave;

	private MenuItem menuSaveFG;

	// -------------------------------------------------------------------------------------
	// construction & gui creation
	// -------------------------------------------------------------------------------------
	/**
	 * Construction
	 *
	 * @param mmm
	 *            the MotherMachineModel to show
	 */
	public MoMAGui( final MoMAModel mmm ) {
		super( new BorderLayout() );

		this.model = mmm;

		buildGui();
		dataToDisplayChanged();
		focusOnSliderTime();
	}

	/**
	 * Builds the GUI.
	 */
	private void buildGui() {

		final MenuBar menuBar = new MenuBar();
		final Menu menuFile = new Menu( "File" );
		menuProps = new MenuItem( "Preferences..." );
		menuProps.addActionListener( this );
		menuLoad = new MenuItem( "Load tracking..." );
		menuLoad.addActionListener( this );
		menuSave = new MenuItem( "Save tracking..." );
		menuSave.addActionListener( this );
		menuSaveFG = new MenuItem( "Save FG..." );
		menuSaveFG.addActionListener( this );
		menuFile.add( menuProps );
		menuFile.addSeparator();
		menuFile.add( menuLoad );
		menuFile.add( menuSave );
		menuFile.addSeparator();
		menuFile.add( menuSaveFG );
		menuBar.add( menuFile );

		final Menu menuView = new Menu( "View" );
		menuViewShowConsole = new MenuItem( "Show/hide Console" );
		menuViewShowConsole.addActionListener( this );
		menuShowImgRaw = new MenuItem( "Show raw imges..." );
		menuShowImgRaw.addActionListener( this );
		menuShowImgTemp = new MenuItem( "Show BG-subtrackted imges..." );
		menuShowImgTemp.addActionListener( this );
		MenuItem menuTrain = new MenuItem("Show trainer window...");
		menuTrain.addActionListener( this );
		menuView.add( menuViewShowConsole );
		menuView.add(menuTrain);
		menuView.addSeparator();
		menuView.add( menuShowImgRaw );
		menuView.add( menuShowImgTemp );
		menuBar.add( menuView );
		if ( !MoMA.HEADLESS ) {
			MoMA.getGuiFrame().setMenuBar( menuBar );
		}

		final JPanel panelContent = new JPanel( new BorderLayout() );
		JPanel panelVerticalHelper;
		JPanel panelHorizontalHelper;

		// --- Slider for time and GL -------------

		sliderTime = new JSlider( SwingConstants.HORIZONTAL, 0, model.getCurrentGL().size() - 2, 0 );
		sliderTime.setValue( 1 );
		model.setCurrentGLF( sliderTime.getValue() );
		sliderTime.addChangeListener( this );
		if ( sliderTime.getMaximum() < 200 ) {
			sliderTime.setMajorTickSpacing( 10 );
			sliderTime.setMinorTickSpacing( 2 );
		} else {
			sliderTime.setMajorTickSpacing( 100 );
			sliderTime.setMinorTickSpacing( 10 );
		}
		sliderTime.setPaintTicks( true );
		sliderTime.setPaintLabels( true );
		sliderTime.setBorder( BorderFactory.createEmptyBorder( 0, 0, 0, 3 ) );
		lblCurrentTime = new JLabel( String.format( " t = %4d", sliderTime.getValue() ) );

		// --- Slider for TrackingRage ----------

		int max = model.getCurrentGL().size() - 2;
		if ( MoMA.getInitialOptRange() != -1 ) {
			max = Math.min( MoMA.getInitialOptRange(), model.getCurrentGL().size() - 2 );
		}
		sliderTrackingRange =
				new RangeSlider( 0, model.getCurrentGL().size() - 2 );
		sliderTrackingRange.setBorder( BorderFactory.createEmptyBorder( 0, 7, 0, 7 ) );
		sliderTrackingRange.setValue( 0 );
		if (MoMA.OPTIMISATION_INTERVAL_LENGTH >= 0) {
			sliderTrackingRange.setUpperValue(MoMA.OPTIMISATION_INTERVAL_LENGTH);
		} else {
			sliderTrackingRange.setUpperValue(max);
		}
		sliderTrackingRange.addChangeListener( this );
		final JLabel lblIgnoreBeyond =
				new JLabel( String.format( "opt. range:", sliderTrackingRange.getValue() ) );
		lblIgnoreBeyond.setToolTipText( "correct up to left slider / ignore data beyond right slider" );

		// --- Assemble sliders -----------------
		final JPanel panelSliderArrangement =
				new JPanel( new MigLayout( "wrap 2", "[]3[grow,fill]", "[]0[]" ) );
		panelSliderArrangement.add( lblIgnoreBeyond );
		panelSliderArrangement.add( sliderTrackingRange );
		panelSliderArrangement.add( lblCurrentTime );
		panelSliderArrangement.add( sliderTime );

		panelHorizontalHelper = new JPanel( new BorderLayout() );
		panelHorizontalHelper.add( panelSliderArrangement, BorderLayout.CENTER );
		panelContent.add( panelHorizontalHelper, BorderLayout.SOUTH );

		// Does not exist any more...
		sliderGL = new JSlider( SwingConstants.VERTICAL, 0, model.mm.getGrowthLines().size() - 1, 0 );
		sliderGL.setValue( 0 );
		sliderGL.addChangeListener( this );
		sliderGL.setMajorTickSpacing( 5 );
		sliderGL.setMinorTickSpacing( 1 );
		sliderGL.setPaintTicks( true );
		sliderGL.setPaintLabels( true );
		sliderGL.setBorder( BorderFactory.createEmptyBorder( 0, 0, 0, 3 ) );
		panelVerticalHelper = new JPanel( new BorderLayout() );
		panelVerticalHelper.setBorder( BorderFactory.createEmptyBorder( 10, 10, 0, 5 ) );
		panelVerticalHelper.add( new JLabel( "GL#" ), BorderLayout.NORTH );
		panelVerticalHelper.add( sliderGL, BorderLayout.CENTER );
		// show the slider only if it actually has a purpose...
		if ( sliderGL.getMaximum() > 1 ) {
			add( panelVerticalHelper, BorderLayout.WEST );
		}

		// --- All the TABs -------------

		tabsViews = new JTabbedPane();
		tabsViews.addChangeListener( this );

		panelCountingView = new CountOverviewPanel();
		panelSegmentationAndAssignmentView = new JScrollPane( buildSegmentationAndAssignmentView() );
		panelSegmentationAndAssignmentView.setHorizontalScrollBarPolicy( ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER );
		panelDetailedDataView = buildDetailedDataView();

		//tabsViews.add( "Cell Counting", panelCountingView );
		tabsViews.add( "Segm. & Assingments", panelSegmentationAndAssignmentView );
		tabsViews.add( "Detailed Data View", panelDetailedDataView );

		tabsViews.setSelectedComponent( panelSegmentationAndAssignmentView );

		// --- Controls ----------------------------------
		cbAutosave = new JCheckBox( "autosave?" );
		cbAutosave.addActionListener(this);
//		btnRedoAllHypotheses = new JButton( "Resegment" );
//		btnRedoAllHypotheses.addActionListener( this );
		btnRestart = new JButton( "Restart" );
		btnRestart.addActionListener( this );
		btnOptimizeMore = new JButton( "Optimize" );
		btnOptimizeMore.addActionListener( this );
		btnExportHtml = new JButton( "Export HTML" );
		btnExportHtml.addActionListener( this );
		btnExportData = new JButton( "Export Data" );
		btnExportData.addActionListener( this );
		panelHorizontalHelper = new JPanel( new FlowLayout( FlowLayout.RIGHT, 5, 0 ) );
		panelHorizontalHelper.setBorder( BorderFactory.createEmptyBorder( 3, 0, 5, 0 ) );
		panelHorizontalHelper.add( cbAutosave );
//		panelHorizontalHelper.add( btnRedoAllHypotheses );
		panelHorizontalHelper.add( btnRestart );
		panelHorizontalHelper.add( btnOptimizeMore );
		panelHorizontalHelper.add( btnExportHtml );
		panelHorizontalHelper.add( btnExportData );
		add( panelHorizontalHelper, BorderLayout.SOUTH );

		// --- Final adding and layout steps -------------

		panelContent.add( tabsViews, BorderLayout.CENTER );
		add( panelContent, BorderLayout.CENTER );

		// - - - - - - - - - - - - - - - - - - - - - - - -
		//  KEYSTROKE SETUP (usingInput- and ActionMaps)
		// - - - - - - - - - - - - - - - - - - - - - - - -
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( 't' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( 'g' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( 'a' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( 's' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( 'd' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( 'r' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( 'o' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( 'e' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( 'v' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put(	KeyStroke.getKeyStroke( 'b' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( '?' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( '0' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( '1' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( '2' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( '3' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( '4' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( '5' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( '6' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( '7' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( '8' ), "MMGUI_bindings" );
		this.getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( '9' ), "MMGUI_bindings" );

		this.getActionMap().put( "MMGUI_bindings", new AbstractAction() {

			private static final long serialVersionUID = 1L;
			@Override
			public void actionPerformed( final ActionEvent e ) {
				if ( e.getActionCommand().equals( "t" ) ) {
					sliderTime.requestFocus();
					dataToDisplayChanged();
				}
				if ( e.getActionCommand().equals( "g" ) ) {
					//sliderGL.requestFocus();
					sliderTime.setValue(sliderTrackingRange.getUpperValue());
					dataToDisplayChanged();
				}
				if ( e.getActionCommand().equals( "a" ) ) {
					/*if ( !tabsViews.getComponent( tabsViews.getSelectedIndex() ).equals( panelCountingView ) ) {
						tabsViews.setSelectedComponent( panelCountingView );
					}*/
					bFreezeHistory.doClick();
					dataToDisplayChanged();
				}
				if ( e.getActionCommand().equals( "s" ) ) {
					if ( !tabsViews.getComponent( tabsViews.getSelectedIndex() ).equals( panelSegmentationAndAssignmentView ) ) {
						tabsViews.setSelectedComponent( panelSegmentationAndAssignmentView );
					}
					dataToDisplayChanged();
				}
				if ( e.getActionCommand().equals( "d" ) ) {
					if ( !tabsViews.getComponent( tabsViews.getSelectedIndex() ).equals( panelDetailedDataView ) ) {
						tabsViews.setSelectedComponent( panelDetailedDataView );
					}
					dataToDisplayChanged();
				}
				if ( e.getActionCommand().equals( "e" ) ) {
					btnExportData.doClick();
				}
				if ( e.getActionCommand().equals( "r" ) ) {
					btnRestart.doClick();
				}
				if ( e.getActionCommand().equals( "o" ) ) {
					btnOptimizeMore.doClick();
				}
				if ( e.getActionCommand().equals( "v" ) ) {
					int selIdx = cbWhichImgToShow.getSelectedIndex();
					selIdx++;
					if ( selIdx == cbWhichImgToShow.getItemCount() ) {
						selIdx = 0;
					}
					cbWhichImgToShow.setSelectedIndex( selIdx );
				}
				if ( e.getActionCommand().equals( "b" ) ) {
					showSegmentationAnnotations = !showSegmentationAnnotations;
					imgCanvasActiveLeft.showSegmentationAnnotations( showSegmentationAnnotations );
					imgCanvasActiveCenter.showSegmentationAnnotations( showSegmentationAnnotations );
					imgCanvasActiveRight.showSegmentationAnnotations( showSegmentationAnnotations );
					dataToDisplayChanged();
				}
				if ( e.getActionCommand().equals( "?" ) || e.getActionCommand().equals( "0" ) || e.getActionCommand().equals( "1" ) || e.getActionCommand().equals( "2" ) || e.getActionCommand().equals( "3" ) || e.getActionCommand().equals( "4" ) || e.getActionCommand().equals( "5" ) || e.getActionCommand().equals( "6" ) || e.getActionCommand().equals( "7" ) || e.getActionCommand().equals( "8" ) || e.getActionCommand().equals( "9" ) ) {
					txtNumCells.requestFocus();
					txtNumCells.setText( e.getActionCommand() );
				}
			}
		} );
	}

	/**
	 * @return
	 */
	private JPanel buildSegmentationAndAssignmentView() {
		final JPanel panelContent = new JPanel( new BorderLayout() );

		final JPanel panelViewCenterHelper =
				new JPanel( new FlowLayout( FlowLayout.CENTER, 0, 10 ) );
		final JPanel panelView =
				new JPanel( new MigLayout( "wrap 7", "[]0[]0[]0[]0[]0[]0[]", "[]0[]" ) );

		// =============== panelIsee-part ===================
		final JPanel panelIsee = new JPanel();
		panelIsee.setLayout( new BoxLayout( panelIsee, BoxLayout.LINE_AXIS ) );

		final JLabel labelNumCells1 = new JLabel( "I see" );
		final JLabel labelNumCells2 = new JLabel( "cells!" );
		txtNumCells = new JTextField( "?", 2 );
		txtNumCells.setHorizontalAlignment( SwingConstants.CENTER );
		txtNumCells.setMaximumSize( txtNumCells.getPreferredSize() );
		txtNumCells.addActionListener(e -> {
			model.getCurrentGL().getIlp().autosave();

			int numCells;
			final GrowthLineTrackingILP ilp = model.getCurrentGL().getIlp();
			try {
				numCells = Integer.parseInt( txtNumCells.getText() );
			} catch ( final NumberFormatException nfe ) {
				numCells = -1;
				txtNumCells.setText( "?" );
				ilp.removeSegmentsInFrameCountConstraint( model.getCurrentTime() );
			}
			if ( numCells != -1 ) {
				try {
					ilp.removeSegmentsInFrameCountConstraint( model.getCurrentTime() );
					ilp.addSegmentsInFrameCountConstraint( model.getCurrentTime(), numCells );
				} catch ( final GRBException e1 ) {
					e1.printStackTrace();
				}
			}

			final Thread t = new Thread(() -> {
				model.getCurrentGL().runILP();
				dataToDisplayChanged();
				sliderTime.requestFocus();
			});
			t.start();
		});

		panelIsee.add( Box.createHorizontalGlue() );
		panelIsee.add( labelNumCells1 );
		panelIsee.add( txtNumCells );
		panelIsee.add( labelNumCells2 );
		panelIsee.add( Box.createHorizontalGlue() );

		// =============== panelDropdown-part ===================
		final JPanel panelDropdown = new JPanel();
		panelDropdown.setLayout( new BoxLayout( panelDropdown, BoxLayout.LINE_AXIS ) );
		cbWhichImgToShow = new JComboBox();
		String itemChannel0BGSubtr = "BG-subtr. Ch.0";
		cbWhichImgToShow.addItem(itemChannel0BGSubtr);
		cbWhichImgToShow.addItem( itemChannel0 );
		if ( model.mm.getRawChannelImgs().size() > 1 ) {
			cbWhichImgToShow.addItem( itemChannel1 );
		}
		if ( model.mm.getRawChannelImgs().size() > 2 ) {
			cbWhichImgToShow.addItem( itemChannel2 );
		}
//		cbWhichImgToShow.addItem( itemPMFRF );
//		cbWhichImgToShow.addItem( itemClassified );
//		cbWhichImgToShow.addItem( itemSegmented );
		cbWhichImgToShow.addActionListener(e -> dataToDisplayChanged());

		panelDropdown.add( Box.createHorizontalGlue() );
		panelDropdown.add( cbWhichImgToShow );
		panelDropdown.add( Box.createHorizontalGlue() );

//		btnExchangeSegHyps = new JButton( "switch" );
//		btnExchangeSegHyps.addActionListener( new ActionListener() {
//
//			@Override
//			public void actionPerformed( final ActionEvent e ) {
//				final GrowthLineFrame glf = model.getCurrentGLF();
//				if ( !glf.isParaMaxFlowComponentTree() ) {
//					glf.generateAwesomeSegmentationHypotheses( model.mm.getImgTemp() );
//				} else {
//					glf.generateSimpleSegmentationHypotheses( model.mm.getImgTemp() );
//				}
//				dataToDisplayChanged();
//			}
//		} );
//		lActiveHyps = new JLabel( "CT" );
//		lActiveHyps.setHorizontalAlignment( SwingConstants.CENTER );
//		lActiveHyps.setBorder( BorderFactory.createCompoundBorder( BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder( 2, 5, 2, 5 ) ) );
//		lActiveHyps.setPreferredSize( new Dimension( 65, lActiveHyps.getPreferredSize().height ) );

//		panelOptions.add( btnExchangeSegHyps );
//		panelOptions.add( lActiveHyps );
//		panelOptions.add( Box.createHorizontalGlue() );

		// =============== panelView-part ===================

		JPanel panelVerticalHelper;
		JPanel panelHorizontalHelper;
		JLabel labelHelper;

		final GrowthLineTrackingILP ilp = model.getCurrentGL().getIlp();

		// --- Left data viewer (t-1) -------------

		panelView.add( new JPanel() );

		panelVerticalHelper = new JPanel( new BorderLayout() );
		panelHorizontalHelper = new JPanel( new FlowLayout( FlowLayout.CENTER, 5, 0 ) );
		labelHelper = new JLabel( "t-1" );
		panelHorizontalHelper.add( labelHelper );
		panelVerticalHelper.add( panelHorizontalHelper, BorderLayout.NORTH );
		// - - - - - -
		imgCanvasActiveLeft = new Viewer2DCanvas( this, MoMA.GL_WIDTH_IN_PIXELS + 2 * MoMA.GL_PIXEL_PADDING_IN_VIEWS, ( int ) model.mm.getImgRaw().dimension( 1 ) );
		panelVerticalHelper.add( imgCanvasActiveLeft, BorderLayout.CENTER );
		panelVerticalHelper.setBorder( BorderFactory.createMatteBorder( 2, 2, 2, 2, Color.GRAY ) );
		panelVerticalHelper.setBackground( Color.BLACK );
		panelView.add( panelVerticalHelper );

		// --- Left assignment viewer (t-1 -> t) -------------
		panelVerticalHelper = new JPanel( new BorderLayout() );
		// - - - - - -
		leftAssignmentViewer = new AssignmentViewer( ( int ) model.mm.getImgRaw().dimension( 1 ), this );
		leftAssignmentViewer.addChangeListener(this);
		// the following block is a workaround. The left assignment viewer gets focus when MoMA starts. But it shouldn't
		leftAssignmentViewer.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) {
				focusOnSliderTime();
			}

			@Override
			public void focusLost(FocusEvent e) {

			}
		});
		if ( ilp != null )
			leftAssignmentViewer.display( ilp.getAllCompatibleRightAssignments( model.getCurrentTime() - 1 ) );
		// - - - - - -
		panelVerticalHelper.add( leftAssignmentViewer, BorderLayout.CENTER );
		panelView.add( panelVerticalHelper );

		// --- Center data viewer (t) -------------

		panelVerticalHelper = new JPanel( new BorderLayout() );
		panelHorizontalHelper = new JPanel( new FlowLayout( FlowLayout.CENTER, 5, 0 ) );
		labelHelper = new JLabel( "t" );
		panelHorizontalHelper.add( labelHelper );
		panelVerticalHelper.add( panelHorizontalHelper, BorderLayout.NORTH );
		// - - - - - -
		imgCanvasActiveCenter = new Viewer2DCanvas( this, MoMA.GL_WIDTH_IN_PIXELS + 2 * MoMA.GL_PIXEL_PADDING_IN_VIEWS, ( int ) model.mm.getImgRaw().dimension( 1 ) );
		panelVerticalHelper.add( imgCanvasActiveCenter, BorderLayout.CENTER );
		panelVerticalHelper.setBorder( BorderFactory.createMatteBorder( 3, 3, 3, 3, Color.RED ) );
		panelVerticalHelper.setBackground( Color.BLACK );
		panelView.add( panelVerticalHelper );

		// --- Right assignment viewer (t -> t+1) -------------
		panelVerticalHelper = new JPanel( new BorderLayout() );
		// - - - - - -
		rightAssignmentViewer = new AssignmentViewer( ( int ) model.mm.getImgRaw().dimension( 1 ), this );
		rightAssignmentViewer.addChangeListener(this);
		if ( ilp != null )
			rightAssignmentViewer.display( ilp.getAllCompatibleRightAssignments( model.getCurrentTime() ) );
		panelVerticalHelper.add( rightAssignmentViewer, BorderLayout.CENTER );
		panelView.add( panelVerticalHelper );

		// ---  Right data viewer (t+1) -------------

		panelVerticalHelper = new JPanel( new BorderLayout() );
		panelHorizontalHelper = new JPanel( new FlowLayout( FlowLayout.CENTER, 5, 0 ) );
		labelHelper = new JLabel( "t+1" );
		panelHorizontalHelper.add( labelHelper );
		panelVerticalHelper.add( panelHorizontalHelper, BorderLayout.NORTH );
		// - - - - - -
		imgCanvasActiveRight = new Viewer2DCanvas( this, MoMA.GL_WIDTH_IN_PIXELS + 2 * MoMA.GL_PIXEL_PADDING_IN_VIEWS, ( int ) model.mm.getImgRaw().dimension( 1 ) );
		panelVerticalHelper.add( imgCanvasActiveRight, BorderLayout.CENTER );
		panelVerticalHelper.setBorder( BorderFactory.createMatteBorder( 2, 2, 2, 2, Color.GRAY ) );
		panelVerticalHelper.setBackground( Color.BLACK );
		panelView.add( panelVerticalHelper );

		panelView.add( new JPanel() );


		// ---  ROW OF CHECKBOXES -------------

		final JLabel lblCheckBoxLine = new JLabel( "Correct are:" );
		panelView.add( lblCheckBoxLine, "align center" );
		// - - - - - -
		cbSegmentationOkLeft = new JCheckBox();
		cbSegmentationOkLeft.addActionListener(this);
		panelView.add( cbSegmentationOkLeft, "align center" );
		// - - - - - -
		cbAssignmentsOkLeft = new JCheckBox();
		cbAssignmentsOkLeft.addActionListener(this);
		panelView.add( cbAssignmentsOkLeft, "align center" );
		// - - - - - -
		cbSegmentationOkCenter = new JCheckBox();
		cbSegmentationOkCenter.addActionListener(this);
		panelView.add( cbSegmentationOkCenter, "align center" );
		// - - - - - -
		cbAssignmentsOkRight = new JCheckBox();
		cbAssignmentsOkRight.addActionListener(this);
		panelView.add( cbAssignmentsOkRight, "align center" );
		// - - - - - -
		cbSegmentationOkRight = new JCheckBox();
		cbSegmentationOkRight.addActionListener(this);
		panelView.add( cbSegmentationOkRight, "align center" );
		// - - - - - -
		bFreezeHistory = new JButton( "<-all" );
		bFreezeHistory.addActionListener( this );
		bCheckBoxLineSet = new JButton( "set" );
		bCheckBoxLineSet.addActionListener( this );
		panelView.add( bCheckBoxLineSet, "align center" );
		bCheckBoxLineReset = new JButton( "reset" );
		bCheckBoxLineReset.addActionListener( this );

		viewSegmentsButton = new JButton( "View Segments" );
		viewSegmentsButton.addActionListener( this );


		// - - - - - -

		panelView.add( bFreezeHistory, "align center" );
		panelView.add( panelIsee, "cell 1 2 5 1, align center" );
		panelView.add( bCheckBoxLineReset, "align center, wrap" );
		panelView.add( viewSegmentsButton );

		panelDropdown.setBorder( BorderFactory.createEmptyBorder( 15, 0, 0, 0 ) );
		panelView.add( panelDropdown, "cell 1 3 5 1, align center, wrap" );

		panelViewCenterHelper.add( panelView );
		panelContent.add( panelViewCenterHelper, BorderLayout.CENTER );

		return panelContent;
	}

	/**
	 * @return
	 */
	private JPanel buildDetailedDataView() {
		final JPanel panelDataView = new JPanel( new BorderLayout() );

		plot = new Plot2DPanel();
		updatePlotPanels();
		plot.setPreferredSize( new Dimension( 500, 500 ) );
		panelDataView.add( plot, BorderLayout.CENTER );

		return panelDataView;
	}

	/**
	 * Removes all plots from the plot panel and adds new ones showing the data
	 * corresponding to the current slider setting.
	 */
	private void updatePlotPanels() {

		final GrowthLineTrackingILP ilp = model.getCurrentGL().getIlp();

		// Intensity plot
		// --------------
		plot.removeAllPlots();

		final float[] yMidline = model.getCurrentGLF().getMirroredCenterLineValues( model.mm.getImgTemp() );
		float[] ySegmentationData;
//		if ( cbWhichImgToShow.getSelectedItem().equals( itemPMFRF ) ) {
//			ySegmentationData = model.getCurrentGLF().getAwesomeGapSeparationValues( model.mm.getImgTemp() );
//		} else {
			ySegmentationData = model.getCurrentGLF().getSimpleGapSeparationValues( model.mm.getImgTemp() );
//		}
		final float[] yAvg = new float[ yMidline.length ];
		final float constY = SimpleFunctionAnalysis.getSum( ySegmentationData ) / ySegmentationData.length;
		Arrays.fill(yAvg, constY);
		plot.addLinePlot( "Midline Intensities", new Color( 127, 127, 255 ), Util.makeDoubleArray( yMidline ) ); // Note-MM-2019-06-10: blue plot line
		plot.addLinePlot( "Segmentation data", new Color( 80, 255, 80 ), Util.makeDoubleArray( ySegmentationData ) ); // Note-MM-2019-06-10: green plot line
		plot.addLinePlot( "avg. fkt-value", new Color( 200, 64, 64 ), Util.makeDoubleArray( yAvg ) ); // Note-MM-2019-06-10: red plot line

		plot.setFixedBounds( 1, 0.0, 1.0 );

		// ComponentTreeNodes
		// ------------------
		dumpCosts( model.getCurrentGLF().getComponentTree(), ySegmentationData, ilp );
		if(ilp != null){
			printCosts( model.getCurrentGLF().getComponentTree(), ilp, "Segment" );
			printCosts( model.getCurrentGLF().getComponentTree(), ilp, "ExitAssignment" );
			printCosts( model.getCurrentGLF().getComponentTree(), ilp, "MappingAssignment" );
			printCosts( model.getCurrentGLF().getComponentTree(), ilp, "DivisionAssignment" );
		}
	}

	private < C extends Component< FloatType, C > > void printCosts( final ComponentForest< C > ct, final GrowthLineTrackingILP ilp, String costType ) {
		final int t = sliderTime.getValue();
		System.out.print("##################### PRINTING ALL COSTS AT TIME " + t + " FOR: " + costType + " #####################");
		for ( final C root : ct.roots() ) {
			System.out.println();
			int level = 0;
			ArrayList< C > ctnLevel = new ArrayList<>();
			ctnLevel.add( root );
			while ( ctnLevel.size() > 0 ) {
				for ( final Component< ?, ? > ctn : ctnLevel ) {
					if(costType.equals("Segment")) {
						System.out.print(String.format("%8.4f;\t", ilp.localIntensityBasedCost(t, ctn)));
					}
					else{
						List<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> assignments = ilp.nodes.getAssignmentsAt(t);
						for(AbstractAssignment<Hypothesis<Component<FloatType, ?>>> ass : assignments){
							if(costType.equals("ExitAssignment")){
								if(ass instanceof ExitAssignment)
									System.out.print( String.format( "%8.4f;\t", ass.getCost() ) );
							}
							else if(costType.equals("MappingAssignment")){
								if(ass instanceof MappingAssignment)
									System.out.print( String.format( "%8.4f;\t", ass.getCost() ) );
							}
							else if(costType.equals("DivisionAssignment")){
								if(ass instanceof DivisionAssignment)
									System.out.print( String.format( "%8.4f;\t", ass.getCost() ) );
							}
						}
					}
				}
				ctnLevel = ComponentTreeUtils.getAllChildren( ctnLevel );
				level++;
				System.out.println();
			}
		}
		System.out.print("##################### STOP PRINTING COSTS: " + costType + " #####################");
		System.out.println();
	}

	private < C extends Component< FloatType, C > > void dumpCosts( final ComponentForest< C > ct, final float[] ySegmentationData, final GrowthLineTrackingILP ilp ) {
		final int numCTNs = ComponentTreeUtils.countNodes( ct );
		final float[][] xydxdyCTNBorders = new float[ numCTNs ][ 4 ];
		final int t = sliderTime.getValue();

		int i = 0;
		for ( final C root : ct.roots() ) {
			System.out.println();
			int level = 0;
			ArrayList< C > ctnLevel = new ArrayList<>();
			ctnLevel.add( root );
			while ( ctnLevel.size() > 0 ) {
				for ( final Component< ?, ? > ctn : ctnLevel ) {
					addBoxAtIndex( i, ctn, xydxdyCTNBorders, ySegmentationData, level );
					if ( ilp != null ) {
						System.out.print( String.format(
								"%8.4f;\t",
								ilp.localIntensityBasedCost( t, ctn ) ) );
					}
					i++;
				}
				ctnLevel = ComponentTreeUtils.getAllChildren( ctnLevel );
				level++;
				System.out.println();
			}
		}
		plot.addBoxPlot( "Seg. Hypothesis", new Color( 127, 127, 127, 255 ), Util.makeDoubleArray2d( xydxdyCTNBorders ) );

		// Plot the segments, which are part of the optimal solution
		if ( ilp != null ) {
			if ( ilp.getOptimalSegmentation( t ).size() > 0 ) {
				final float[][] xydxdyCTNBordersActive = new float[ ilp.getOptimalSegmentation( t ).size() ][ 4 ];
				i = 0;
				for ( final Hypothesis< Component< FloatType, ? >> hyp : ilp.getOptimalSegmentation( t ) ) {
					final Component< FloatType, ? > ctn = hyp.getWrappedComponent();
					addBoxAtIndex( i, ctn, xydxdyCTNBordersActive, ySegmentationData, ComponentTreeUtils.getLevelInTree( ctn ) );
					i++;
				}
				plot.addBoxPlot( "Active Seg. Hypothesis", new Color( 255, 0, 0, 255 ), Util.makeDoubleArray2d( xydxdyCTNBordersActive ) );
			}
		}
	}

	/**
	 * @param index
	 * @param ctn
	 * @param boxDataArray
	 * @param ydata
	 * @param level
	 */
	@SuppressWarnings( "unchecked" )
	private void addBoxAtIndex( final int index, final Component< ?, ? > ctn, final float[][] boxDataArray, final float[] ydata, final int level ) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		Iterator< Localizable > componentIterator = ctn.iterator();
		if ( ctn instanceof FilteredComponent ) {
			componentIterator = ( ( FilteredComponent< FloatType > ) ctn ).iteratorExtended();
		}
		while ( componentIterator.hasNext() ) { // MM-2019-07-01: Here we determine the y-boundaries of the component for drawing
			final int pos = componentIterator.next().getIntPosition( 1 ); 
			min = Math.min( min, pos );
			max = Math.max( max, pos );
		}
		final int leftLocation = min;
		final int rightLocation = max;
		boxDataArray[ index ] = new float[] { 0.5f * ( leftLocation + rightLocation ) + 1, 1.0f - level * 0.05f - 0.02f, rightLocation - leftLocation, 0.02f };
	}

	// -------------------------------------------------------------------------------------
	// getters and setters
	// -------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------
	// methods
	// -------------------------------------------------------------------------------------
	/**
	 * Picks the right hyperslice in Z direction in imgRaw and sets an
	 * View.offset according to the current offset settings. Note: this method
	 * does not and should not invoke a repaint!
	 */
	@SuppressWarnings( { "unchecked"} )
	public void dataToDisplayChanged() {

		final GrowthLineTrackingILP ilp = model.getCurrentGL().getIlp();

		// IF 'COUNTING VIEW' VIEW IS ACTIVE
		// =================================
		if ( tabsViews.getComponent( tabsViews.getSelectedIndex() ).equals( panelCountingView ) ) {
			if ( ilp != null ) {
				panelCountingView.showData( model.getCurrentGL() );
			} else {
				panelCountingView.showData( null );
			}
		}

		// IF SEGMENTATION AND ASSIGNMENT VIEW IS ACTIVE
		// =============================================
		if ( tabsViews.getComponent( tabsViews.getSelectedIndex() ).equals( panelSegmentationAndAssignmentView ) ) {
			// - - t-1 - - - - - -

			if ( model.getCurrentGLFsPredecessor() != null ) {
				final GrowthLineFrame glf = model.getCurrentGLFsPredecessor();
				/**
				 * The view onto <code>imgRaw</code> that is supposed to be shown on screen
				 * (left one in active assignments view).
				 */
				IntervalView<FloatType> viewImgLeftActive = Views.offset(Views.hyperSlice(model.mm.getImgRaw(), 2, glf.getOffsetF()), glf.getOffsetX() - MoMA.GL_WIDTH_IN_PIXELS / 2 - MoMA.GL_PIXEL_PADDING_IN_VIEWS, glf.getOffsetY());
				imgCanvasActiveLeft.setScreenImage( glf, viewImgLeftActive);
			} else {
				// show something empty
				imgCanvasActiveLeft.setEmptyScreenImage();
			}

			// - - t+1 - - - - - -

			if ( model.getCurrentGLFsSuccessor() != null && sliderTime.getValue() < sliderTime.getMaximum() ) { // hence copy of last frame for border-problem avoidance
				final GrowthLineFrame glf = model.getCurrentGLFsSuccessor();
				/**
				 * The view onto <code>imgRaw</code> that is supposed to be shown on screen
				 * (right one in active assignments view).
				 */
				IntervalView<FloatType> viewImgRightActive = Views.offset(Views.hyperSlice(model.mm.getImgRaw(), 2, glf.getOffsetF()), glf.getOffsetX() - MoMA.GL_WIDTH_IN_PIXELS / 2 - MoMA.GL_PIXEL_PADDING_IN_VIEWS, glf.getOffsetY());
				imgCanvasActiveRight.setScreenImage( glf, viewImgRightActive);
			} else {
				// show something empty
				imgCanvasActiveRight.setEmptyScreenImage();
			}

			// - -  t  - - - - - -

			final GrowthLineFrame glf = model.getCurrentGLF();
//			final IntervalView< FloatType > paramaxflowSumImageFloatTyped = model.getCurrentGLF().getParamaxflowSumImageFloatTyped( null );
			final FloatType min = new FloatType();
			final FloatType max = new FloatType();

//			if ( paramaxflowSumImageFloatTyped != null && cbWhichImgToShow.getSelectedItem().equals( itemPMFRF ) ) {
//				imgCanvasActiveCenter.setScreenImage( glf, paramaxflowSumImageFloatTyped );
//			} else
			/**
			 * The view onto <code>imgRaw</code> that is supposed to be shown on screen
			 * (center one in active assignments view).
			 */
			IntervalView<FloatType> viewImgCenterActive;
			if ( cbWhichImgToShow.getSelectedItem().equals( itemChannel0 ) ) {
				viewImgCenterActive = Views.offset( Views.hyperSlice( model.mm.getImgRaw(), 2, glf.getOffsetF() ), glf.getOffsetX() - MoMA.GL_WIDTH_IN_PIXELS / 2 - MoMA.GL_PIXEL_PADDING_IN_VIEWS, glf.getOffsetY() );
				imgCanvasActiveCenter.setScreenImage( glf, viewImgCenterActive);
			} else if ( cbWhichImgToShow.getSelectedItem().equals( itemChannel1 ) ) {
				final IntervalView< FloatType > viewToShow = Views.hyperSlice( model.mm.getRawChannelImgs().get( 1 ), 2, glf.getOffsetF() );
				Util.computeMinMax( Views.iterable( viewToShow ), min, max );
				viewImgCenterActive =
						Views.offset(
								Converters.convert(
										( RandomAccessibleInterval< FloatType > ) viewToShow,
										new RealFloatNormalizeConverter( max.get() ),
										new FloatType() ),
								glf.getOffsetX() - MoMA.GL_WIDTH_IN_PIXELS / 2 - MoMA.GL_PIXEL_PADDING_IN_VIEWS,
								glf.getOffsetY() );
				imgCanvasActiveCenter.setScreenImage( glf, viewImgCenterActive);
			} else if ( cbWhichImgToShow.getSelectedItem().equals( itemChannel2 ) ) {
				final IntervalView< FloatType > viewToShow = Views.hyperSlice( model.mm.getRawChannelImgs().get( 2 ), 2, glf.getOffsetF() );
				Util.computeMinMax( Views.iterable( viewToShow ), min, max );
				viewImgCenterActive =
						Views.offset(
								Converters.convert(
										( RandomAccessibleInterval< FloatType > ) viewToShow,
										new RealFloatNormalizeConverter( max.get() ),
										new FloatType() ),
								glf.getOffsetX() - MoMA.GL_WIDTH_IN_PIXELS / 2 - MoMA.GL_PIXEL_PADDING_IN_VIEWS,
								glf.getOffsetY() );
				imgCanvasActiveCenter.setScreenImage( glf, viewImgCenterActive);
//			} else if ( cbWhichImgToShow.getSelectedItem().equals( itemClassified ) ) {
//				final Thread t = new Thread() {
//
//					@Override
//					public void run() {
//						final IntervalView< FloatType > sizeEstimationImageFloatTyped = Views.offset( Views.hyperSlice( model.mm.getCellClassificationImgs(), 2, glf.getOffsetF() ), glf.getOffsetX() - MotherMachine.GL_WIDTH_IN_PIXELS / 2 - MotherMachine.GL_PIXEL_PADDING_IN_VIEWS, glf.getOffsetY() );
//						imgCanvasActiveCenter.setScreenImage( glf, sizeEstimationImageFloatTyped );
//					}
//				};
//				t.start();
//			} else if ( cbWhichImgToShow.getSelectedItem().equals( itemSegmented ) ) {
//				final Thread t = new Thread() {
//
//					@Override
//					public void run() {
//						final IntervalView< FloatType > sizeEstimationImageFloatTyped = Views.offset( Converters.convert( Views.hyperSlice( model.mm.getCellSegmentedChannelImgs(), 2, glf.getOffsetF() ), new RealFloatNormalizeConverter( 1.0f ), new FloatType() ), glf.getOffsetX() - MotherMachine.GL_WIDTH_IN_PIXELS / 2 - MotherMachine.GL_PIXEL_PADDING_IN_VIEWS, glf.getOffsetY() );
//						imgCanvasActiveCenter.setScreenImage( glf, sizeEstimationImageFloatTyped );
//					}
//				};
//				t.start();
			} else { // BG-subtracted Channel 0 selected or PMFRF not available
				viewImgCenterActive = Views.offset( Views.hyperSlice( model.mm.getImgTemp(), 2, glf.getOffsetF() ), glf.getOffsetX() - MoMA.GL_WIDTH_IN_PIXELS / 2 - MoMA.GL_PIXEL_PADDING_IN_VIEWS, glf.getOffsetY() );
				imgCanvasActiveCenter.setScreenImage( glf, viewImgCenterActive);
			}

//			if ( glf.isParaMaxFlowComponentTree() ) {
//				lActiveHyps.setText( "PMFRF" );
//				lActiveHyps.setForeground( Color.red );
//			} else {
//				lActiveHyps.setText( "CT " );
//				lActiveHyps.setForeground( Color.black );
//			}

			// - -  assignment-views  - - - - - -

			if ( ilp != null ) {
				final int t = sliderTime.getValue();
				if ( t == 0 ) {
					leftAssignmentViewer.display( null );
				} else {
					leftAssignmentViewer.display( ilp.getAllCompatibleRightAssignments( t - 1 ) );
				}
				if ( t == sliderTime.getMaximum() ) {
					rightAssignmentViewer.display( null );
				} else {
					rightAssignmentViewer.display( ilp.getAllCompatibleRightAssignments( t ) );
				}
			} else {
				leftAssignmentViewer.display( null );
				rightAssignmentViewer.display( null );
			}

			// - -  i see ? cells  - - - - - -
			updateNumCellsField();
		}

		// IF DETAILED DATA VIEW IS ACTIVE
		// ===============================
		if ( tabsViews.getComponent( tabsViews.getSelectedIndex() ).equals( panelDetailedDataView ) ) {
			updatePlotPanels();
		}
		setFocusToTimeSlider();

	}

	/**
	 * @see javax.swing.event.ChangeListener#stateChanged(javax.swing.event.ChangeEvent)
	 */
	@Override
	public void stateChanged( final ChangeEvent e ) {

		if ( e.getSource().equals( sliderGL ) ) {
			model.setCurrentGL( sliderGL.getValue(), sliderTime.getValue() );
		}

		if ( e.getSource().equals( sliderTime ) ) {
			updateNumCellsField();
		}

		if ( e.getSource().equals( sliderTrackingRange ) ) {
			if ( model.getCurrentGL().getIlp() != null ) {
				model.getCurrentGL().getIlp().ignoreBeyond( sliderTrackingRange.getUpperValue() );
			}
		}

		dataToDisplayChanged();
		this.repaint();
		focusOnSliderTime();
	}

	/**
	 *
	 */
	private void updateNumCellsField() {
		this.lblCurrentTime.setText( String.format( " t = %4d", sliderTime.getValue() ) );
		this.model.setCurrentGLF( sliderTime.getValue() );
		if ( model.getCurrentGL().getIlp() != null ) {
			final int rhs =
					model.getCurrentGL().getIlp().getSegmentsInFrameCountConstraintRHS(
							sliderTime.getValue() );
			if ( rhs == -1 ) {
				txtNumCells.setText( "?" );
			} else {
				txtNumCells.setText( "" + rhs );
			}
		}
	}

	/**
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	@Override
	public void actionPerformed( final ActionEvent e ) {

		if ( e.getSource().equals( menuProps ) ) {
			final DialogPropertiesEditor propsEditor =
					new DialogPropertiesEditor( this, MoMA.props );
			propsEditor.setVisible( true );
		}
		if ( e.getSource().equals( menuLoad ) ) {

			final MoMAGui self = this;

			final Thread t = new Thread( new Runnable() {

				@Override
				public void run() {
					GrowthLineTrackingILP ilp = model.getCurrentGL().getIlp();

					final File file = OsDependentFileChooser.showLoadFileChooser(
							self,
							MoMA.STATS_OUTPUT_PATH,
							"Choose tracking to load...",
							new ExtensionFileFilter( "timm", "Curated TIMM tracking" ) );
					System.out.println( "File to load tracking from: " + file.getAbsolutePath() );

					try {
						if ( file != null ) {

							// GL_OFFSET_BOTTOM adjustment if needed...

							if ( ilp == null ) {
								prepareOptimization();
								ilp = model.getCurrentGL().getIlp();
							}
							ilp.loadState( file );
						}
					} catch ( final IOException e1 ) {
						e1.printStackTrace();
					}
				}


			} );
			t.start();
		}
		if ( e.getSource().equals( menuSave ) ) {

			final GrowthLineTrackingILP ilp = model.getCurrentGL().getIlp();

			if ( ilp != null ) { // && ilp.getStatus() != GrowthLineTrackingILP.OPTIMIZATION_NEVER_PERFORMED
				final File file = OsDependentFileChooser.showSaveFileChooser(
						this,
						MoMA.STATS_OUTPUT_PATH,
						"Save current tracking to...",
						new ExtensionFileFilter( "timm", "Curated TIMM tracking" ) );
				System.out.println( "File to save tracking to: " + file.getAbsolutePath() );
				ilp.saveState( file );
			} else {
				JOptionPane.showMessageDialog(
						this,
						"Loaded data must be optimized before tracking can be saved!",
						"Error",
						JOptionPane.ERROR_MESSAGE );
			}
		}
		if ( e.getSource().equals( menuViewShowConsole ) ) {
			MoMA.instance.showConsoleWindow( !MoMA.instance.isConsoleVisible() );
			MoMA.getGuiFrame().setVisible( true );
		}
		if ( e.getSource().equals( menuShowImgTemp ) ) {
			new ImageJ();
			ImageJFunctions.show( MoMA.instance.getImgTemp(), "BG-subtracted data" );
		}
		if ( e.getSource().equals( menuShowImgRaw ) ) {
			new ImageJ();
			ImageJFunctions.show( MoMA.instance.getRawChannelImgs().get( 0 ), "raw data (ch.0)" );
		}
		if ( e.getSource().equals( menuSaveFG ) ) {
			final File file = OsDependentFileChooser.showSaveFileChooser(
					this,
					MoMA.DEFAULT_PATH,
					"Save Factor Graph...",
					new ExtensionFileFilter( new String[] { "txt", "TXT" }, "TXT-file" ) );

			if ( file != null ) {
				MoMA.DEFAULT_PATH = file.getParent();

				if ( model.getCurrentGL().getIlp() == null ) {
					System.out.println( "Generating ILP..." );
					model.getCurrentGL().generateILP(
							new DialogProgress( this, "Building tracking model...", ( model.getCurrentGL().size() - 1 ) * 2 ) );
				} else {
					System.out.println( "Using existing ILP (possibly containing user-defined ground-truth bits)..." );
				}
				System.out.println( "Saving ILP as FactorGraph..." );
				model.getCurrentGL().getIlp().exportFG_PAUL( file );
				System.out.println( "...done!" );
			}
		}
//		if ( e.getSource().equals( btnRedoAllHypotheses ) ) {
//
////			final int choiceAwesome = JOptionPane.showOptionDialog( this, "Do you want to reset to PMFRF segmentations?\n(Otherwise fast CT segments will be built.)", "PMFRF or CT?", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null );
//			final DialogResegmentSetup dSetup = new DialogResegmentSetup( this, true, true );
//			dSetup.ask();
//			if ( !dSetup.wasCanceled() ) {
//				final JSlider sliderGL = this.sliderGL;
//
//				final Thread t = new Thread( new Runnable() {
//
//					@Override
//					public void run() {
//						if ( dSetup.allFrames() ) {
//							for ( int i = sliderGL.getMinimum(); i <= sliderGL.getMaximum(); i++ ) {
//								sliderGL.setValue( i );
//								dataToDisplayChanged();
//								if ( dSetup.doPMFRF() ) {
//									activateAwesomeHypothesesForCurrentGL();
//								} else {
//									activateSimpleHypotheses();
//								}
//							}
//						} else {
//							if ( dSetup.doPMFRF() ) {
//								activateAwesomeHypothesesForCurrentGL();
//							} else {
//								activateSimpleHypotheses();
//							}
//						}
//						dataToDisplayChanged();
//					}
//				} );
//				t.start();
//			}
//		}
		if ( e.getSource().equals( bCheckBoxLineSet ) ) {
			final Thread t = new Thread(() -> {
				model.getCurrentGL().getIlp().autosave();

				setAllVariablesFixedWhereChecked();

				System.out.println( "Finding optimal result..." );
				model.getCurrentGL().runILP();
				System.out.println( "...done!" );

				sliderTime.requestFocus();
				dataToDisplayChanged();
			});
			t.start();
		}
		if ( e.getSource().equals( bCheckBoxLineReset ) ) {
			final Thread t = new Thread(() -> {
				model.getCurrentGL().getIlp().autosave();

				setAllVariablesFreeWhereChecked();

				System.out.println( "Finding optimal result..." );
				model.getCurrentGL().runILP();
				System.out.println( "...done!" );

				sliderTime.requestFocus();
				dataToDisplayChanged();
			});
			t.start();
		}
		if ( e.getSource().equals( bFreezeHistory ) ) {
			final Thread t = new Thread(() -> {
				final int t1 = sliderTime.getValue();
				if ( sliderTrackingRange.getUpperValue() < sliderTrackingRange.getMaximum() ) {
					final int extent =
							sliderTrackingRange.getUpperValue() - sliderTrackingRange.getValue();
					sliderTrackingRange.setUpperValue( t1 - 1 + extent );
					btnOptimizeMore.doClick();
				}
				sliderTrackingRange.setValue( t1 - 1 );
			});
			t.start();
		}
		if ( e.getSource().equals( btnRestart ) ) {
			final int choise =
					JOptionPane.showConfirmDialog(
							this,
							"Do you really want to restart the optimization?\nYou will loose all manual edits performed so far!",
							"Are you sure?",
							JOptionPane.YES_NO_OPTION );

			if ( choise == JOptionPane.OK_OPTION ) {
    			final Thread t = new Thread(() -> {
					model.getCurrentGL().getIlp().autosave();

					prepareOptimization();

					if ( !( sliderTrackingRange.getUpperValue() == sliderTrackingRange.getMaximum() ) ) {
						final int extent =
								sliderTrackingRange.getUpperValue() - sliderTrackingRange.getValue();
						sliderTrackingRange.setUpperValue(extent);
					}
					sliderTrackingRange.setValue( 0 );

					model.getCurrentGL().getIlp().freezeBefore( sliderTrackingRange.getValue() );
					if ( sliderTrackingRange.getUpperValue() < sliderTrackingRange.getMaximum() ) {
						// this is needed because of the duplication of the last time-point
						model.getCurrentGL().getIlp().ignoreBeyond( sliderTrackingRange.getUpperValue() );
					}

					System.out.println( "Finding optimal result..." );
					model.getCurrentGL().runILP();
					System.out.println( "...done!" );

					dataToDisplayChanged();
				});
    			t.start();
			}
		}
		if ( e.getSource().equals( btnOptimizeMore ) ) {
			final Thread t = new Thread(() -> {
				if ( model.getCurrentGL().getIlp() == null ) {
					prepareOptimization();
					sliderTrackingRange.setValue( 0 );
				}

				if ( sliderTime.getValue() > sliderTrackingRange.getUpperValue() ) {
					sliderTrackingRange.setUpperValue( sliderTime.getValue() );
				}
				if ( sliderTime.getValue() < sliderTrackingRange.getValue() ) {
					final int len =
							sliderTrackingRange.getUpperValue() - sliderTrackingRange.getValue();
					sliderTrackingRange.setValue( sliderTime.getValue() - len / 2 );
					sliderTrackingRange.setUpperValue( sliderTime.getValue() + len / 2 + len % 2 );
				}

				model.getCurrentGL().getIlp().freezeBefore( sliderTrackingRange.getValue() );
				if ( sliderTrackingRange.getUpperValue() < sliderTrackingRange.getMaximum() ) {
					// this is needed because of the duplication of the last time-point
					model.getCurrentGL().getIlp().ignoreBeyond( sliderTrackingRange.getUpperValue() );
				}

				System.out.println( "Finding optimal result..." );
				model.getCurrentGL().runILP();
				System.out.println( "...done!" );

				dataToDisplayChanged();
			});
			t.start();
		}
		if ( e.getSource().equals( btnExportHtml ) ) {
			final Thread t = new Thread(this::exportHtmlOverview);
			t.start();
		}
		if ( e.getSource().equals( btnExportData ) ) {
			final Thread t = new Thread(this::exportDataFiles);
			t.start();
		}
		if ( e.getSource().equals( viewSegmentsButton ) ) {
//			final GrowthLineTrackingILP ilp = model.getCurrentGL().getIlp();

//			final GrowthLineTrackingILP ilp = model.getCurrentGL().getIlp().getCurrentGLF();
//			Object var = model.getCurrentGL().getIlp().getCurrentGLF();
			AssignmentsAndHypotheses<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>, Hypothesis<Component<FloatType, ?>>> nodes = model.getCurrentGL().getIlp().nodes;
			GrowthLineFrame glf = model.getCurrentGLF();
			final int t = glf.getParent().getFrames().indexOf( glf );
			List<Hypothesis<Component<FloatType, ?>>> optimalSegs = glf.getParent().getIlp().getOptimalSegmentation(t);

			Plotting.drawComponentTree(model.getCurrentGLF().getComponentTree(), optimalSegs);
		}
		setFocusToTimeSlider();
	}

	private void setFocusToTimeSlider() {

		SwingUtilities.invokeLater(() -> sliderTime.requestFocusInWindow());
	}

	/**
	 * @return
	 */
	private void prepareOptimization() {
		System.out.println( "Filling in CT hypotheses where needed..." );
		int frameIndex = 0;
		for ( final GrowthLineFrame glf : model.getCurrentGL().getFrames() ) {
			if ( glf.getComponentTree() == null ) {
				glf.generateSimpleSegmentationHypotheses( MoMA.instance.getImgProbs(), frameIndex );
				frameIndex++;
			}
		}

		System.out.println( "Generating ILP..." );
		if ( MoMA.HEADLESS ) {
			model.getCurrentGL().generateILP( null );
		} else {
			model.getCurrentGL().generateILP(
					new DialogProgress( this, "Building tracking model...", ( model.getCurrentGL().size() - 1 ) * 2 ) );
		}
	}

	/**
	 * Depending on which checkboxes are checked, fix ALL respective
	 * segmentations and assignments to current ILP state.
	 */
	private void setAllVariablesFixedWhereChecked() {
		final GrowthLineTrackingILP ilp = model.getCurrentGL().getIlp();
		final int t = sliderTime.getValue();
		if ( ilp != null ) {
			if ( cbSegmentationOkLeft.isSelected() ) {
				ilp.fixSegmentationAsIs( t - 1 );
			}
			if ( cbAssignmentsOkLeft.isSelected() ) {
				ilp.fixAssignmentsAsAre( t - 1 );
			}
			if ( cbSegmentationOkCenter.isSelected() ) {
				ilp.fixSegmentationAsIs( t );
			}
			if ( cbAssignmentsOkRight.isSelected() ) {
				ilp.fixAssignmentsAsAre( t );
			}
			if ( cbSegmentationOkRight.isSelected() ) {
				ilp.fixSegmentationAsIs( t + 1 );
			}
		}
	}

	/**
	 * Depending on which checkboxes are UNchecked, free ALL respective
	 * segmentations and assignments if they are clamped to any value in the
	 * ILP.
	 */
	private void setAllVariablesFreeWhereChecked() {
		final GrowthLineTrackingILP ilp = model.getCurrentGL().getIlp();
		final int t = sliderTime.getValue();
		if ( ilp != null ) {
			if ( cbSegmentationOkLeft.isSelected() ) {
				ilp.removeAllSegmentConstraints( t - 1 );
			}
			if ( cbAssignmentsOkLeft.isSelected() ) {
				ilp.removeAllAssignmentConstraints( t - 1 );
			}
			if ( cbSegmentationOkCenter.isSelected() ) {
				ilp.removeAllSegmentConstraints( t );
			}
			if ( cbAssignmentsOkRight.isSelected() ) {
				ilp.removeAllAssignmentConstraints( t );
			}
			if ( cbSegmentationOkRight.isSelected() ) {
				ilp.removeAllSegmentConstraints( t + 1 );
			}
		}
	}

	/**
	 * Depending on which checkboxes are checked, fix ALL respective
	 * segmentations and assignments to current ILP state.
	 */
	protected void setAllVariablesFixedUpTo( final int t ) {
		final GrowthLineTrackingILP ilp = model.getCurrentGL().getIlp();
		if ( ilp != null ) {
			for ( int i = 0; i < t; i++ ) {
				ilp.freezeAssignmentsAsAre( i - 1 );
			}
		}
	}

	/**
	 *
	 */
	public void exportDataFiles() {
		if ( model.getCurrentGL().getIlp() == null ) {
			JOptionPane.showMessageDialog( this, "The current GL can only be exported after being tracked (optimized)!" );
			return;
		}

		final CellStatsExporter exporter = new CellStatsExporter( this );
		exporter.export();
	}

	/**
	 *
	 */
	public void exportHtmlOverview() {
		final MoMAGui self = this;

		if ( model.getCurrentGL().getIlp() == null ) {
			JOptionPane.showMessageDialog( this, "The current GL can only be exported after being tracked (optimized)!" );
			return;
		}

		boolean doExport = true;
		int startFrame = 1;
		int endFrame = sliderTime.getMaximum() + 1;

		File file = new File(MoMA.STATS_OUTPUT_PATH + "/index.html");

		if ( !MoMA.HEADLESS ) {
			final JFileChooser fc = new JFileChooser();
			fc.setSelectedFile( file );
			fc.addChoosableFileFilter( new ExtensionFileFilter( new String[] { "html" }, "HTML-file" ) );

			if ( fc.showSaveDialog( self ) == JFileChooser.APPROVE_OPTION ) {
				file = fc.getSelectedFile();
				if ( !file.getAbsolutePath().endsWith( ".html" ) && !file.getAbsolutePath().endsWith( ".htm" ) ) {
					file = new File( file.getAbsolutePath() + ".html" );
				}
				MoMA.STATS_OUTPUT_PATH = file.getParent();

				boolean done = false;
				while ( !done ) {
					try {
						final String str = ( String ) JOptionPane.showInputDialog( self, "First frame to be exported:", "Start at...", JOptionPane.QUESTION_MESSAGE, null, null, "" + startFrame );
						if ( str == null ) return; // User decided to hit cancel!
						startFrame = Integer.parseInt( str );
						done = true;
					} catch ( final NumberFormatException nfe ) {
						done = false;
					}
				}
				done = false;
				while ( !done ) {
					try {
						final String str = ( String ) JOptionPane.showInputDialog( self, "Last frame to be exported:", "End with...", JOptionPane.QUESTION_MESSAGE, null, null, "" + endFrame );
						if ( str == null ) return; // User decided to hit cancel!
						endFrame = Integer.parseInt( str );
						done = true;
					} catch ( final NumberFormatException nfe ) {
						done = false;
					}
				}
			} else {
				doExport = false;
			}
		}

		// ----------------------------------------------------------------------------------------------------
		if ( doExport ) {
			exportHtmlTrackingOverview( file, startFrame - 1, endFrame - 1 );
		}
		// ----------------------------------------------------------------------------------------------------

		if ( !MoMA.HEADLESS ) {
			dataToDisplayChanged();
		}
	}

	/**
	 * Goes over all glfs of the current gl and activates the simple, intensity
	 * + comp.tree hypotheses.
	 */
	private void activateSimpleHypotheses() {
		activateSimpleHypothesesForGL( model.getCurrentGL() );
	}

	private void activateSimpleHypothesesForGL( final GrowthLine gl ) {
		int frameIndex = 0;
		for ( final GrowthLineFrame glf : gl.getFrames() ) {
			System.out.print( "." );
			glf.generateSimpleSegmentationHypotheses( model.mm.getImgProbs(), frameIndex );
			frameIndex++;
		}
		System.out.println();
	}

	/*
	  Goes over all glfs of the current gl and activates the awesome,
	  RF-classified + paramaxflow hypotheses.
	 */
//	private void activateAwesomeHypothesesForCurrentGL() {
//		activateAwesomeHypothesesForGL( model.getCurrentGL() );
//	}

	/*
	  Goes over all glfs of the given gl and activates the awesome,
	  RF-classified + paramaxflow hypotheses.
	 */
//	private void activateAwesomeHypothesesForGL( final GrowthLine gl ) {
//		// Since I am gonna mix CT and PMFRF, I have to also ensure to have the CT ones available
//		if ( MoMA.SEGMENTATION_MIX_CT_INTO_PMFRF > 0.0001 ) {
//			activateSimpleHypothesesForGL( gl );
//		}
//
//		final int numProcessors = Prefs.getThreads();
//		final int numThreads = Math.min( model.getCurrentGL().getFrames().size(), numProcessors );
//		final Thread[] threads = new Thread[ numThreads ];
//
//		class ImageProcessingThread extends Thread {
//
//			final int numThread;
//			final int numThreads;
//
//			public ImageProcessingThread( final int numThread, final int numThreads ) {
//				this.numThread = numThread;
//				this.numThreads = numThreads;
//			}
//
//			@Override
//			public void run() {
//
//				for ( int i = numThread; i < model.getCurrentGL().getFrames().size(); i += numThreads ) {
//					gl.getFrames().get( i ).generateAwesomeSegmentationHypotheses( model.mm.getImgTemp() );
//				}
//			}
//		}
//
//		// start threads
//		for ( int i = 0; i < numThreads; i++ ) {
//			threads[ i ] = new ImageProcessingThread( i, numThreads );
//			threads[ i ].start();
//		}
//
//		// wait for all threads to terminate
//		for ( final Thread thread : threads ) {
//			try {
//				thread.join();
//			} catch ( final InterruptedException e ) {}
//		}
//
//		// OLD SINGLETHREADED VERSION
////		for ( final GrowthLineFrame glf : model.getCurrentGL().getFrames() ) {
////			System.out.println( ">>>>> Generating PMFRF hypotheses for GLF #" + glf.getTime() );
////			glf.generateAwesomeSegmentationHypotheses( model.mm.getImgTemp() );
////		}
////		System.out.print( "" );
//
//		// NEW SINGLETHREADED VERSION
////		for ( int i = this.sliderTime.getMinimum(); i <= this.sliderTime.getMaximum(); i++ ) {
////			sliderTime.setValue( i );
////			if ( model.getCurrentGLF().getAwesomeGapSeparationValues( null ) == null ) {
////				btnExchangeSegHyps.doClick();
////			}
////			dataToDisplayChanged();
////		}
//	}

	/**
	 * Exports current tracking solution as individual PNG images in the given
	 * folder.
	 *
	 * @param endFrame
	 * @param startFrame
	 *
	 */
	private void exportHtmlTrackingOverview( final File htmlFileToSaveTo, final int startFrame, final int endFrame ) {
		System.out.println( "Exporting html tracking overview..." );

		final String path = htmlFileToSaveTo.getParent();
		final String imgpath = path + "/imgs";

		final HtmlOverviewExporter exporter = new HtmlOverviewExporter( this, htmlFileToSaveTo, imgpath, startFrame, endFrame );
		exporter.run();

		System.out.println( "...done!" );

	}

	/**
	 * Requests the focus on the slider controlling the time (frame).
	 */
	public void focusOnSliderTime() {

		SwingUtilities.invokeLater(() -> sliderTime.requestFocus());
	}

	/**
	 * Checkbox getter to enable or disable autosave functionality.
	 *
	 * @return true if the corresponding CheckBox is checked.
	 */
	public boolean isAutosaveRequested() {
		return cbAutosave.isSelected();
	}
}
