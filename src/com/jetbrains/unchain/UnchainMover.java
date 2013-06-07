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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.MoveClassesOrPackagesRefactoring;
import com.intellij.refactoring.MoveDestination;
import com.intellij.util.containers.MultiMap;

import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class UnchainMover {
  private final Module myTargetModule;
  private final List<String> myQNames;

  public UnchainMover(Module targetModule, List<String> qNames) {
    myTargetModule = targetModule;
    myQNames = qNames;
  }

  public void run() {
    Project project = myTargetModule.getProject();
    VirtualFile[] sourceRoots = ModuleRootManager.getInstance(myTargetModule).getSourceRoots();

    MultiMap<String, PsiClass> map = groupClassesByPackage(project);

    JavaRefactoringFactory factory = JavaRefactoringFactory.getInstance(project);
    for (String packageName : map.keySet()) {
      MoveDestination destination = factory.createSourceRootMoveDestination(packageName, sourceRoots[0]);
      Collection<PsiClass> elements = map.get(packageName);
      PsiElement[] elementArray = elements.toArray(new PsiElement[elements.size()]);
      MoveClassesOrPackagesRefactoring refactoring = factory.createMoveClassesOrPackages(elementArray, destination);
      refactoring.run();
    }
  }

  private MultiMap<String, PsiClass> groupClassesByPackage(Project project) {
    MultiMap<String, PsiClass> map = new MultiMap<String, PsiClass>();
    for (String qName : myQNames) {
      PsiElement psiElement = PsiQNames.findElementByQName(project, qName);
      if (!(psiElement instanceof PsiClass)) {
        throw new UnsupportedOperationException("Moving is only supported for classes");
      }
      PsiClass psiClass = (PsiClass) psiElement;
      if (!(psiClass.getContainingFile() instanceof PsiJavaFile)) {
        throw new UnsupportedOperationException("Class not in a Java file");
      }
      PsiJavaFile javaFile = (PsiJavaFile) psiClass.getContainingFile();
      map.putValue(javaFile.getPackageName(), psiClass);
    }
    return map;
  }
}
