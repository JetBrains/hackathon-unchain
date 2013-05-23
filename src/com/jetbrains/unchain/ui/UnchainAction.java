package com.jetbrains.unchain.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

/**
 * @author yole
 */
public class UnchainAction extends AnAction {
  private static final String UNCHAIN_TOOLWINDOW_ID = "Unchain";

  @Override
  public void actionPerformed(AnActionEvent anActionEvent) {
    Project project = getEventProject(anActionEvent);

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(UNCHAIN_TOOLWINDOW_ID);
    if (toolWindow == null) {
      toolWindow = toolWindowManager.registerToolWindow(UNCHAIN_TOOLWINDOW_ID, false, ToolWindowAnchor.RIGHT);
      ContentFactory contentFactory = toolWindow.getContentManager().getFactory();
      Content content = contentFactory.createContent(new UnchainPanel(project), "Dependencies", false);
      toolWindow.getContentManager().addContent(content);
    }
    toolWindow.activate(null);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(getEventProject(e) != null);
  }
}
