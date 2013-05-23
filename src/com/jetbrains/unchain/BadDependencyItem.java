package com.jetbrains.unchain;

import com.intellij.pom.Navigatable;

import java.util.List;

/**
 * @author yole
 */
public class BadDependencyItem {
  private final String myText;
  private final Navigatable myNavigatable;
  private final List<String> myCallChain;

  public BadDependencyItem(String text, Navigatable navigatable, List<String> callChain) {
    myText = text;
    myNavigatable = navigatable;
    myCallChain = callChain;
  }

  @Override
  public String toString() {
    return myText;
  }

  public Navigatable getNavigatable() {
    return myNavigatable;
  }

  public List<String> getCallChain() {
    return myCallChain;
  }
}
