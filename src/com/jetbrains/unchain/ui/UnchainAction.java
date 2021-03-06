/*
 * Copyright (c) 2013 JetBrains
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.jetbrains.unchain.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

/**
 * @author yole
 */
public class UnchainAction extends AnAction {
  static final String UNCHAIN_TOOLWINDOW_ID = "Move with Dependencies";

  @Override
  public void actionPerformed(AnActionEvent anActionEvent) {
    Project project = getEventProject(anActionEvent);

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(UNCHAIN_TOOLWINDOW_ID);
    if (toolWindow == null) {
      toolWindow = toolWindowManager.registerToolWindow(UNCHAIN_TOOLWINDOW_ID, false, ToolWindowAnchor.RIGHT);
      ContentFactory contentFactory = toolWindow.getContentManager().getFactory();
      PsiFile psiFile = anActionEvent.getData(LangDataKeys.PSI_FILE);
      PsiClass psiClass = null;
      if (psiFile instanceof PsiJavaFile) {
        PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
        if (classes.length > 0) {
          psiClass = classes[0];
        }
      }
      Content content = contentFactory.createContent(new UnchainPanel(project, psiClass), "", false);
      toolWindow.getContentManager().addContent(content);
    }
    toolWindow.activate(null);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(getEventProject(e) != null);
  }
}
