package com.jug.gui.assignmentview;

import com.jug.MoMA;
import com.jug.gui.MoMAGui;
import com.jug.lp.*;
import com.jug.util.OSValidator;
import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.type.numeric.real.FloatType;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.GeneralPath;
import java.util.*;

/**
 * @author jug
 */
public class AssignmentsEditorCanvasView extends JComponent implements MouseInputListener, MouseWheelListener {

    /**
     *
     */
    private static final int DISPLAY_COSTS_ABSOLUTE_X = 10;

    /**
     *
     */
    private static final int LINEHEIGHT_DISPLAY_COSTS = 20;

    /**
     *
     */
    private static final int OFFSET_DISPLAY_COSTS = -15;
    // -------------------------------------------------------------------------------------
    // statics
    // -------------------------------------------------------------------------------------
    private static final long serialVersionUID = -2920396224787446598L;
    private static int HEIGHT_OFFSET;
    private static int ASSIGNMENT_DISPLAY_OFFSET;
    // -------------------------------------------------------------------------------------
    // fields
    // -------------------------------------------------------------------------------------
    private final int width;
    private final Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> filteredAssignments;
    private final float defaultFilterMinCost;
    private final float defaultFilterMaxCost;
    private boolean doFilterDataByCost;
    private float filterMinCost = -100f;
    private float filterMaxCost = 100f;
    private boolean doFilterDataByIdentity = false;
    private boolean doAddToFilter = false; // if 'true' all assignments at the mouse location will be added to the filter next time repaint is called...
    private HashMap<Hypothesis<Component<FloatType, ?>>, Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>>> data;

    private boolean isMouseOver = false; /* indicates if the mouse is inside this AssignmentsEditorCanvasView instance */
    private int mousePosX;
    private int mousePosY;
    private int currentCostLine;

    private boolean isDragging = false;
    private int dragX;
    private int dragY;
    private int dragInitiatingMouseButton = 0;
    private float dragStepWeight = 0;

    private boolean doAddAsGroundTruth;
    private boolean doAddAsGroundUntruth;

    private MoMAGui gui;

    private Color strokeColor;

    private ArrayList<AssignmentView> assignmentViews = new ArrayList<>();

    // -------------------------------------------------------------------------------------
    // construction
    // -------------------------------------------------------------------------------------
    public AssignmentsEditorCanvasView(final int height, final MoMAGui callbackGui) {
        this(height, -GrowthLineTrackingILP.CUTOFF_COST, GrowthLineTrackingILP.CUTOFF_COST);
        this.doFilterDataByCost = false;
        this.gui = callbackGui;
    }

    /**
     * @param height
     * @param filterMinCost
     * @param filterMaxCost
     */
    private AssignmentsEditorCanvasView(final int height, final float filterMinCost, final float filterMaxCost) {
        if (OSValidator.isUnix()) {
            HEIGHT_OFFSET = -10;
            ASSIGNMENT_DISPLAY_OFFSET = -7;
        }
        if (OSValidator.isMac()) {
            HEIGHT_OFFSET = -60;
            ASSIGNMENT_DISPLAY_OFFSET = -9;
        }
        if (OSValidator.isWindows()) {
            HEIGHT_OFFSET = -10;
            ASSIGNMENT_DISPLAY_OFFSET = -7;
        }

        this.width = 90;
        this.setPreferredSize(new Dimension(width, height + HEIGHT_OFFSET));

        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.addMouseWheelListener(this);

        this.defaultFilterMinCost = filterMinCost;
        this.defaultFilterMaxCost = filterMaxCost;

        this.doFilterDataByCost = true;
        this.setCostFilterMin(filterMinCost);
        this.setCostFilterMax(filterMaxCost);

        this.filteredAssignments = new HashSet<>();
    }

    // -------------------------------------------------------------------------------------
    // getters and setters
    // -------------------------------------------------------------------------------------

    /**
     * @return the filterMinCost
     */
    public float getCostFilterMin() {
        return filterMinCost;
    }

    /**
     * @param filterMinCost the filterMinCost to set
     */
    public void setCostFilterMin(final float filterMinCost) {
        this.filterMinCost = filterMinCost;
    }

    /**
     * @return the filterMaxCost
     */
    public float getCostFilterMax() {
        return filterMaxCost;
    }

    /**
     * @param filterMaxCost the filterMaxCost to set
     */
    public void setCostFilterMax(final float filterMaxCost) {
        this.filterMaxCost = filterMaxCost;
    }

    // -------------------------------------------------------------------------------------
    // methods
    // -------------------------------------------------------------------------------------

    /**
     * Turns of filtering and shows all the given data.
     *
     * @param data a <code>HashMap</code> containing pairs of segmentation
     *             hypothesis at some time-point t and assignments towards t+1.
     */
    public void display(final HashMap<Hypothesis<Component<FloatType, ?>>, Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>>> data) {
        doFilterDataByCost = false;
        setData(data);

        this.repaint();
    }

    /**
     * In this overwritten method we added filtering and calling
     * <code>drawAssignment(...)</code>.
     *
     * @see java.awt.Component#paint(java.awt.Graphics)
     */
    @Override
    public void paint(final Graphics g) {
        if (data == null) return;

        drawGlOffsetTop((Graphics2D) g);

        for (AssignmentView assView : assignmentViews) {
            assView.draw((Graphics2D) g);
        }

        if (!this.isDragging) {
            drawCostTooltip((Graphics2D) g); /* update cost tool-tip only, if we are not dragging; or else we cannot see cost-range numbers at top of canvas */
        }

        if (this.isDragging) {
            g.setColor(Color.GREEN.darker());
            g.drawString(String.format("min: %.4f", this.getCostFilterMin()), 0, 10);
            g.setColor(Color.RED.darker());
            g.drawString(String.format("max: %.4f", this.getCostFilterMax()), 0, 30);
            g.setColor(Color.GRAY);
            g.drawString(String.format("dlta %.4f", this.dragStepWeight), 0, 50);
        }

        // in case we where adding assignments - stop now!
        this.doAddToFilter = false;
    }

    /**
     * Draw the top offset and cross-over range to the assignment view.
     *
     * @param g
     */
    private void drawGlOffsetTop(final Graphics2D g) {
        double componentExitRange = MoMA.COMPONENT_EXIT_RANGE / 2.0f; // defines the range, over which the cost increases.

        final int x1 = 0;
        strokeColor = Color.RED.darker();
        BasicStroke dashedStroke = new BasicStroke(1, 1, 1, 1, new float[]{1.0f, 2.0f}, 1.0f);
        BasicStroke solidStroke = new BasicStroke(1);

        GeneralPath polygon = new GeneralPath();
        polygon.moveTo(x1, MoMA.GL_OFFSET_TOP);
        polygon.lineTo(this.width, MoMA.GL_OFFSET_TOP);
        polygon.closePath();
        g.setPaint(strokeColor);
        g.setStroke(solidStroke);
        g.draw(polygon);

        polygon = new GeneralPath();
        polygon.moveTo(x1, MoMA.GL_OFFSET_TOP - componentExitRange);
        polygon.lineTo(this.width, MoMA.GL_OFFSET_TOP - componentExitRange);
        polygon.closePath();
        g.setPaint(strokeColor);
        g.setStroke(dashedStroke);
        g.draw(polygon);

        polygon = new GeneralPath();
        polygon.moveTo(x1, MoMA.GL_OFFSET_TOP + componentExitRange);
        polygon.lineTo(this.width, MoMA.GL_OFFSET_TOP + componentExitRange);
        polygon.closePath();
        g.setPaint(strokeColor);
        g.setStroke(dashedStroke);
        g.draw(polygon);
    }

    private void drawCostTooltip(Graphics2D g2){
        g2.setPaint(new Color(0f, 0f,0f, 1.0f));
        currentCostLine = 0;
//        Font currentFont = g2.getFont();
        for(AssignmentView assView : hoveredAssignments){
            String valueString = assView.getCostTooltipString();
            if (assView.equals(selectedAssignment)) valueString = "* " + assView.getCostTooltipString();
//            if (assView.equals(selectedAssignment)) g2.setFont(new Font("default", Font.BOLD, currentFont.getSize()));
//            else g2.setFont(currentFont);

            g2.drawString(
                    valueString,
                    DISPLAY_COSTS_ABSOLUTE_X,
                    this.mousePosY + OFFSET_DISPLAY_COSTS - this.currentCostLine * LINEHEIGHT_DISPLAY_COSTS);
            currentCostLine++;
        }
    }

    /**
     * Set the assignment data that will be displayed in this instance of {@link AssignmentsEditorCanvasView}.
     *
     * @param data: assignment data to display
     */
    public void setData(final HashMap<Hypothesis<Component<FloatType, ?>>, Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>>> data) {
        this.data = data;
        initializeAssignmentViews();
        this.repaint();
    }

    private void initializeAssignmentViews() {
        assignmentViews.clear();
        for (final Set<AbstractAssignment<Hypothesis<Component<FloatType, ?>>>> setOfAssignments : data.values()) {
            for (final AbstractAssignment<Hypothesis<Component<FloatType, ?>>> assignment : setOfAssignments) {
                if (assignment.getType() == GrowthLineTrackingILP.ASSIGNMENT_MAPPING) {
                    assignmentViews.add(new MappingAssignmentView((MappingAssignment) assignment, width, ASSIGNMENT_DISPLAY_OFFSET));
                }
                else if (assignment.getType() == GrowthLineTrackingILP.ASSIGNMENT_DIVISION) {
                    assignmentViews.add(new DivisionAssignmentView((DivisionAssignment) assignment, width, ASSIGNMENT_DISPLAY_OFFSET));
                }
                else if (assignment.getType() == GrowthLineTrackingILP.ASSIGNMENT_EXIT) {
                    assignmentViews.add(new ExitAssignmentView((ExitAssignment) assignment, width, ASSIGNMENT_DISPLAY_OFFSET));
                }
                else if (assignment.getType() == GrowthLineTrackingILP.ASSIGNMENT_LYSIS) {
                    assignmentViews.add(new LysisAssignmentView((LysisAssignment) assignment, width, ASSIGNMENT_DISPLAY_OFFSET));
                }
            }
        }
        sortAssignmentViews(assignmentViews);
    }

    /**
     * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseClicked(final MouseEvent e) {
        // unmod. float_click  --  show filter window thing
        if (e.getClickCount() == 2 && !e.isShiftDown()) {
            new DialogAssignmentViewSetup(this, e.getXOnScreen(), e.getYOnScreen()).setVisible(true);
        }

        if (selectedAssignment == null) { /* currently there is no assignment hovered */
            return;
        }
        // unmodified click -- include assignment
        // ctrl click       -- avoid assignment
        if (!isDragging) {
            if (e.getClickCount() == 1 && !e.isAltDown() && !e.isShiftDown() && e.getButton() == MouseEvent.BUTTON1) {
                if (e.isControlDown()) {
                    this.doAddAsGroundUntruth = true;
                    selectedAssignment.addAsGroundUntruth();
                } else {
                    selectedAssignment.addAsGroundTruth();
                    this.doAddAsGroundTruth = true;
                }
            }
        }

        repaint();
    }

    /**
     * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
     */
    @Override
    public void mousePressed(final MouseEvent e) {
        System.out.println("shift:    " + e.isShiftDown());
        System.out.println("ctrl:     " + e.isControlDown());
        System.out.println("alt:      " + e.isAltDown());
        System.out.println("click-c.: " + e.getClickCount());
        System.out.println("button:   " + e.getButton());

        // shift-click   --   hide assignments
        if (!e.isAltDown() && !e.isControlDown() && e.isShiftDown() && e.getButton() == MouseEvent.BUTTON1) {
            System.out.println("Filter!");
            this.doFilterDataByIdentity = true;
            this.doAddToFilter = true; // when repainting component next time...
            for(AssignmentView assignmentView : hoveredAssignments){
                assignmentView.hide();
            }
        }

        // right_click or shift-right_click  --  clear list of hidden assignments
        if (!e.isAltDown() && !e.isControlDown() && e.getButton() == MouseEvent.BUTTON3) {
            this.doFilterDataByIdentity = false;
            this.filteredAssignments.clear();
            for(AssignmentView assignmentView : assignmentViews){
                assignmentView.unhide();
            }
            resetCostFilterValues();
        }

        // I repaint before detecting dragging (since this can interfere with filtering)
        repaint();

        // plain click to initiate dragging
        if (!e.isShiftDown() && !e.isControlDown() && !e.isAltDown() &&
                (e.getButton() == MouseEvent.BUTTON1 || e.getButton() == MouseEvent.BUTTON3)) {
            System.out.println("Dragging!");
            this.isDragging = true;
            this.dragX = e.getX();
            this.dragY = e.getY();
            this.dragInitiatingMouseButton = e.getButton();
        }
    }

    private void resetCostFilterValues() {
        this.setCostFilterMin(defaultFilterMinCost);
        this.setCostFilterMax(defaultFilterMaxCost);
    }

    /**
     * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseReleased(final MouseEvent e) {
        this.isDragging = false;
        repaint();
    }

    /**
     * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseEntered(final MouseEvent e) {
        this.isMouseOver = true;
    }

    /**
     * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseExited(final MouseEvent e) {
        this.isMouseOver = false;
        clearHoveredAssignments();
        repaint();
    }

    /**
     * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseDragged(final MouseEvent e) {
        this.doFilterDataByCost = true;

        final float minstep = 0.01f;
        final float xsensitivity = 30.0f;
        final int dX = e.getX() - this.dragX;
        final int dY = this.dragY - e.getY();

        final float fac = (float) Math.pow(2, Math.abs((xsensitivity + dX) / xsensitivity));
        if (dX > 0) {
            this.dragStepWeight = minstep * fac;
        } else {
            this.dragStepWeight = minstep / fac;
        }

        if (this.dragInitiatingMouseButton == MouseEvent.BUTTON1) {
            System.out.println(" b1");
            this.setCostFilterMax(this.getCostFilterMax() + dY * this.dragStepWeight);
        }
        if (this.dragInitiatingMouseButton == MouseEvent.BUTTON3) {
            System.out.println(" b3");
            this.setCostFilterMin(this.getCostFilterMin() + dY * this.dragStepWeight);
        }

        fiterShownAssignmentsByCost();

        this.dragY = e.getY();
        repaint();
    }

    void fiterShownAssignmentsByCost(){
        float currentMinCost = this.getCostFilterMin();
        float currentMaxCost = this.getCostFilterMax();
        for(AssignmentView assView : assignmentViews){
            if(assView.getCost() < currentMinCost || assView.getCost() > currentMaxCost){
                assView.hide();
            }
            else{
                assView.unhide();
            }
        }
    }

    ArrayList<AssignmentView> hoveredAssignments = new ArrayList<>();
    int selectedAssignmentIndex = 0;
    AssignmentView selectedAssignment;

    /**
     * @see java.awt.event.MouseMotionListener#mouseMoved(java.awt.event.MouseEvent)
     */
    @Override
    public void mouseMoved(final MouseEvent e) {
        this.mousePosX = e.getX();
        this.mousePosY = e.getY();
        updateHoveredAssignments();
        this.doAddAsGroundTruth = false;
        this.doAddAsGroundUntruth = false;
        this.repaint();
    }

    private void clearHoveredAssignments() {
        if(selectedAssignment != null) selectedAssignment.setIsSelected(false);
        selectedAssignment = null;
        hoveredAssignments = new ArrayList<>();

    }

    private void updateHoveredAssignments() {
        ArrayList<AssignmentView> updatedHoveredAssignments = getHoveredAssignmentViews(this.mousePosX, this.mousePosY);

        sortAssignmentViews(updatedHoveredAssignments);

        if(updatedHoveredAssignments.isEmpty()){
            resetSelectedAssignments();
            clearHoveredAssignments();
        }
        else if(!updatedHoveredAssignments.equals(hoveredAssignments)){
            resetSelectedAssignments();
            hoveredAssignments = updatedHoveredAssignments;
            selectedAssignmentIndex = getIndexOfOptimalAssignmentIfAvailable(hoveredAssignments);
            selectedAssignment = hoveredAssignments.get(selectedAssignmentIndex);
            selectedAssignment.setIsSelected(true);
        }
    }

    private int getIndexOfOptimalAssignmentIfAvailable(ArrayList<AssignmentView> assignmentViews){
        AssignmentView selectedHypothesis = assignmentViews.stream().filter(assView -> assView.isGroundTruth() || assView.isChosen())
                .findFirst()
                .orElse(null);
        if (selectedHypothesis != null) { /* there is an optimal assignment at the hover position; get it */
            return assignmentViews.indexOf(selectedHypothesis); /* set index to optimal assignment at that position */
        } else {
            AssignmentView assignmentViewWithMinimalCost = assignmentViews.stream().min(Comparator.comparingDouble(AssignmentView::getCost)).get();
            return assignmentViews.indexOf(assignmentViewWithMinimalCost);  /* there is no optimal assignment at the hover position; return index of assignmentView with minimal cost */
        }
    }

    private void sortAssignmentViews(ArrayList<AssignmentView> assignmentViews){
        assignmentViews.sort(Comparator.comparingDouble(AssignmentView::getCost));
        Collections.reverse(assignmentViews);
    }

    private ArrayList<AssignmentView> getHoveredAssignmentViews(int mousePosX, int mousePosY){
        ArrayList<AssignmentView> hoveredAssignments = new ArrayList<>();
        for(AssignmentView assView : assignmentViews){
            if(assView.isHovered(mousePosX, mousePosY)){
                hoveredAssignments.add(assView);
            }
        }
        return hoveredAssignments;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        resetSelectedAssignments();
        int increment = -e.getWheelRotation();

        if (selectedAssignmentIndex + increment >= hoveredAssignments.size()) {
            selectedAssignmentIndex = 0;
        } else if (selectedAssignmentIndex + increment < 0) {
            selectedAssignmentIndex = hoveredAssignments.size() - 1;
        } else {
            selectedAssignmentIndex += increment;
        }
        selectedAssignment = hoveredAssignments.get(selectedAssignmentIndex);
        selectedAssignment.setIsSelected(true);
        repaint();
    }

    void resetSelectedAssignments(){
        for(AssignmentView assView : assignmentViews){
            assView.setIsSelected(false);
        }
    }
}
