package com.jetbrains.unchain.ui;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.EditorTextField;
import com.jetbrains.unchain.BadDependencyItem;
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
  private final EditorTextField myClassNameField;

  public UnchainPanel(final Project project) {
    myProject = project;
    setLayout(new BorderLayout());
    add(myMainPanel, BorderLayout.CENTER);

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
    myCallChainList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2 && !mouseEvent.isPopupTrigger()) {
          String qName = (String) myCallChainList.getSelectedValue();
          if (qName != null) {
            navigateToQName(qName);
          }
        }
      }
    });

  }

  private void navigateToQName(String qName) {
    int hash = qName.indexOf('#');
    String className = hash >= 0 ? qName.substring(0, hash) : qName;
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.projectScope(myProject));
    if (aClass == null) {
      return;
    }
    PsiElement target = aClass;
    if (hash >= 0) {
      PsiMethod[] methodsByName = aClass.findMethodsByName(qName.substring(hash + 1), false);
      if (methodsByName.length > 0) {
        target = methodsByName[0];
      }
      else {
        PsiField field = aClass.findFieldByName(qName.substring(hash + 1), false);
        if (field != null) {
          target = field;
        }
      }
    }

    if (myCallChainList.getSelectedIndex() == myCallChainList.getModel().getSize() - 1) {
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
    fillBadDependenciesList(unchainer);
  }

  private void fillBadDependenciesList(Unchainer unchainer) {
    myBadDependenciesList.setModel(new CollectionListModel<BadDependencyItem>(unchainer.getBadDependencies()));
  }

  private PsiClass getSelectedClass() {
    return JavaPsiFacade.getInstance(myProject).findClass(myClassNameField.getText(), ProjectScope.getProjectScope(myProject));
  }
}
