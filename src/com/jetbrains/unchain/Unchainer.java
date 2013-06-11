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

package com.jetbrains.unchain;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.MultiMap;

import java.util.*;

/**
 * @author yole
 */
public class Unchainer {
  private static final Logger LOG = Logger.getInstance(Unchainer.class);

  private final PsiClass myPsiClass;
  private final Module mySourceModule;
  private final Module myTargetModule;
  private final Set<Module> myAllowedDependencies = new HashSet<Module>();
  private final Set<String> myVisitedNames = new HashSet<String>();
  private final Queue<AnalysisItem> myAnalysisQueue = new ArrayDeque<AnalysisItem>();
  private final MultiMap<PsiElement, Pair<PsiElement, List<String>>> myBadDependencies = new MultiMap<PsiElement, Pair<PsiElement, List<String>>>();
  private Runnable myBadDependencyFoundCallback;
  private List<String> myUnwantedDependencies;

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
    OrderEnumerator orderEnumerator = ModuleRootManager.getInstance(myTargetModule).orderEntries();
    VirtualFile vFile = myPsiClass.getContainingFile().getVirtualFile();
    if (!ProjectFileIndex.SERVICE.getInstance(myTargetModule.getProject()).isInTestSourceContent(vFile)) {
      orderEnumerator = orderEnumerator.productionOnly();
    }
    orderEnumerator.recursively().forEachModule(new Processor<Module>() {
      @Override
      public boolean process(Module module) {
        myAllowedDependencies.add(module);
        return true;
      }
    });
  }

  public void setBadDependencyFoundCallback(Runnable badDependencyFoundCallback) {
    myBadDependencyFoundCallback = badDependencyFoundCallback;
  }

  public void setUnwantedDependencies(List<String> unwantedDependencies) {
    myUnwantedDependencies = unwantedDependencies;
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
        if (module != null && (module != mySourceModule && !myAllowedDependencies.contains(module) ||
            isUnwantedDependency(dependency))) {
          if (dependency instanceof PsiMember) {
            while (((PsiMember) dependency).getContainingClass() != null) {
              dependency = ((PsiMember) dependency).getContainingClass();
            }
          }
          if (myBadDependencyFoundCallback != null) {
            myBadDependencyFoundCallback.run();
          }
          myBadDependencies.putValue(dependency, Pair.create(referencingElement, item.myCallChain));
        }
        else if (module == mySourceModule) {
          if (isNonStaticMember(dependency)) {
            myAnalysisQueue.offer(new AnalysisItem(((PsiMember) dependency).getContainingClass(), item));
          }
          else {
            myAnalysisQueue.offer(new AnalysisItem(dependency, item));
          }
        }
        return true;
      }
    });
  }

  private boolean isUnwantedDependency(PsiElement dependency) {
    if (myUnwantedDependencies.size() == 0) {
      return false;
    }
    PsiClass psiClass = PsiTreeUtil.getParentOfType(dependency, PsiClass.class, false);
    while (psiClass != null) {
      if (myUnwantedDependencies.contains(psiClass.getQualifiedName())) {
        return true;
      }
      psiClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class);
    }
    return false;
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

  public int getBadDependencyCount() {
    return myBadDependencies.keySet().size();
  }

  public List<BadDependencyItem> getBadDependencies() {
    List<BadDependencyItem> result = new ArrayList<BadDependencyItem>();
    for (Map.Entry<PsiElement, Collection<Pair<PsiElement, List<String>>>> entry : myBadDependencies.entrySet()) {
      Pair<PsiElement, List<String>> pair = entry.getValue().iterator().next();
      PsiElement usage = pair.first;
      result.add(new BadDependencyItem(PsiQNames.getQName(entry.getKey()), usage, pair.second));
    }
    Collections.sort(result, new Comparator<BadDependencyItem>() {
      @Override
      public int compare(BadDependencyItem badDependencyItem, BadDependencyItem badDependencyItem2) {
        return badDependencyItem.toString().compareTo(badDependencyItem2.toString());
      }
    });
    return result;
  }

  public List<String> getGoodDependencies() {
    List<String> result = new ArrayList<String>();
    Set<String> mergedClasses = new HashSet<String>();
    Set<String> partialClasses = new HashSet<String>();
    ArrayList<String> sortedNames = new ArrayList<String>(myVisitedNames);
    Collections.sort(sortedNames);
    for (String qName : sortedNames) {
      if (qName.contains("#") || qName.contains("@")) {
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
      else {
        int dot = qName.lastIndexOf('.');
        if (dot >= 0 && result.contains(qName.substring(0, dot))) {
          continue;
        }
      }
      result.add(qName);
    }
    return result;
  }

  private boolean seenAllMembers(String className) {
    Project project = myTargetModule.getProject();
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(className, ProjectScope.getProjectScope(project));
    if (aClass == null) {
      LOG.error("Could not find class " + className);
      return false;
    }
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
