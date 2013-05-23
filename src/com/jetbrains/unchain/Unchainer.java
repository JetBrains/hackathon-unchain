package com.jetbrains.unchain;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.MultiMap;

import java.util.*;

/**
 * @author yole
 */
public class Unchainer {
  private final PsiClass myPsiClass;
  private final Module mySourceModule;
  private final Module myTargetModule;
  private final Set<Module> myAllowedDependencies = new HashSet<Module>();
  private final Set<String> myVisitedNames = new HashSet<String>();
  private final Queue<AnalysisItem> myAnalysisQueue = new ArrayDeque<AnalysisItem>();
  private final MultiMap<PsiElement, Pair<PsiElement, List<String>>> myBadDependencies = new MultiMap<PsiElement, Pair<PsiElement, List<String>>>();

  private static class AnalysisItem {
    private final List<String> myCallChain = new ArrayList<String>();
    private final PsiElement myElementToAnalyze;

    private AnalysisItem(PsiElement elementToAnalyze, AnalysisItem prevItem) {
      if (prevItem != null) {
        myCallChain.addAll(prevItem.myCallChain);
      }
      myCallChain.add(PsiQNames.getQName(elementToAnalyze));
      myElementToAnalyze = elementToAnalyze;
    }
  }

  public Unchainer(PsiClass psiClass, Module targetModule) {
    myPsiClass = psiClass;
    mySourceModule = ModuleUtil.findModuleForPsiElement(psiClass);
    myTargetModule = targetModule;

    collectAllowedDependencies();
  }

  private void collectAllowedDependencies() {
    myAllowedDependencies.add(myTargetModule);
    ModuleRootManager.getInstance(myTargetModule).orderEntries().recursively().forEachModule(new Processor<Module>() {
      @Override
      public boolean process(Module module) {
        myAllowedDependencies.add(module);
        return true;
      }
    });
  }

  public void run() {
    myAnalysisQueue.add(new AnalysisItem(myPsiClass, null));
    while (!myAnalysisQueue.isEmpty()) {
      AnalysisItem element = myAnalysisQueue.remove();
      analyze(element);
    }
  }

  private void analyze(final AnalysisItem item) {
    String qName = PsiQNames.getQName(item.myElementToAnalyze);
    if (myVisitedNames.contains(qName)) {
      return;
    }
    myVisitedNames.add(qName);

    processDependencies(item.myElementToAnalyze, new PairProcessor<PsiElement, PsiElement>() {
      @Override
      public boolean process(PsiElement referencingElement, PsiElement dependency) {
        Module module = ModuleUtil.findModuleForPsiElement(dependency);
        if (module == mySourceModule) {
          if (isNonStaticMember(dependency)) {
            myAnalysisQueue.offer(new AnalysisItem(((PsiMember) dependency).getContainingClass(), item));
          }
          else {
            myAnalysisQueue.offer(new AnalysisItem(dependency, item));
          }
        }
        else if (module != null && !myAllowedDependencies.contains(module)) {
          if (dependency instanceof PsiMember) {
            while(((PsiMember) dependency).getContainingClass() != null) {
              dependency = ((PsiMember) dependency).getContainingClass();
            }
          }
          myBadDependencies.putValue(dependency, Pair.create(referencingElement, item.myCallChain));
        }
        return true;
      }
    });
  }

  private static boolean isNonStaticMember(PsiElement dependency) {
    if (dependency instanceof PsiMember) {
      PsiMember member = (PsiMember) dependency;
      return member.getContainingClass() != null && !member.hasModifierProperty(PsiModifier.STATIC);
    }
    return false;
  }

  private void processDependencies(PsiElement element, final PairProcessor<PsiElement, PsiElement> processor) {
    element.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        for (PsiReference ref: element.getReferences()) {
          processReference(element, ref);
        }
      }

      private void processReference(PsiElement element, PsiReference ref) {
        PsiElement result = ref.resolve();
        if ((result instanceof PsiClass || result instanceof PsiMember) && !(result instanceof PsiTypeParameter)) {
          processor.process(element, result);
        }
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        PsiExpression qualifierExpression = expression.getQualifierExpression();
        if (qualifierExpression instanceof PsiJavaCodeReferenceElement && qualifierExpression.getReference().resolve() instanceof PsiClass) {
          processReference(expression, expression);
        }
        else {
          visitElement(expression);
        }
      }
    });
  }

  public List<BadDependencyItem> getBadDependencies() {
    List<BadDependencyItem> result = new ArrayList<BadDependencyItem>();
    for (Map.Entry<PsiElement, Collection<Pair<PsiElement, List<String>>>> entry : myBadDependencies.entrySet()) {
      Pair<PsiElement, List<String>> pair = entry.getValue().iterator().next();
      PsiElement usage = pair.first;
      result.add(new BadDependencyItem(PsiQNames.getQName(entry.getKey()), usage, pair.second));
    }
    return result;
  }

  public List<String> getGoodDependencies() {
    List<String> result = new ArrayList<String>();
    Set<String> mergedClasses = new HashSet<String>();
    Set<String> partialClasses = new HashSet<String>();
    ArrayList<String> sortedNames = new ArrayList<String>(myVisitedNames);
    Collections.sort(sortedNames);
    for (String qName : sortedNames) {
      if (qName.contains("#")) {
        String className = PsiQNames.extractClassName(qName);
        if (mergedClasses.contains(className) || result.contains(className)) {
          continue;
        }
        if (!partialClasses.contains(className)) {
          boolean seenAll = seenAllMembers(className);
          if (seenAll) {
            mergedClasses.add(className);
            result.add(className);
            continue;
          }
          partialClasses.add(className);
        }
      }
      result.add(qName);
    }
    return result;
  }

  private boolean seenAllMembers(String className) {
    Project project = myTargetModule.getProject();
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(className, ProjectScope.getProjectScope(project));
    for (PsiMethod psiMethod : aClass.getMethods()) {
      if (!psiMethod.isConstructor() && !myVisitedNames.contains(PsiQNames.getQName(psiMethod))) {
        return false;
      }
    }
    for (PsiField field : aClass.getFields()) {
      if (!myVisitedNames.contains(PsiQNames.getQName(field))) {
        return false;
      }
    }
    for (PsiClass psiClass : aClass.getInnerClasses()) {
      if (!myVisitedNames.contains(PsiQNames.getQName(psiClass))) {
        return false;
      }
    }
    return true;
  }
}
