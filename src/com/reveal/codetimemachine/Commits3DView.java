package com.reveal.codetimemachine;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.reveal.metrics.Metrics;
import com.reveal.metrics.MetricCalculationResults;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.util.*;

public class Commits3DView extends JComponent implements ComponentListener
{
    ///////// ++ Constant ++ /////////
    ///////// -- Constant -- /////////

    ///////// ++ UI ++ /////////

    Point centerOfThisComponent;
    ///////// -- UI -- /////////

    String debuggingText = "";

    //////// ++ Timer and Timing
    Timer playing3DAnimationTimer=null, mouseHovredItemTimer=null;
    final int TICK_INTERVAL_MS = 50;
    ///////
    final Color DUMMY_COLOR = Color.black;
    final Color ACTIVE_WINDOW_COLOR = Color.RED;

    ///////// ++ UI: 3D Prespective Variables ++ /////////
    final float LAYER_DISTANCE = 0.2f;
    final float LAYERS_DEPTH_ERROR_THRESHOLD = LAYER_DISTANCE/10;
    float maxVisibleDepth = 2f;
    final float MIN_VISIBLE_DEPTH = -LAYER_DISTANCE;
    final float MAX_VISIBLE_DEPTH_CHANGE_VALUE = 0.3f;
    final float EPSILON = 0.01f;
    Point startPointOfTimeLine = new Point(0,0), trianglePoint = new Point(0,0);
    Point startPointOfChartTimeLine = new Point(0,0);

    Point currentMousePoint = new Point(-100,-100);
    Map<String, Color> authorsColor = null;
    boolean isAuthorsColorMode = false;
    /////
    int topLayerIndex=0, targetLayerIndex=0 /*if equals to topLayerIndex it means no animation is running*/;
    float topLayerOffset;
    Dimension topIdealLayerDimention = new Dimension(0,0);
    Point topIdealLayerCenterPos = new Point(0,0);
    final int INVALID = -1;
    int lastBorderHighlighted_VirtualWindowIndex =-1, currentMouseHoveredIndex =INVALID;
    ///////// ++ UI
    final Color MOUSE_HOVERED_COLOR = Color.ORANGE;
    final boolean COLORFUL = false;
    final int TOP_BAR_HEIGHT = 25;
    final int VIRTUAL_WINDOW_BORDER_TICKNESS = 1;
    final int TIME_LINE_WIDTH = 3;
    ////////

    TTMSingleFileView TTMWindow = null;
    Project project;
    CustomEditorTextField mainEditorWindow = null;
    ArrayList<CommitWrapper> commitList = null;
    ArrayList<MetricCalculationResults> metricResultsList = null;
    VirtualEditorWindow[] virtualEditorWindows = null;
    VirtualFile virtualFile;

    JButton updateActiveFileToThisCommitBtn = null, updateProjectToThisCommitBtn = null;

    Metrics.Types currentMetric = null;



    public Commits3DView( Project project, VirtualFile virtualFile, ArrayList<CommitWrapper> commitList, ArrayList<MetricCalculationResults> metricResultsList, TTMSingleFileView TTMWindow)
    {
        super();

        this.TTMWindow = TTMWindow;
        this.project = project;
        this.virtualFile = virtualFile;
        this.commitList = commitList;
        this.currentMetric = Metrics.Types.NONE;
        this.metricResultsList = metricResultsList;

        this.setLayout(null);
        this.addComponentListener(this); // Check class definition as : ".. implements ComponentListener"
        if (CommonValues.IS_UI_IN_DEBUGGING_MODE)   this.setBackground(Color.ORANGE);
        this.setOpaque(true);

        preCalculateAuthorsColor();
        setupUI_mainEditorWindow();
        setupUI_buttons();
        setupUI_virtualWindows();
        initialVirtualWindowsVisualizations(); // initial 3D Variables

        componentResized(null); //to "updateTopIdealLayerBoundary()" and then "updateEverythingAfterComponentResize()"

        playing3DAnimationTimer = new Timer(TICK_INTERVAL_MS, new ActionListener(){
            public void actionPerformed(ActionEvent e)
            {
                tick(TICK_INTERVAL_MS/1000.f);
                repaint();
            }
        });

        mouseHovredItemTimer = new Timer(TICK_INTERVAL_MS,  new ActionListener(){
            public void actionPerformed(ActionEvent e)
            {
                updateHoveredWindow();
            }
        });
        mouseHovredItemTimer.start();

        addMouseWheelListener();
        addMouseMotionListener();
        addMouseListener();
    }

    private void preCalculateAuthorsColor()
    {
        authorsColor = new HashMap<String, Color>();
        Color c;
        Random rand = new Random();
        String s;
        for(int i=0; i<commitList.size(); i++)
        {
            s = commitList.get(i).getAuthor();
            if(authorsColor.containsKey(s)==true) continue;

            float r = rand.nextFloat();
            float g = rand.nextFloat();
            float b = rand.nextFloat();
            c = new Color(r,g,b);

            authorsColor.put(s, c);
        }
    }

    public void toggleAuthorsColorMode()
    {
        isAuthorsColorMode = !isAuthorsColorMode;

        if(isAuthorsColorMode==true)
        {
            for(int i=0; i<commitList.size(); i++)
                virtualEditorWindows[i].setTemporaryHighlightTopBar(true, authorsColor.get(commitList.get(i).getAuthor()));
        }
        else
        {
            for(int i=0; i<commitList.size(); i++)
                virtualEditorWindows[i].setTemporaryHighlightTopBar(false, DUMMY_COLOR);
        }
        repaint();
    }

    private void setupUI_buttons()
    {
        setupUI_buttons_updateActiveFile();
        setupUI_buttons_updateProjectFile();
    }

    private void setupUI_buttons_updateActiveFile()
    {
        ImageIcon icon = new ImageIcon(getClass().getResource("/images/travelTime.png"));
        updateActiveFileToThisCommitBtn = new JButton("Put File to This Time",icon);
        updateActiveFileToThisCommitBtn.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                final Runnable readRunner = new Runnable() {
                    @Override
                    public void run() {
                        Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
                        document.setText(commitList.get(TTMWindow.activeCommit_cIndex).getFileContent());
                    }
                };
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
                            @Override
                            public void run() {
                                ApplicationManager.getApplication().runWriteAction(readRunner);
                            }
                        }, "Update__ActiveFile__ToThisCommitBtn", null);
                    }
                });

                CodeTimeMachineAction.toolWindow.hide(null);
            }
        });
        updateActiveFileToThisCommitBtn.setFocusable(false);
        updateActiveFileToThisCommitBtn.setFocusPainted(false);
        updateActiveFileToThisCommitBtn.setForeground(Color.GREEN);
        this.add(updateActiveFileToThisCommitBtn); // As there's no layout, we should set Bound it. we'll do this in "ComponentResized()" event
    }

    private void setupUI_buttons_updateProjectFile()
    {
        ImageIcon icon = new ImageIcon(getClass().getResource("/images/travelTimeProject.png"));
        updateProjectToThisCommitBtn = new JButton("Put Project to This Time",icon);
        updateProjectToThisCommitBtn.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                GitHelper instance = GitHelper.getInstance(project);
                if(commitList.get(TTMWindow.activeCommit_cIndex).isFake() == true)
                {
                    // It means that: TTMWindow.activeCommit_cIndex = 0 , Plus, we have uncommited changes
                    instance.checkoutCommitID(commitList.get(1).getCommitID());
                    instance.applyStash();
                }
                else
                    instance.checkoutCommitID(commitList.get(TTMWindow.activeCommit_cIndex).getCommitID());

                CodeTimeMachineAction.toolWindow.hide(null);
            }
        });
        updateProjectToThisCommitBtn.setFocusable(false);
        updateProjectToThisCommitBtn.setFocusPainted(false);
        updateProjectToThisCommitBtn.setForeground(Color.RED);
        this.add(updateProjectToThisCommitBtn); // As there's no layout, we should set Bound it. we'll do this in "ComponentResized()" event
    }

    private void addMouseMotionListener()
    {
        this.addMouseMotionListener(new MouseMotionListener()
        {
            @Override
            public void mouseDragged(MouseEvent e)
            {
            }

            @Override
            public void mouseMoved(MouseEvent e)
            {
                // Important Note: When mouse enter TextArea, we no longer get called until we get out of it.
                // So "currentMousePoint" show that last location just before entering TextArea.

                currentMousePoint = e.getPoint();
                //updateHovredWindow //This is not enough. Sometimes we don't move mosue, but underneath the windows are moving.
            }
        });
    }

    private void updateHoveredWindow()
    {
        for (int i=0; i<virtualEditorWindows.length; i++)
        {
            if(virtualEditorWindows[i].isVisible==false) continue;

            if(virtualEditorWindows[i].drawingRect.contains(currentMousePoint))
            {
                if(currentMouseHoveredIndex ==i)
                    return;
                UpdateHoveredVirtualWindow(i);
                return;
            }
        }
        if(currentMouseHoveredIndex!=INVALID)
            UpdateHoveredVirtualWindow(INVALID);// Here = mouse hovered no virtualWindows
    }

    private void UpdateHoveredVirtualWindow(int new_cIndex)
    {
        if(currentMouseHoveredIndex != INVALID)
        {
            virtualEditorWindows[currentMouseHoveredIndex].setHighlightBorder(false, DUMMY_COLOR);
            if(!isAuthorsColorMode)
                virtualEditorWindows[currentMouseHoveredIndex].setTemporaryHighlightTopBar(false, DUMMY_COLOR);
        }

        currentMouseHoveredIndex =new_cIndex;

        if(currentMouseHoveredIndex!=INVALID )
        {
            virtualEditorWindows[new_cIndex].setHighlightBorder(true, MOUSE_HOVERED_COLOR);
            if(!isAuthorsColorMode)
                virtualEditorWindows[new_cIndex].setTemporaryHighlightTopBar(true, MOUSE_HOVERED_COLOR);
        }

        TTMWindow.updateTopLayerCommitsInfoData(currentMouseHoveredIndex);

        repaint();
    }

    private void addMouseListener()
    {
        this.addMouseListener(new MouseListener()
        {
            @Override
            public void mouseClicked(MouseEvent e) {}

            @Override
            public void mousePressed(MouseEvent e) {}

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if(currentMouseHoveredIndex!= INVALID)
                {
                    TTMWindow.activeCommit_cIndex = currentMouseHoveredIndex;
                    showCommit(currentMouseHoveredIndex, true);
                    TTMWindow.commitsBar.setActiveCommit_cIndex();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
            }
        });
    }

    private void addMouseWheelListener()
    {
        this.addMouseWheelListener(new MouseWheelListener()
        {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e)
            {
                int notches = e.getWheelRotation();
                if (notches > 0)
                {
                    //Mouse wheel moved UP for -1*notches

                    int activeCommit_cIndex = TTMWindow.activeCommit_cIndex;
                    //int activeCommit_cIndex = targetLayerIndex;
                    if(activeCommit_cIndex+1 >= commitList.size()) return;
                    activeCommit_cIndex++;
                    TTMWindow.activeCommit_cIndex = activeCommit_cIndex;
                    showCommit(activeCommit_cIndex, true);
                    TTMWindow.commitsBar.setActiveCommit_cIndex();
                }
                else
                {
                    //int activeCommit_cIndex = targetLayerIndex;
                    int activeCommit_cIndex = TTMWindow.activeCommit_cIndex;
                    if(activeCommit_cIndex-1 <0) return;
                    activeCommit_cIndex--;
                    TTMWindow.activeCommit_cIndex = activeCommit_cIndex;
                    showCommit(activeCommit_cIndex, true);
                    TTMWindow.commitsBar.setActiveCommit_cIndex();
                }
            }
        });
    }

    private void setupUI_mainEditorWindow()
    {
        //mainEditorWindow = new CustomEditorTextField(FileDocumentManager.getInstance().getDocument(virtualFile), project, FileTypeRegistry.getInstance().getFileTypeByExtension("java"),true,false);
        mainEditorWindow = new CustomEditorTextField("",project, FileTypeRegistry.getInstance().getFileTypeByExtension("java"));
        //mainEditorWindow.setBounds(100,100,100,50); //TEST
        mainEditorWindow.setEnabled(true);
        mainEditorWindow.setOneLineMode(false);
        //mainEditorWindow.setOpaque(true);
        this.add(mainEditorWindow); // As there's no layout, we should set Bound it. we'll do this in "ComponentResized()" event
    }

    private void setupUI_virtualWindows()
    {
        virtualEditorWindows = new VirtualEditorWindow[commitList.size()];

        for (int i = 0; i< commitList.size() ; i++)
        {
            virtualEditorWindows[i] = new VirtualEditorWindow(i /*not cIndex*/, commitList.get(i), metricResultsList.get(i));
        }
    }

    private void updateVirtualWindowsBoundaryAfterComponentResize()
    {
        int xCenter, yCenter, w, h;
        w = topIdealLayerDimention.width;
        h = topIdealLayerDimention.height;
        xCenter = topIdealLayerCenterPos.x;
        yCenter = topIdealLayerCenterPos.y;

        for (int i = 0; i< commitList.size() ; i++)
            virtualEditorWindows[i].setDefaultValues(xCenter, yCenter, w, h);
    }

    private void initialVirtualWindowsVisualizations()
    {
        topLayerOffset = 0;
        topLayerIndex = lastBorderHighlighted_VirtualWindowIndex = 0;

        // Don't forget to call `updateVirtualWindowsBoundryAfterComponentResize()` before
        for (int i = 0; i< commitList.size() ; i++)
        {
            float d = i * LAYER_DISTANCE;
            virtualEditorWindows[i].updateDepth(d);
        }

        highlight(topLayerIndex);
    }

    public void increaseMaxVisibleDepth()
    {
        changeMaxVisibleDepth(MAX_VISIBLE_DEPTH_CHANGE_VALUE);
    }

    public void decreaseMaxVisibleDepth()
    {
        changeMaxVisibleDepth(-1*MAX_VISIBLE_DEPTH_CHANGE_VALUE);
    }

    private void changeMaxVisibleDepth(float delta)
    {
        if(maxVisibleDepth+delta<=LAYER_DISTANCE) return;
        maxVisibleDepth += delta;
        render();
    }

    private void highlight( int virtualWindowIndex)
    {
        virtualEditorWindows[lastBorderHighlighted_VirtualWindowIndex].setHighlightBorder(false, DUMMY_COLOR);

        virtualEditorWindows[virtualWindowIndex].setHighlightBorder(true, ACTIVE_WINDOW_COLOR);

        lastBorderHighlighted_VirtualWindowIndex = virtualWindowIndex;
    }

    @Override
    public void componentResized(ComponentEvent e)
    {
        Dimension size = getSize();
        centerOfThisComponent = new Point(size.width/2, size.height/2);
        //////
        updateTopIdealLayerBoundary();
        //virtualEditorWindows[topLayerIndex].setHighlightBorder(); // Why here?
    }

    @Override
    public void componentMoved(ComponentEvent e) {}

    @Override
    public void componentShown(ComponentEvent e) {}

    @Override
    public void componentHidden(ComponentEvent e) {}

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2d = (Graphics2D) g;

        super.paintComponent(g);

        if(CommonValues.IS_UI_IN_DEBUGGING_MODE)
        {
            g.setColor(new Color(0,255,255));
            g.fillRect(0, 0,getSize().width,getSize().height);
            g.setColor(new Color(255,0,0));
            g.fillOval(getSize().width/2-10, getSize().height/2-10,20,20); //Show Center
        }

        if(virtualEditorWindows!=null)
        {
            draw_tipOfTimeLine(g2d);

            if(currentMetric != Metrics.Types.NONE)
                g.drawString(currentMetric.toString(), startPointOfChartTimeLine.x+10, startPointOfChartTimeLine.y - 30);

            for(int i = commitList.size()-1; i>=0; i--)
            {
                if(virtualEditorWindows[i].isVisible==false) continue;
//                if(i==targetLayerIndex)
//                {
//                    if(mainEditorWindow!=null)
//                    {
//                        mainEditorWindow.paint(g);
//                        mainEditorWindow.paintComponents(g);
//                    }
//                }
                virtualEditorWindows[i].draw(g);



                ////////////////////////  (Left) TimeLine
                Point timeLineMyPoint = virtualEditorWindows[i].timeLinePoint;
                // TimeLine Lines
                if(i != commitList.size()-1 && virtualEditorWindows[i+1].isVisible)
                {
                    // I'm not the first one in for-loop (OR) I'm not the oldest point of time
                    Point timeLineNextPoint = virtualEditorWindows[i+1].timeLinePoint; // Line between myPoint and NextPoint (=Newer commit = Closer to Camera)
                    g.setColor(new Color(0,0,255,virtualEditorWindows[i].alpha));
                    g.drawLine(timeLineMyPoint.x, timeLineMyPoint.y, timeLineNextPoint.x, timeLineNextPoint.y);
                }
                // TimeLine Point
                if(i==targetLayerIndex)
                    g.setColor(new Color(255,0,0,virtualEditorWindows[i].alpha));
                else if(i == currentMouseHoveredIndex)
                    g.setColor(new Color(MOUSE_HOVERED_COLOR.getRed(), MOUSE_HOVERED_COLOR.getGreen(), MOUSE_HOVERED_COLOR.getBlue(), virtualEditorWindows[i].alpha));
                else
                    g.setColor(new Color(0,0,255,virtualEditorWindows[i].alpha));
                g2d.setStroke(new BasicStroke(TIME_LINE_WIDTH));

                final Dimension TIME_LINE_POINT_SIZE = new Dimension(10,4);


                g.fillRoundRect(timeLineMyPoint.x-TIME_LINE_POINT_SIZE.width/2, timeLineMyPoint.y-TIME_LINE_POINT_SIZE.height/2,
                        TIME_LINE_POINT_SIZE.width,TIME_LINE_POINT_SIZE.height,1,1);



                if(i==targetLayerIndex)
                {
                    g2d.setFont(new Font("Arial",Font.BOLD, 11));
                    g2d.drawString(CalendarHelper.convertDateToStringYMD(commitList.get(i).getDate()), timeLineMyPoint.x - 70, timeLineMyPoint.y + 2);
                    g2d.drawString(CalendarHelper.convertDateToTime(commitList.get(i).getDate()), timeLineMyPoint.x +10, timeLineMyPoint.y + 2);
                }
                else if(i==commitList.size()-1 || !CalendarHelper.isSameDay(commitList.get(i).getDate(),commitList.get(i+1).getDate()) )
                {
                    g2d.setFont(new Font("Arial",Font.BOLD, 10));
                    g2d.drawString(CalendarHelper.convertDateToStringYMD(commitList.get(i).getDate()), timeLineMyPoint.x - 68, timeLineMyPoint.y + 2);
                }
                else
                {
                    g2d.setFont(new Font("Arial",Font.ITALIC, 9));
                    g2d.drawString(CalendarHelper.convertDateToTime(commitList.get(i).getDate()), timeLineMyPoint.x - 30, timeLineMyPoint.y + 2);
                }


                ////////////////////////  (Right) Chart
                if(currentMetric== Metrics.Types.NONE) continue;

                Point chartTimeLineMyPoint = virtualEditorWindows[i].chartTimeLinePoint;
                Point chartTimeLineMyValuePoint = virtualEditorWindows[i].getChartValuePoint(currentMetric);


                // TimeLine Lines
                if(i != commitList.size()-1 && virtualEditorWindows[i+1].isVisible)
                {
                    // I'm not the first one in for-loop (OR) I'm not the oldest point of time
                    Point chartTimeLineNextPoint = virtualEditorWindows[i+1].chartTimeLinePoint;
                    Point chartTimeLineNextValuePoint = virtualEditorWindows[i+1].getChartValuePoint(currentMetric);

                    g2d.setStroke(new BasicStroke(TIME_LINE_WIDTH/3));
                    g.setColor(new Color(0,0,255,virtualEditorWindows[i].alpha));
                    g.drawLine(chartTimeLineMyPoint.x, chartTimeLineMyPoint.y, chartTimeLineNextPoint.x, chartTimeLineNextPoint.y);

                    g2d.setStroke(new BasicStroke(TIME_LINE_WIDTH));
                    if(i != currentMouseHoveredIndex && i+1!=currentMouseHoveredIndex)
                        g.setColor(new Color(0, 0, 0, virtualEditorWindows[i].alpha));
                    else
                        g.setColor(new Color(MOUSE_HOVERED_COLOR.getRed(), MOUSE_HOVERED_COLOR.getGreen(), MOUSE_HOVERED_COLOR.getBlue(), virtualEditorWindows[i].alpha));
                    g.drawLine(chartTimeLineMyValuePoint.x, chartTimeLineMyValuePoint.y, chartTimeLineNextValuePoint.x, chartTimeLineNextValuePoint.y);
                }

                // ChartTimeLine Point
                if(i==targetLayerIndex)
                    g.setColor(new Color(255,0,0,virtualEditorWindows[i].alpha));
                else if(i == currentMouseHoveredIndex)
                    g.setColor(new Color(MOUSE_HOVERED_COLOR.getRed(), MOUSE_HOVERED_COLOR.getGreen(), MOUSE_HOVERED_COLOR.getBlue(), virtualEditorWindows[i].alpha));
                else
                    g.setColor(new Color(0,0,255,virtualEditorWindows[i].alpha));
                g.fillRoundRect(chartTimeLineMyPoint.x-TIME_LINE_POINT_SIZE.width/2, chartTimeLineMyPoint.y-TIME_LINE_POINT_SIZE.height/2,
                        TIME_LINE_POINT_SIZE.width,TIME_LINE_POINT_SIZE.height,1,1);

                Color metricC = virtualEditorWindows[i].getMetricColor();
                g.setColor(new Color(metricC.getRed(),metricC.getGreen(),metricC.getBlue(),virtualEditorWindows[i].alpha));

                //Vertical Value Line
                g.drawLine(chartTimeLineMyPoint.x, chartTimeLineMyPoint.y, chartTimeLineMyValuePoint.x, chartTimeLineMyValuePoint.y);
                // Point on Value Height
                g.fillOval(chartTimeLineMyValuePoint.x-TIME_LINE_POINT_SIZE.width/2, chartTimeLineMyValuePoint.y-TIME_LINE_POINT_SIZE.height/2,
                        TIME_LINE_POINT_SIZE.width,TIME_LINE_POINT_SIZE.height);
            }
        }


        //g.drawString(debuggingText,20,20);
    }

    private void draw_tipOfTimeLine(Graphics2D g2d)
    {
        //// Line from tip of TimeLine (ACTUALLY: startPoint+0.1depth) of time line to Triangle
        g2d.setColor(Color.BLUE);
        g2d.setStroke(new BasicStroke(TIME_LINE_WIDTH));
        g2d.drawLine(startPointOfTimeLine.x, startPointOfTimeLine.y, trianglePoint.x, trianglePoint.y);

        //// Triangle
        int[] triangleVertices_x = new int[]{trianglePoint.x-6,trianglePoint.x-14,trianglePoint.x+4};
        int[] triangleVertices_y = new int[]{trianglePoint.y-2,trianglePoint.y+10,trianglePoint.y+3};
        g2d.fillPolygon(triangleVertices_x, triangleVertices_y, 3);
    }

    private void tick(float dt_sec)
    {

        // Moving virtual windows towards active_cIndex codes
        float targetDepth = virtualEditorWindows[targetLayerIndex].depth;
        float targetDepthAbs = Math.abs(virtualEditorWindows[targetLayerIndex].depth);
        int sign = (int) Math.signum(targetDepth);

        float speed_depthPerSec = 0;

        if(targetDepthAbs<=LAYERS_DEPTH_ERROR_THRESHOLD)
            speed_depthPerSec = targetDepth/dt_sec;
        else if(targetDepthAbs<=LAYER_DISTANCE)
            speed_depthPerSec = 3*LAYER_DISTANCE*sign;
        else if(targetDepthAbs<4*LAYER_DISTANCE)
            speed_depthPerSec = 4*LAYER_DISTANCE*sign;
        else if(targetDepthAbs<5)
            speed_depthPerSec = 2*sign;
        else
            speed_depthPerSec = 5*sign;

        //debuggingText = "> "+targetDepth+" > Speed: "+speed_depthPerSec;


        /*/ Slow for debugging
        if(targetDepthAbs<LAYER_DISTANCE)
            speed_depthPerSec = 0.05f*sign;
        else
            speed_depthPerSec = 1*sign;*/

        float deltaDepth = dt_sec * speed_depthPerSec;


        int indexCorrespondingToLowestNonNegativeDepth=-1;
        float lowestNonNegativeDepth=Float.MAX_VALUE;

        for(int i=0; i<commitList.size(); i++)
        {
            float newDepth = virtualEditorWindows[i].depth - deltaDepth;

            virtualEditorWindows[i].updateDepth(newDepth);


            if(newDepth>=0 && newDepth<lowestNonNegativeDepth)
            {
                lowestNonNegativeDepth = newDepth;
                indexCorrespondingToLowestNonNegativeDepth = i;
            }
        }


        topLayerIndex = indexCorrespondingToLowestNonNegativeDepth;
        TTMWindow.updateCommits3DViewActiveRangeOnTimeLine(virtualEditorWindows[topLayerIndex].cIndex);

        targetDepth = virtualEditorWindows[targetLayerIndex].depth;
        if(targetDepth>=0 && targetDepth<0.03)
        {
            // Assert topLayerIndex == targetLayerIndex;
            debuggingText = "<>";
            stopAnimation();
        }


        return;

    }

    public void showCommit(int newCommit_cIndex, boolean withAnimation)
    {
        if(withAnimation==false)
        {
            // TODO: without animation
            //loadMainEditorWindowContent();
            //virtualEditorWindows[topLayerIndex].setHighlightBorder();
            // Arrange VirtualEditorWindows
            // After implementing this function, we could call "codeHistory3DView.showCommit(0, false);" after instancing this class
        }
        else
        {
            if( targetLayerIndex==newCommit_cIndex)
                return;
            this.targetLayerIndex = newCommit_cIndex;

            highlight(targetLayerIndex);

            if(!playing3DAnimationTimer.isRunning())
                playing3DAnimationTimer.start();

            mainEditorWindow.setVisible(false);
        }
    }

    private String getStringFromCommits(int commitIndex)
    {
        String content= commitList.get(commitIndex).getFileContent();
        return content;
    }

    public void stopAnimation()
    {
        loadMainEditorWindowContent();

        playing3DAnimationTimer.stop();
    }

    public void render()
    {
        updateTimeLineDrawing();

        for(int i=0; i<commitList.size(); i++)
        {
            float d = virtualEditorWindows[i].depth;
            virtualEditorWindows[i].updateDepth(d);

        }
        repaint();
    }

    private void loadMainEditorWindowContent()
    {
        String content = getStringFromCommits(topLayerIndex);

        ApplicationManager.getApplication().invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                mainEditorWindow.setText(content);
            }
        });

        updateMainEditorWindowBoundaryAfterComponentResize();
        mainEditorWindow.setCaretPosition(0); // TODO: Doesn't Work
        mainEditorWindow.setVisible(true);

    }

    private void updateMainEditorWindowBoundaryAfterComponentResize()
    {
        int x,y,w,h;
        w = virtualEditorWindows[topLayerIndex].drawingRect.width-2*VIRTUAL_WINDOW_BORDER_TICKNESS;
        h = virtualEditorWindows[topLayerIndex].drawingRect.height-TOP_BAR_HEIGHT;
        x = virtualEditorWindows[topLayerIndex].drawingRect.x+VIRTUAL_WINDOW_BORDER_TICKNESS;
        y = virtualEditorWindows[topLayerIndex].drawingRect.y+TOP_BAR_HEIGHT;
        //mainEditorWindow.setSize(w,h);
        //mainEditorWindow.setLocation(x,y);
        mainEditorWindow.setBounds(x,y,w,h);
    }

    private void updateTopIdealLayerBoundary()
    {
        final int FREE_SPACE_VERTICAL = 100, FREE_SPACE_HORIZONTAL = 60;
        topIdealLayerDimention = new Dimension(  getSize().width/2, 2*getSize().height/3 /*2/3 of whole vertical*/);
        topIdealLayerDimention.width *= MyRenderer.getInstance().BASE_DEPTH; // because Renderer divide it by BASE_DEPTH
        topIdealLayerDimention.height *= MyRenderer.getInstance().BASE_DEPTH;

        topIdealLayerCenterPos = new Point(centerOfThisComponent.x, 2*getSize().height/3 /*Fit from bottom*/);
        ////
        updateEverythingAfterComponentResize();
    }

    private void updateEverythingAfterComponentResize()
    {
        updateVirtualWindowsBoundaryAfterComponentResize();
        updateMainEditorWindowBoundaryAfterComponentResize();
        updateButtonsAfterComponentResize();
        updateTimeLineDrawing();
    }

    private void updateButtonsAfterComponentResize()
    {
        updateActiveFileToThisCommitBtn.setBounds(100,550,180,27);
        updateProjectToThisCommitBtn.setBounds(100,600,180,27);
    }

    private void updateTimeLineDrawing()
    {
        startPointOfTimeLine = MyRenderer.getInstance().calculateTimeLinePoint(topIdealLayerCenterPos.x, topIdealLayerCenterPos.y,
                                                                        topIdealLayerDimention.width, topIdealLayerDimention.height,
                                                                        0.05f+MyRenderer.getInstance().BASE_DEPTH);


        Point aLittleAfterstartPointOfTimeLine = MyRenderer.getInstance().calculateTimeLinePoint(topIdealLayerCenterPos.x, topIdealLayerCenterPos.y,
                                                                        topIdealLayerDimention.width, topIdealLayerDimention.height,
                                                                        +0.4f+MyRenderer.getInstance().BASE_DEPTH);

        int deltaX = startPointOfTimeLine.x - aLittleAfterstartPointOfTimeLine.x;
        int deltaY = startPointOfTimeLine.y - aLittleAfterstartPointOfTimeLine.y;
        trianglePoint = (Point) startPointOfTimeLine.clone();
        trianglePoint.x += deltaX; //
        trianglePoint.y += deltaY;

        startPointOfChartTimeLine = MyRenderer.getInstance().calculateChartTimeLinePoint(topIdealLayerCenterPos.x, topIdealLayerCenterPos.y,
                topIdealLayerDimention.width, topIdealLayerDimention.height,
                0+MyRenderer.getInstance().BASE_DEPTH);
    }

    public void setTopBarHighlight(int cIndex, boolean newStatus, Color c)
    {
        virtualEditorWindows[cIndex].setHighlightTopBar(newStatus, c);
        repaint();
    }

    public void setMetricCalculator(Metrics.Types newMetric)
    {
        currentMetric = newMetric;
        repaint(); //TODO: why not render() only ? or why not both?
    }

    protected class VirtualEditorWindow
    {
        //TODO: Extract whole class to another file
        ////////
        int cIndex =-1;
        private Color someRandomMetric1Color = Color.BLACK;
        CommitWrapper commitWrapper = null;
        MetricCalculationResults metricResults = null;

        boolean isVisible=true;
        float depth;
        int alpha=255;
        Color DEFAULT_BORDER_COLOR = Color.GRAY;
        Color DEFAULT_TOP_BAR_COLOR = Color.GRAY;

        Color myColor=Color.WHITE, myBorderColor=DEFAULT_BORDER_COLOR, myTopBarColor=DEFAULT_TOP_BAR_COLOR;
        Color myTopBarTempColor;
        boolean isTopBarTempColorValid = false;
        int xCenterDefault, yCenterDefault, wDefault, hDefault;
        Rectangle drawingRect = new Rectangle(0, 0, 0, 0);
        Point timeLinePoint = new Point(0,0), chartTimeLinePoint = new Point(0,0);
        //private Point chartValuePoint= new Point(0,0);
        ////////

        public VirtualEditorWindow(int index, CommitWrapper commitWrapper, MetricCalculationResults metricResults)
        {
            this.cIndex = index;
            this.commitWrapper = commitWrapper;
            this.metricResults = metricResults;

            if(COLORFUL || CommonValues.IS_UI_IN_DEBUGGING_MODE)
            {
                Random rand = new Random();
                float r = rand.nextFloat();
                float g = rand.nextFloat();
                float b = rand.nextFloat();
                this.myColor = new Color(r,g,b);
            }
        }

        public Point getChartValuePoint(Metrics.Types metricType)
        {
            Point p = (Point) chartTimeLinePoint.clone();
            int MAX_UI_HEIGHT = 200;

            float v =  metricResults.getMetricValue(metricType);
            v = v*MAX_UI_HEIGHT/metricResults.getMetricMaxValue(metricType);
            if(v<30)
                someRandomMetric1Color = Color.GREEN;
            else if(v<70)
                someRandomMetric1Color = Color.YELLOW;
            else
                someRandomMetric1Color = Color.RED;
            v = MyRenderer.getInstance().render3DTo2D((int)v,depth+MyRenderer.getInstance().BASE_DEPTH);
            p.y -= (int)v;
            return p;
        }

        // this function should be called on each size change
        public void setDefaultValues(int xCenterDefault, int yCenterDefault, int wDefault, int hDefault)
        {
            if(wDefault<=0 || hDefault<=0) return; //Window is not intialized corerctly yet

            this.xCenterDefault = xCenterDefault;
            this.yCenterDefault = yCenterDefault;
            this.wDefault = (int) (wDefault);
            this.hDefault = (int) (hDefault);

            // Now update Boundary according to current depth and new DefaultValues
            updateDepth(depth);

        }

        public void setAlpha(int newAlpha)
        {
            if(newAlpha>255)
                newAlpha=255;
            if(newAlpha<0)
                newAlpha=0;
            alpha = newAlpha;
            myBorderColor = new Color(myBorderColor.getRed(), myBorderColor.getGreen(), myBorderColor.getBlue(), alpha);
            if(!isTopBarTempColorValid)
                myTopBarColor = new Color(myTopBarColor.getRed(), myTopBarColor.getGreen(), myTopBarColor.getBlue(), alpha);
            else
                myTopBarTempColor = new Color(myTopBarTempColor.getRed(), myTopBarTempColor.getGreen(), myTopBarTempColor.getBlue(), alpha);
            myColor = new Color(myColor.getRed(), myColor.getGreen(), myColor.getBlue(), alpha);
        }

        public void updateDepth(float depth)
        {
            this.depth = depth;

            // we shouldn't "&& alpha<X". because if get invisible we never enter "doRenderCalculation()" and we never
            // get visible again. :D
            if(depth<MIN_VISIBLE_DEPTH || depth> maxVisibleDepth)
                isVisible=false;
            else
                isVisible=true;

            if(isVisible)
                doRenderCalculation();
        }

        public void doRenderCalculation()
        {
            float renderingDepth = depth + MyRenderer.getInstance().BASE_DEPTH;

            //////////////// Alpha
            int newAlpha;
            if(depth>0)
                newAlpha = (int)(255*(1-depth/ maxVisibleDepth));
            else
                // By adding LAYERS_DEPTH_ERROR_THRESHOLD we make the layer invisible(alpha=0) before getting depth=-LAYER_DISTANCE
                // It's needed because sometimes layers movement stops while the new top layer is 0+e (not 0) and so the layer
                // which was supposed to go out (go to depth -LAYER_DISTANCE) get stuck at depth = -LAYERDISTANCE+e.
                newAlpha = (int) (255*(1-depth/(MIN_VISIBLE_DEPTH+LAYERS_DEPTH_ERROR_THRESHOLD+EPSILON)));
            setAlpha(newAlpha);

            if(newAlpha < 20)
            {
                // Optimization
                isVisible=false;
                return;
            }



            //////////////// Size
            drawingRect.width = MyRenderer.getInstance().render3DTo2D(wDefault, renderingDepth);
            drawingRect.height = MyRenderer.getInstance().render3DTo2D(hDefault, renderingDepth);
            Point p = MyRenderer.getInstance().render3DTo2D(xCenterDefault, yCenterDefault, renderingDepth);
            drawingRect.x = p.x - drawingRect.width/2;
            drawingRect.y = p.y - drawingRect.height/2;


            ////////////// TimeLine
            // We also could use "MyRenderer.getInstance().calculateTimeLinePoint()". But it's worthless and that function
            // is designed for external user ( check 'updateTimeLineDrawing()' function)
            timeLinePoint = MyRenderer.getInstance().render3DTo2D(xCenterDefault, yCenterDefault, renderingDepth);
            timeLinePoint.x = drawingRect.x - (int)(MyRenderer.getInstance().TIME_LINE_GAP*drawingRect.width);
            timeLinePoint.y = drawingRect.y;

            ///////////// Chart TimeLine
            chartTimeLinePoint = MyRenderer.getInstance().render3DTo2D(xCenterDefault, yCenterDefault, renderingDepth);
            chartTimeLinePoint.x = drawingRect.x + drawingRect.width + (int)(MyRenderer.getInstance().TIME_LINE_GAP*drawingRect.width);
            chartTimeLinePoint.y = drawingRect.y;
        }

        public void setHighlightBorder(boolean newStatus, Color newColor)
        {
            if(newStatus==true)
                myBorderColor = newColor;
            else if(cIndex == TTMWindow.activeCommit_cIndex)
                myBorderColor = ACTIVE_WINDOW_COLOR;
            else
                myBorderColor = DEFAULT_BORDER_COLOR;

            setAlpha(alpha); //Apply current alpha to above solid colors
        }

        public void setTemporaryHighlightTopBar(boolean newStatus, Color c)
        {
            if(newStatus==true)
            {
                myTopBarTempColor = c;
                isTopBarTempColorValid = true;
            }
            else
            {
                isTopBarTempColorValid = false;
            }

            setAlpha(alpha); //Apply current alpha to above solid colors
        }

        public void setHighlightTopBar(boolean newStatus, Color c)
        {
            if(newStatus==true)
                myTopBarColor = c;
            else
                myTopBarColor = DEFAULT_TOP_BAR_COLOR;

            setAlpha(alpha); //Apply current alpha to above solid colors
        }

        public void setHighlightTopBar(boolean newStatus)
        {
           setHighlightTopBar(newStatus, Color.ORANGE);
        }

        public void draw(Graphics g)
        {
            Graphics2D g2d = (Graphics2D) g;
            if(this.isVisible!=true) return;

           draw_mainRect(g2d, drawingRect);
           draw_mainRectBorder(g2d, drawingRect);
           draw_topBar(g2d, drawingRect);
           draw_topBarText(g, drawingRect);
        }

        private void draw_topBarText(Graphics g, Rectangle drawingRect)
        {
            /// Name
            String text="";
            Graphics g2 = g.create();
            g2.setColor(new Color(0,0,0,alpha));
            Rectangle2D rectangleToDrawIn = new Rectangle2D.Double(drawingRect.x,drawingRect.y,drawingRect.width,TOP_BAR_HEIGHT);
            g2.setClip(rectangleToDrawIn);
            if(cIndex ==topLayerIndex)
            {
                g2.setFont(new Font("Courier", Font.BOLD, 10));
                text = getTopBarMessage();
                //text = "I: "+cIndex+"Depth = "+ Float.toString(depth)+ "FontSize: --  "+ "Alpha: "+alpha;
                DrawingHelper.drawStringCenter(g2, text, drawingRect.x+drawingRect.width/2, drawingRect.y+8);
                String path = virtualFile.getPath();
                path = fit(path, 70, g2);
                text = new String(path);
                DrawingHelper.drawStringCenter(g2, text, drawingRect.x+drawingRect.width/2, drawingRect.y+18);
            }
            else
            {
                float fontSize = 20.f/(MyRenderer.getInstance().BASE_DEPTH+depth);
                g2.setFont(new Font("Courier", Font.BOLD, (int)fontSize));
                text = getTopBarMessage();
                //text = "I: "+cIndex+"Depth = "+ Float.toString(depth)+ "FontSize: "+fontSize + "Alpha: "+alpha;
                DrawingHelper.drawStringCenter(g2, text, drawingRect.x+drawingRect.width/2, drawingRect.y+15);
            }
        }

        private void draw_topBar(Graphics2D g2d, Rectangle drawingRect)
        {
            /// TopBar
            if(!isTopBarTempColorValid)
                g2d.setColor( this.myTopBarColor);
            else
                g2d.setColor( this.myTopBarTempColor);
            g2d.fillRect(drawingRect.x, drawingRect.y+VIRTUAL_WINDOW_BORDER_TICKNESS, drawingRect.width, TOP_BAR_HEIGHT);
        }

        private void draw_mainRectBorder(Graphics2D g2d, Rectangle drawingRect)
        {
            /// Border
            g2d.setStroke(new BasicStroke(2));
            g2d.setColor( this.myBorderColor);
            g2d.drawRect(drawingRect.x, drawingRect.y, drawingRect.width, drawingRect.height);
        }

        private void draw_mainRect(Graphics2D g2d, Rectangle drawingRect)
        {
            /// Rect
            g2d.setColor( this.myColor);
            g2d.fillRect(drawingRect.x, drawingRect.y, drawingRect.width, drawingRect.height);
        }

        public int getMetricValue(Metrics.Types metric)
        {
            /*switch (metric)
            {
                case METRIC1:
                    return someRandomMetric1;
                case METRIC2:
                    return  someRandomMetric2;
            }*/
            return 0;
        }

        public Color getMetricColor()
        {
            return someRandomMetric1Color;
        }

        private String getTopBarMessage()
        {
            String text;
            if(commitWrapper.isFake())
                text = new String(commitWrapper.getCommitID());
            else
                text = new String("Commit "+commitWrapper.getCommitID());
            return text;
        }

        private String fit(String s, int maxCharacterCount, Graphics g)
        {
            String result = s;
            int extraCharacterCount = result.length() - maxCharacterCount;
            int cropFrom = result.indexOf('/', extraCharacterCount);
            result = result.substring(cropFrom);
            result = "..."+result;
            return result;
        }


    } // End of VirtualEditorWindow class

    class CustomEditorTextField extends EditorTextField
    {
        // >>>>>>>> Scroll for EditorTextField
        // https://intellij-support.jetbrains.com/hc/en-us/community/posts/206759275-EditorTextField-and-surrounding-JBScrollPane

        final Rectangle INVISBLE_BOUND_RECT = new Rectangle(-100, -100, 0,0);
        Rectangle lastBoundBeforeInvisible;
        boolean isVisible=true;

        public CustomEditorTextField(Document document, Project project, FileType fileType, boolean isViewer, boolean oneLineMode)
        {
            super(document,project,fileType,isViewer,oneLineMode);
        }

        public CustomEditorTextField(@NotNull String text, Project project, FileType fileType) {
            this(EditorFactory.getInstance().createDocument(text), project, fileType, true, true);
        }

        @Override
        protected EditorEx createEditor()
        {
            EditorEx editor = super.createEditor();
            editor.setVerticalScrollbarVisible(true);
            editor.setHorizontalScrollbarVisible(true);
            addLineNumberToEditor(editor);
            return editor;
        }

        private String getSelectedText()
        {
            String s = getEditor().getSelectionModel().getSelectedText();
            if(s==null)
                s = "";
            return s;
        }

        private void addLineNumberToEditor(EditorEx editor)
        {
            EditorSettings settings = editor.getSettings();
            settings.setLineNumbersShown(true);
            editor.reinitSettings();
        }

        public void setVisible(boolean newStatus)
        {
            // if we use normal behaviour of setVisible(), while visibility is False the KeyBinding doesn't work strangely.
            if(isVisible == newStatus) return;

            if(newStatus==false)
            {
                isVisible = false;
                lastBoundBeforeInvisible = this.getBounds();
                setBounds(INVISBLE_BOUND_RECT);
            }
            else
            {
                isVisible = true;
                setBounds(lastBoundBeforeInvisible);
            }



        }

    }

} // End of Commits3DView class