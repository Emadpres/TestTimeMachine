package com.reveal.codetimemachine;


import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public abstract class CommitsBarBase
{
    final int INVALID = -1;

    protected TTMSingleFileView TTMWindow = null;

    protected Dimension COMMITS_BAR_VIEW_DIMENSION = new Dimension(200,1000);

    protected ArrayList<CommitWrapper> commitList /* Most recent commit at 0*/;

    protected ClassType classType = ClassType.NONE;



    public abstract void updateCommitsList(ArrayList<CommitWrapper> newCommitList);

    public abstract JComponent getComponent();

    public abstract void setActiveCommit_cIndex();
}