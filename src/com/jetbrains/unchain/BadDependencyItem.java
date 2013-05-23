package com.jetbrains.unchain;

import com.intellij.pom.Navigatable;

/**
 * @author yole
 */
public class BadDependencyItem {
  private final String myText;
  private final Navigatable myNavigatable;

  public BadDependencyItem(String text, Navigatable navigatable) {
    myText = text;
    myNavigatable = navigatable;
  }

  @Override
  public String toString() {
    return myText;
  }

  public Navigatable getNavigatable() {
    return myNavigatable;
  }
}
