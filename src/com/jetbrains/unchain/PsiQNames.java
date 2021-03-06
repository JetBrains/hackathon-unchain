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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;

/**
 * @author yole
 */
public class PsiQNames {
  public static String getQName(final PsiElement element) {
    if (element instanceof PsiClass) {
      return getClassQName((PsiClass) element);
    }
    if (element instanceof PsiMember) {
      PsiMember member = (PsiMember) element;
      PsiClass containingClass = member.getContainingClass();
      String qName = getClassQName(containingClass) + "#" + member.getName();
      if (member instanceof PsiMethod) {
        PsiMethod[] methodsByName = containingClass.findMethodsByName(member.getName(), false);
        if (methodsByName.length > 1) {
          return qName + "(" + collectParameterTypes((PsiMethod) member) + ")";
        }
      }
      return qName;
    }
    throw new UnsupportedOperationException("Don't know how to build qname for " + element);
  }

  private static String getClassQName(PsiClass element) {
    String qualifiedName = element.getQualifiedName();
    if (qualifiedName != null) {
      return qualifiedName;
    }
    PsiClass topLevelClass = element;
    while (PsiTreeUtil.getParentOfType(topLevelClass, PsiClass.class) != null) {
      topLevelClass = PsiTreeUtil.getParentOfType(topLevelClass, PsiClass.class);
    }
    return topLevelClass.getQualifiedName() + "@" + element.getTextRange().getStartOffset();
  }

  private static String collectParameterTypes(PsiMethod method) {
    return StringUtil.join(method.getParameterList().getParameters(), new Function<PsiParameter, String>() {
      @Override
      public String fun(PsiParameter psiParameter) {
        return psiParameter.getType().getPresentableText();
      }
    }, ",");
  }

  public static PsiClass findClassByQName(Project project, String qName) {
    String className = extractClassName(qName);
    return JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.projectScope(project));
  }

  public static String extractClassName(String qName) {
    int hash = StringUtil.indexOfAny(qName, "#@");
    return hash >= 0 ? qName.substring(0, hash) : qName;
  }

  public static PsiElement findElementByQName(Project project, String qName) {
    if (qName == null) {
      return null;
    }
    PsiClass aClass = findClassByQName(project, qName);
    if (aClass == null) {
      return null;
    }
    int hash = StringUtil.indexOfAny(qName, "#@");
    PsiElement target = aClass;
    if (hash >= 0) {
      if (qName.charAt(hash) == '#') {
        target = findByHash(qName, aClass, hash);
      } else {
        int offset = Integer.parseInt(qName.substring(hash + 1));
        target = PsiTreeUtil.getParentOfType(aClass.getContainingFile().findElementAt(offset), PsiClass.class, false);
      }
    }
    return target;
  }

  private static PsiElement findByHash(String qName, PsiClass aClass, int hash) {
    String hashWithArgs = qName.substring(hash + 1);
    String memberName = hashWithArgs;
    int lparen = hashWithArgs.indexOf('(');
    if (lparen > 0) {
      memberName = hashWithArgs.substring(0, lparen);
    }
    PsiMethod[] methodsByName = aClass.findMethodsByName(memberName, false);
    if (methodsByName.length > 0) {
      for (PsiMethod psiMethod : methodsByName) {
        if (getQName(psiMethod).equals(qName)) {
          return psiMethod;
        }
      }
      return methodsByName[0];
    }
    PsiField field = aClass.findFieldByName(memberName, false);
    if (field != null) {
      return field;
    }
    PsiClass innerClass = aClass.findInnerClassByName(memberName, false);
    if (innerClass != null) {
      return innerClass;
    }
    return aClass;
  }
}
