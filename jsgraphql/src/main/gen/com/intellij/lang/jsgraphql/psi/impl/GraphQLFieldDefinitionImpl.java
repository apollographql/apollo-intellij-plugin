// This is a generated file. Not intended for manual editing.
package com.intellij.lang.jsgraphql.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.lang.jsgraphql.psi.GraphQLElementTypes.*;
import com.intellij.lang.jsgraphql.psi.*;

public class GraphQLFieldDefinitionImpl extends GraphQLNamedElementImpl implements GraphQLFieldDefinition {

  public GraphQLFieldDefinitionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull GraphQLVisitorBase visitor) {
    visitor.visitFieldDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof GraphQLVisitorBase) accept((GraphQLVisitorBase)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public GraphQLArgumentsDefinition getArgumentsDefinition() {
    return findChildByClass(GraphQLArgumentsDefinition.class);
  }

  @Override
  @Nullable
  public GraphQLDescription getDescription() {
    return findChildByClass(GraphQLDescription.class);
  }

  @Override
  @Nullable
  public GraphQLType getType() {
    return findChildByClass(GraphQLType.class);
  }

  @Override
  @NotNull
  public List<GraphQLDirective> getDirectives() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, GraphQLDirective.class);
  }

  @Override
  @NotNull
  public GraphQLIdentifier getNameIdentifier() {
    return findNotNullChildByClass(GraphQLIdentifier.class);
  }

}
