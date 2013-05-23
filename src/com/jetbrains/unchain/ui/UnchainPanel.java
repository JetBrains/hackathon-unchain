package com.jetbrains.unchain.ui;

import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.ui.*;
import com.jetbrains.unchain.BadDependencyItem;
import com.jetbrains.unchain.PsiQNames;
import com.jetbrains.unchain.Unchainer;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;

/**
 * @author yole
 */
public class UnchainPanel extends JPanel {
  private final Project myProject;
  private JPanel myMainPanel;
  private JPanel myClassNamePlaceholder;
  private JComboBox myTargetModuleComboBox;
  private JButton myGoButton;
  private JPanel myCardsPanel;
  private JList myBadDependenciesList;
  private JList myCallChainList;
  private JList myGoodDepsList;
  private final EditorTextField myClassNameField;

  public UnchainPanel(final Project project) {
    myProject = project;
    setLayout(new BorderLayout());
    add(myMainPanel, BorderLayout.CENTER);

    createToolbar();

    myClassNameField = new EditorTextField("", project, StdFileTypes.JAVA);
    ComponentWithBrowseButton<EditorTextField> classNameWithBrowseButton = new ComponentWithBrowseButton<EditorTextField>(myClassNameField, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        PsiClass initialClass = getSelectedClass();
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).createProjectScopeChooser("Choose Class to Move", initialClass);
        chooser.showDialog();
        PsiClass selected = chooser.getSelected();
        if (selected != null) {
          myClassNameField.setText(selected.getQualifiedName());
        }
      }
    });
    myClassNamePlaceholder.add(classNameWithBrowseButton, BorderLayout.CENTER);

    Module[] modules = ModuleManager.getInstance(project).getModules();
    myTargetModuleComboBox.setModel(new CollectionComboBoxModel(Arrays.asList(modules)));
    myTargetModuleComboBox.setRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          append(((Module) value).getName());
        }
      }
    });
    myGoButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        PsiClass psiClass = getSelectedClass();
        Module module = (Module) myTargetModuleComboBox.getSelectedItem();
        if (psiClass != null && module != null) {
          runUnchainer(psiClass, module);
        }
      }
    });

    setupBadDependenciesListeners();
    setupCallChainListeners();
    setupGoodDependenciesListeners();
  }

  private void createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new CloseTabToolbarAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ToolWindowManager.getInstance(myProject).unregisterToolWindow(UnchainAction.UNCHAIN_TOOLWINDOW_ID);
      }
    });
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    add(toolbar.getComponent(), BorderLayout.NORTH);
  }

  private void setupBadDependenciesListeners() {
    myBadDependenciesList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2 && !mouseEvent.isPopupTrigger()) {
          BadDependencyItem selectedValue = (BadDependencyItem) myBadDependenciesList.getSelectedValue();
          if (selectedValue != null) {
            Navigatable navigatable = selectedValue.getNavigatable();
            if (navigatable != null) {
              navigatable.navigate(true);
            }
          }
        }
      }
    });
    myBadDependenciesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent listSelectionEvent) {
        BadDependencyItem selectedValue = (BadDependencyItem) myBadDependenciesList.getSelectedValue();
        if (selectedValue != null) {
          myCallChainList.setModel(new CollectionListModel<String>(selectedValue.getCallChain()));
        }
      }
    });
  }

  private void setupCallChainListeners() {
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        String qName = (String) myCallChainList.getSelectedValue();
        PsiElement target = PsiQNames.findElementByQName(myProject, qName);
        navigateToReference(target);
        return true;
      }
    }.installOn(myCallChainList);
  }

  private void setupGoodDependenciesListeners() {
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        String qName = (String) myGoodDepsList.getSelectedValue();
        PsiElement target = PsiQNames.findElementByQName(myProject, qName);
        if (target instanceof Navigatable) {
          ((Navigatable) target).navigate(true);
        }
        return true;
      }
    }.installOn(myGoodDepsList);
  }


  private void navigateToReference(PsiElement target) {
    if (target != null && myCallChainList.getSelectedIndex() == myCallChainList.getModel().getSize() - 1) {
      BadDependencyItem badDependency = (BadDependencyItem) myBadDependenciesList.getSelectedValue();
      PsiReference reference = ReferencesSearch.search(badDependency.getPsiElement(), new LocalSearchScope(target)).findFirst();
      if (reference != null) {
        new OpenFileDescriptor(myProject, reference.getElement().getContainingFile().getVirtualFile(),
            reference.getRangeInElement().getStartOffset() + reference.getElement().getTextRange().getStartOffset()).navigate(true);
        return;
      }
    }
    if (target instanceof Navigatable) {
      ((Navigatable) target).navigate(true);
    }
  }

  private void runUnchainer(PsiClass psiClass, Module module) {
    final Unchainer unchainer = new Unchainer(psiClass, module);
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        unchainer.run();
      }
    }, "Analyzing Dependencies", true, myProject);

    CardLayout cardLayout = (CardLayout) myCardsPanel.getLayout();
    if (unchainer.getBadDependencies().size() > 0) {
      cardLayout.show(myCardsPanel, "BadDeps");
      fillBadDependenciesList(unchainer);
    }
    else {
      cardLayout.show(myCardsPanel, "GoodDeps");
      fillGoodDependenciesList(unchainer);
    }
  }

  private void fillGoodDependenciesList(Unchainer unchainer) {
    myGoodDepsList.setModel(new CollectionListModel<String>(unchainer.getGoodDependencies()));
  }

  private void fillBadDependenciesList(Unchainer unchainer) {
    myBadDependenciesList.setModel(new CollectionListModel<BadDependencyItem>(unchainer.getBadDependencies()));
  }

  private PsiClass getSelectedClass() {
    return JavaPsiFacade.getInstance(myProject).findClass(myClassNameField.getText(), ProjectScope.getProjectScope(myProject));
  }
}
