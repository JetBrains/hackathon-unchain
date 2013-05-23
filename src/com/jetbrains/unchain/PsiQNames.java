package com.jetbrains.unchain;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;

/**
 * @author yole
 */
public class PsiQNames {
  public static String getQName(PsiElement element) {
    if (element instanceof PsiClass) {
      return ((PsiClass) element).getQualifiedName();
    }
    if (element instanceof PsiMember) {
      PsiMember member = (PsiMember) element;
      PsiClass containingClass = member.getContainingClass();
      String qName = containingClass.getQualifiedName() + "#" + member.getName();
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

  private static String collectParameterTypes(PsiMethod method) {
    return StringUtil.join(method.getParameterList().getParameters(), new Function<PsiParameter, String>() {
      @Override
      public String fun(PsiParameter psiParameter) {
        return psiParameter.getType().getPresentableText();
      }
    }, ",");
  }

  public static PsiElement findElementByQName(Project project, String qName) {
    int hash = qName.indexOf('#');
    String className = hash >= 0 ? qName.substring(0, hash) : qName;
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.projectScope(project));
    if (aClass == null) {
      return null;
    }
    PsiElement target = aClass;
    if (hash >= 0) {
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
            target = psiMethod;
            break;
          }
        }
        if (!(target instanceof PsiMethod)) {
          target = methodsByName[0];
        }
      }
      else {
        PsiField field = aClass.findFieldByName(memberName, false);
        if (field != null) {
          target = field;
        }
      }
    }
    return target;
  }
}
