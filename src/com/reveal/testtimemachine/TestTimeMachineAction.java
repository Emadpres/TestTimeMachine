package com.reveal.testtimemachine;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by emadpres on 11/23/16.
 */
public class TestTimeMachineAction extends AnAction
{
    final boolean AUTOMATICALLY_CHOOSE_SAMPLE_FILES = false;
    final int MAX_NUM_OF_FILES = 2;
    //////////////////////////////
    Project project = null;
    ToolWindow toolWindow = null;

    @Override
    public void actionPerformed(AnActionEvent e)
    {
        project = e.getProject();


        VirtualFile[] chosenVirtualFiles = selectVirtualFiles_auto(e);
        if(chosenVirtualFiles[0] == null)
            chosenVirtualFiles = selectVirtualFiles_manually();

        if(chosenVirtualFiles == null || chosenVirtualFiles.length==0 || chosenVirtualFiles.length > MAX_NUM_OF_FILES)
            return;

        VcsHistoryProvider myGitVcsHistoryProvider = getGitHistoryProvider();
        ArrayList<List<VcsFileRevision>> _fileRevisionsLists = getRevisionListForSubjectAndTestClass(myGitVcsHistoryProvider, chosenVirtualFiles);

        ArrayList<CommitWrapper>[] subjectAndTestClassCommitsList = new ArrayList[2];
        CommitWrapper aCommitWrapper = null;
        for(int i=0; i< chosenVirtualFiles.length; i++)
        {
            int realCommitsSize = _fileRevisionsLists.get(i).size();
            subjectAndTestClassCommitsList[i] = new ArrayList<>(realCommitsSize + 1);


            int cIndex = 0;

            ///// First Fake (UncommitedChanges)
            String currentContent = "";
            try
            {
                byte[] currentBytes = chosenVirtualFiles[i].contentsToByteArray();
                currentContent = new String(currentBytes);
            } catch (IOException e1)
            {
                e1.printStackTrace();
            }

            String mostRecentCommitContent = VcsFileRevisionHelper.getContent(_fileRevisionsLists.get(i).get(0));
            if(! mostRecentCommitContent.equals(currentContent) )
            {
                final String UNCOMMITED_CHANGE_TEXT  = "Uncommited Changes";
                aCommitWrapper = new CommitWrapper(currentContent, UNCOMMITED_CHANGE_TEXT,new Date(),UNCOMMITED_CHANGE_TEXT, cIndex++);
                subjectAndTestClassCommitsList[i].add(0,aCommitWrapper);
            }

            ///// Other Real
            for(int j=0; j< realCommitsSize; j++)
            {
                aCommitWrapper = new CommitWrapper(_fileRevisionsLists.get(i).get(j),cIndex++);
                subjectAndTestClassCommitsList[i].add(aCommitWrapper);
            }






            /// Sort by Date all commits
            Collections.sort(subjectAndTestClassCommitsList[i], new Comparator<CommitWrapper>()
            {
                @Override
                public int compare(CommitWrapper o1, CommitWrapper o2)
                {
                    return o2.getDate().compareTo(o1.getDate());
                }
            });
        }


        if(toolWindow == null)
            toolWindow = ToolWindowManager.getInstance(project).registerToolWindow("TTM", true, ToolWindowAnchor.TOP);



        String contentName = "";
        contentName += chosenVirtualFiles[0].getNameWithoutExtension();
        for(int i=1; i< chosenVirtualFiles.length; i++)
        {
            contentName += " vs. ";
            contentName += chosenVirtualFiles[1].getNameWithoutExtension();
        }

        TTMSingleFileView mainWindow = new TTMSingleFileView(project, chosenVirtualFiles[0], subjectAndTestClassCommitsList[0]);
        Content ttm_content = toolWindow.getContentManager().getFactory().createContent(mainWindow.getComponent(), contentName, true);
        toolWindow.getContentManager().addContent(ttm_content);
        toolWindow.setAutoHide(false);
        toolWindow.setAvailable(true,null);



    }


    private ArrayList<List<VcsFileRevision>> getRevisionListForSubjectAndTestClass(VcsHistoryProvider myGitVcsHistoryProvider, VirtualFile[] chosenVirtualFiles)
    {
        ArrayList<List<VcsFileRevision>> _fileRevisionsLists = new ArrayList<>(chosenVirtualFiles.length);

        for(int i = 0; i< chosenVirtualFiles.length; i++)
        {
            FilePath filePathOn = VcsContextFactory.SERVICE.getInstance().createFilePathOn(chosenVirtualFiles[i]);
            VcsHistorySession sessionFor = null;
            try
            {
                sessionFor = myGitVcsHistoryProvider.createSessionFor(filePathOn);
            } catch (VcsException e1)
            {
                e1.printStackTrace();
            }
            _fileRevisionsLists.add(sessionFor.getRevisionList());
        }
        return _fileRevisionsLists;
    }

    private VcsHistoryProvider getGitHistoryProvider()
    {
        ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
        AbstractVcs[] allActiveVcss = mgr.getAllActiveVcss();
        AbstractVcs myGit = allActiveVcss[0];
        return myGit.getVcsHistoryProvider();
    }

    public VirtualFile[] selectVirtualFiles_manually()
    {
        VirtualFile[] chosenVirtualFiles = null;

        if(AUTOMATICALLY_CHOOSE_SAMPLE_FILES)
        {
            chosenVirtualFiles = new VirtualFile[2];
            chosenVirtualFiles[0] = LocalFileSystem.getInstance().findFileByIoFile(new File("/Users/emadpres/IdeaProjects/Vector/src/com/math/vector/Vector.java"));
            chosenVirtualFiles[1] = LocalFileSystem.getInstance().findFileByIoFile(new File("/Users/emadpres/IdeaProjects/Vector/testSrc/com/math/vector/VectorTest.java"));
        }
        else
        {
            chosenVirtualFiles = FileChooser.chooseFiles(
                    new FileChooserDescriptor(true, false, false, false, false, true),
                    project, null);
        }

        return chosenVirtualFiles;
    }


    private VirtualFile[] selectVirtualFiles_auto(AnActionEvent e)
    {
        VirtualFile[] chosenVirtualFiles = new VirtualFile[1];

        if(e.getData(LangDataKeys.PSI_FILE) != null)
            chosenVirtualFiles[0] = e.getData(LangDataKeys.PSI_FILE).getVirtualFile();
        else
            chosenVirtualFiles[0] = null;

        return chosenVirtualFiles;
    }
}
