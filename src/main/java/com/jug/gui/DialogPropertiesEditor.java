/**
 *
 */
package com.jug.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Properties;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import com.jug.MoMA;
import com.l2fprod.common.propertysheet.DefaultProperty;
import com.l2fprod.common.propertysheet.Property;
import com.l2fprod.common.propertysheet.PropertySheet;
import com.l2fprod.common.propertysheet.PropertySheetPanel;

/**
 * @author jug
 */
public class DialogPropertiesEditor extends JDialog implements ActionListener {

	private static final long serialVersionUID = -5529104109524798394L;
	protected static final PropEditedListener propEditListener = new PropEditedListener();

	protected static class PropEditedListener implements PropertyChangeListener {

		@Override
		public void propertyChange( final PropertyChangeEvent evt ) {
			final String sourceName = ( ( Property ) evt.getSource() ).getName();

			try {
                switch (sourceName) {
                    case "GUROBI_TIME_LIMIT":
                        MoMA.GUROBI_TIME_LIMIT =
                                Double.parseDouble(evt.getNewValue().toString());
                        MoMA.props.setProperty(
                                "GUROBI_TIME_LIMIT",
                                "" + MoMA.GUROBI_TIME_LIMIT);
                        break;
                    case "GUROBI_MAX_OPTIMALITY_GAP":
                        MoMA.GUROBI_MAX_OPTIMALITY_GAP =
                                Double.parseDouble(evt.getNewValue().toString());
                        MoMA.props.setProperty(
                                "GUROBI_MAX_OPTIMALITY_GAP",
                                "" + MoMA.GUROBI_MAX_OPTIMALITY_GAP);
                        break;
                    case "GL_OFFSET_TOP": {
                        MoMA.GL_OFFSET_TOP =
                                Integer.parseInt(evt.getNewValue().toString());
                        MoMA.props.setProperty(
                                "GL_OFFSET_TOP",
                                "" + MoMA.GL_OFFSET_TOP);
                        final Thread t = new Thread(() -> {
                            MoMA.instance.restartFromGLSegmentation();
                            MoMA.getGui().dataToDisplayChanged();
                        });
                        t.start();
                        break;
                    }
                    case "GL_OFFSET_BOTTOM": {
                        MoMA.GL_OFFSET_BOTTOM =
                                Integer.parseInt(evt.getNewValue().toString());
                        MoMA.props.setProperty(
                                "GL_OFFSET_BOTTOM",
                                "" + MoMA.GL_OFFSET_BOTTOM);
                        final Thread t = new Thread(() -> {
                            MoMA.instance.restartFromGLSegmentation();
                            MoMA.getGui().dataToDisplayChanged();
                        });
                        t.start();
                        break;
                    }
                    default:
                        JOptionPane.showMessageDialog(
                                MoMA.getGui(),
                                "Value not changed - NOT YET IMPLEMENTED!",
                                "Warning",
                                JOptionPane.WARNING_MESSAGE);
                        break;
                }
			} catch ( final NumberFormatException e ) {
				JOptionPane.showMessageDialog(
						MoMA.getGui(),
						"Illegal value entered -- value not changed!",
						"Error",
						JOptionPane.ERROR_MESSAGE );
			}
		}

	}

	private static class PropFactory {

		private static String BGREM = "Background removal";
		private static String GL = "GrowthLine props";
		private static String TRA = "Tracking props";
		private static String SEG = "Segmentation props";
		private static String GRB = "GUROBI props";

		public static Property buildFor(final String key, final Object value) {
			final DefaultProperty property = new DefaultProperty();
			property.setDisplayName( key );
			property.setName( key );
			property.setValue( value.toString() );
			property.setType( String.class );
			property.addPropertyChangeListener( propEditListener );

            switch (key) {
                case "BGREM_TEMPLATE_XMIN":
                    property.setCategory(BGREM);
                    property.setShortDescription(key);
                    break;
                case "BGREM_TEMPLATE_XMAX":
                    property.setCategory(BGREM);
                    property.setShortDescription(key);
                    break;
                case "BGREM_X_OFFSET":
                    property.setCategory(BGREM);
                    property.setShortDescription(key);
                    break;
                case "GL_WIDTH_IN_PIXELS":
                    property.setCategory(GL);
                    property.setShortDescription(key);
                    break;
                case "GL_FLUORESCENCE_COLLECTION_WIDTH_IN_PIXELS":
                    property.setCategory(GL);
                    property.setShortDescription(key);
                    break;
                case "GL_OFFSET_BOTTOM":
                    property.setCategory(GL);
                    property.setShortDescription(key);
                    break;
                case "GL_OFFSET_TOP":
                    property.setCategory(GL);
                    property.setShortDescription(key);
                    break;
                case "GL_OFFSET_LATERAL":
                    property.setCategory(GL);
                    property.setShortDescription(key);
                    break;
                case "MOTHER_CELL_BOTTOM_TRICK_MAX_PIXELS":
                    property.setCategory(GL);
                    property.setShortDescription(key);
                    break;
                case "MIN_CELL_LENGTH":
                    property.setCategory(TRA);
                    property.setShortDescription(key);
                    break;
                case "MIN_GAP_CONTRAST":
                    property.setCategory(GL);
                    property.setShortDescription(key);
                    break;
                case "SIGMA_PRE_SEGMENTATION_X":
                    property.setCategory(SEG);
                    property.setShortDescription(key);
                    break;
                case "SIGMA_PRE_SEGMENTATION_Y":
                    property.setCategory(SEG);
                    property.setShortDescription(key);
                    break;
                case "SIGMA_GL_DETECTION_X":
                    property.setCategory(SEG);
                    property.setShortDescription(key);
                    break;
                case "SIGMA_GL_DETECTION_Y":
                    property.setCategory(SEG);
                    property.setShortDescription(key);
                    break;
                case "SEGMENTATION_MIX_CT_INTO_PMFRF":
                    property.setCategory(SEG);
                    property.setShortDescription(key);
                    property.setEditable(false);
                    break;
                case "SEGMENTATION_CLASSIFIER_MODEL_FILE":
                    property.setCategory(SEG);
                    property.setShortDescription(key);
                    property.setEditable(false);
                    break;
                case "CELLSIZE_CLASSIFIER_MODEL_FILE":
                    property.setCategory(SEG);
                    property.setShortDescription(key);
                    property.setEditable(false);
                    break;
                case "DEFAULT_PATH":
                    property.setShortDescription(key);
                    break;
                case "GUROBI_TIME_LIMIT":
                    property.setCategory(GRB);
                    property.setShortDescription(key);
                    break;
                case "GUROBI_MAX_OPTIMALITY_GAP":
                    property.setCategory(GRB);
                    property.setShortDescription(key);
                    break;
                default:
                    // ALL OTHERS ARE ADDED HERE
                    property.setShortDescription(key);
                    property.setEditable(false);
                    break;
            }
			return property;
		}
	}


	private JButton bClose;
	private final Properties props;

	public DialogPropertiesEditor( final Component parent, final Properties props ) {
		super( SwingUtilities.windowForComponent( parent ), "TIMM Properties Editor" );
		this.dialogInit();
		this.setModal( true );

		final int width = 800;
		final int height = 400;

		final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		final int screenWidth = ( int ) screenSize.getWidth();
		final int screenHeight = ( int ) screenSize.getHeight();
		this.setBounds( ( screenWidth - width ) / 2, ( screenHeight - height ) / 2, width, height );

		this.props = props;

		buildGui();
		setKeySetup();
	}

	private void buildGui() {
		this.rootPane.setLayout( new BorderLayout() );

		final PropertySheetPanel sheet = new PropertySheetPanel();
		sheet.setMode( PropertySheet.VIEW_AS_CATEGORIES );
		sheet.setDescriptionVisible( false );
		sheet.setSortingCategories( false );
		sheet.setSortingProperties( false );
		sheet.setRestoreToggleStates( false );
		for ( final String key : this.props.stringPropertyNames() ) {
			sheet.addProperty( PropFactory.buildFor( key, props.getProperty( key ) ) );
		}
//		sheet.setEditorFactory( PropertyEditorRegistry.Instance );

		bClose = new JButton( "Close" );
		bClose.addActionListener( this );
		this.rootPane.setDefaultButton( bClose );
		final JPanel horizontalHelper = new JPanel( new FlowLayout( FlowLayout.CENTER, 5, 0 ) );
		horizontalHelper.add( bClose );

		this.rootPane.add( sheet, BorderLayout.CENTER );
		this.rootPane.add( horizontalHelper, BorderLayout.SOUTH );
	}

	private void setKeySetup() {
		this.rootPane.getInputMap( JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( "ESCAPE" ), "closeAction" );

		this.rootPane.getActionMap().put( "closeAction", new AbstractAction() {

			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed( final ActionEvent e ) {
				setVisible( false );
				dispose();
			}
		} );

	}

	/**
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	@Override
	public void actionPerformed( final ActionEvent e ) {
		if ( e.getSource().equals( bClose ) ) {
			this.setVisible( false );
			this.dispose();
		}
	}
}
