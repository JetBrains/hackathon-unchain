package com.jetbrains.unchain.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.jetbrains.unchain.BadDependencyItem;
import com.jetbrains.unchain.PsiQNames;
import com.jetbrains.unchain.UnchainMover;
import com.jetbrains.unchain.Unchainer;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
  private JBList myBadDepsList;
  private JBList myCallChainList;
  private JList myGoodDepsList;
  private JButton myMoveClassesButton;
  private final EditorTextField myClassNameField;
  private boolean myBadDepsVisible;
  private boolean myGoodDepsVisible;
  private final List<String> myUnwantedDeps = new ArrayList<String>();

  public UnchainPanel(final Project project) {
    myProject = project;
    setLayout(new BorderLayout());
    add(myMainPanel, BorderLayout.CENTER);

    myBadDepsList.getEmptyText().setText("Select class to analyze and press Go");

    createToolbar();

    myClassNameField = new EditorTextField("", project, StdFileTypes.JAVA);

    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(myProject);
    PsiPackage defaultPackage = JavaPsiFacade.getInstance(myProject).findPackage("");
    final PsiCodeFragment fragment = factory.createReferenceCodeFragment("", defaultPackage, true, true);
    myClassNameField.setDocument(PsiDocumentManager.getInstance(myProject).getDocument(fragment));

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
        runUnchainer();
      }
    });

    setupBadDependenciesListeners();
    setupCallChainListeners();
    setupGoodDependenciesListeners();
    myMoveClassesButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        moveClasses();
      }
    });
  }

  private void createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new CloseTabToolbarAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ToolWindowManager.getInstance(myProject).unregisterToolWindow(UnchainAction.UNCHAIN_TOOLWINDOW_ID);
      }
    });
    group.add(new MergeAction());
    group.add(new MarkUnwantedAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    add(toolbar.getComponent(), BorderLayout.NORTH);
  }

  private void setupBadDependenciesListeners() {
    myBadDepsList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2 && !mouseEvent.isPopupTrigger()) {
          BadDependencyItem selectedValue = (BadDependencyItem) myBadDepsList.getSelectedValue();
          if (selectedValue != null) {
            Navigatable navigatable = selectedValue.getNavigatable();
            if (navigatable != null) {
              navigatable.navigate(true);
            }
          }
        }
      }
    });
    myBadDepsList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent listSelectionEvent) {
        BadDependencyItem selectedValue = (BadDependencyItem) myBadDepsList.getSelectedValue();
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
      BadDependencyItem badDependency = (BadDependencyItem) myBadDepsList.getSelectedValue();
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

  private void runUnchainer() {
    PsiClass psiClass = getSelectedClass();
    Module module = (Module) myTargetModuleComboBox.getSelectedItem();
    if (psiClass != null && module != null) {
      runUnchainer(psiClass, module);
    }
  }

  private void runUnchainer(PsiClass psiClass, Module module) {
    final Unchainer unchainer = new Unchainer(psiClass, module);
    unchainer.setUnwantedDependencies(myUnwantedDeps);
    unchainer.setBadDependencyFoundCallback(new Runnable() {
      @Override
      public void run() {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        indicator.setText2("Found " + unchainer.getBadDependencyCount() + " bad dependencies");
      }
    });
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        indicator.setIndeterminate(true);
        indicator.setText2("Found no bad dependencies");
        unchainer.run();
      }
    }, "Analyzing Dependencies", true, myProject);

    boolean haveBadDeps = unchainer.getBadDependencies().size() > 0;
    showDepsCard(haveBadDeps);
    if (haveBadDeps) {
      fillBadDependenciesList(unchainer);
    }
    else {
      fillGoodDependenciesList(unchainer);
    }
  }

  private void showDepsCard(boolean bad) {
    myGoodDepsVisible = false;
    myBadDepsVisible = false;
    CardLayout cardLayout = (CardLayout) myCardsPanel.getLayout();
    if (bad) {
      cardLayout.show(myCardsPanel, "BadDeps");
      myBadDepsVisible = true;
    }
    else {
      cardLayout.show(myCardsPanel, "GoodDeps");
      myGoodDepsVisible = true;
    }
  }

  private void fillGoodDependenciesList(Unchainer unchainer) {
    myGoodDepsList.setModel(new CollectionListModel<String>(unchainer.getGoodDependencies()));
  }

  private void fillBadDependenciesList(Unchainer unchainer) {
    myBadDepsList.setModel(new CollectionListModel<BadDependencyItem>(unchainer.getBadDependencies()));
  }

  private PsiClass getSelectedClass() {
    return JavaPsiFacade.getInstance(myProject).findClass(myClassNameField.getText(), ProjectScope.getProjectScope(myProject));
  }

  private List<String> mergeMembers(List<String> qNames, String selectedMemberQName) {
    List<String> result = new ArrayList<String>();
    String classToMergeQName = PsiQNames.extractClassName(selectedMemberQName);
    for (String qName : qNames) {
      if (qName.startsWith(classToMergeQName)) {
        if (!result.contains(classToMergeQName)) {
          result.add(classToMergeQName);
        }
      }
      else {
        result.add(qName);
      }
    }
    return result;
  }

  private void moveClasses() {
    Module selectedItem = (Module) myTargetModuleComboBox.getSelectedItem();
    CollectionListModel<String> model = (CollectionListModel<String>) myGoodDepsList.getModel();
    final UnchainMover mover = new UnchainMover(selectedItem, model.getItems());
    final Ref<Boolean> failed = Ref.create(false);
    new WriteCommandAction.Simple(myProject, "Moving classes to target module") {
     @Override
      protected void run() throws Throwable {
        try {
          mover.run();
        }
        catch (UnsupportedOperationException e) {
          Messages.showErrorDialog(myProject, e.getMessage(), "Move Failed");
          failed.set(true);
        }
      }
    }.execute();

    if (!failed.get()) {
      myClassNameField.setText("");
      ((CollectionListModel) myBadDepsList.getModel()).removeAll();
      ((CollectionListModel) myCallChainList.getModel()).removeAll();
      showDepsCard(true);
    }
  }

  private class MergeAction extends AnAction {
    public MergeAction() {
      super("Merge", "Move all methods of selected class to target module", AllIcons.Modules.Merge);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      CollectionListModel<String> model = (CollectionListModel<String>) myGoodDepsList.getModel();
      model.replaceAll(mergeMembers(model.getItems(), (String) myGoodDepsList.getSelectedValue()));
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myGoodDepsVisible && myGoodDepsList.getSelectedValue() != null);
    }
  }

  private class MarkUnwantedAction extends AnAction {
    private MarkUnwantedAction() {
      super("Mark Unwanted", "Mark selected class as an unwanted dependency", AllIcons.Actions.Menu_cut);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myUnwantedDeps.add(PsiQNames.extractClassName((String) myCallChainList.getSelectedValue()));
      int selIndex = myBadDepsList.getSelectedIndex();
      runUnchainer();
      int size = myBadDepsList.getModel().getSize();
      if (size > 0 && myBadDepsVisible) {
        myBadDepsList.setSelectedIndex(selIndex >= size ? size - 1 : selIndex);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myBadDepsVisible && myCallChainList.getSelectedValue() != null);
    }
  }
}
