package com.jetbrains.unchain;

import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * @author yole
 */
public class BadDependencyItem {
  private final String myText;
  private final PsiElement myPsiElement;
  private final List<String> myCallChain;

  public BadDependencyItem(String text, PsiElement element, List<String> callChain) {
    myText = text;
    myPsiElement = element;
    myCallChain = callChain;
  }

  @Override
  public String toString() {
    return myText;
  }

  public PsiElement getPsiElement() {
    return myPsiElement;
  }

  public Navigatable getNavigatable() {
    return myPsiElement instanceof Navigatable ? (Navigatable) myPsiElement : null;
  }

  public List<String> getCallChain() {
    return myCallChain;
  }
}
