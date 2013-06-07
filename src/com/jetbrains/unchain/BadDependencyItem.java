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
