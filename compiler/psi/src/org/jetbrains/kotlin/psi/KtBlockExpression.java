/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.util.IncorrectOperationException;
import kotlin.annotations.jvm.ReadOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.psiUtil.KtPsiUtilKt;

import java.util.Arrays;
import java.util.List;

import static org.jetbrains.kotlin.KtNodeTypes.BLOCK;

public class KtBlockExpression extends LazyParseablePsiElement implements KtElement, KtExpression, KtStatementExpression, PsiModifiableCodeBlock {

    public KtBlockExpression(@Nullable CharSequence text) {
        super(BLOCK, text);
    }

    @Override
    public boolean shouldChangeModificationCount(PsiElement place) {
        // To prevent OutOfBlockModification increase from JavaCodeBlockModificationListener
        return false;
    }

    @NotNull
    @Override
    public Language getLanguage() {
        return KotlinLanguage.INSTANCE;
    }

    @Override
    public String toString() {
        return getNode().getElementType().toString();
    }

    @NotNull
    @Override
    public KtFile getContainingKtFile() {
        PsiFile file = getContainingFile();
        if(!(file instanceof KtFile))  {
            String fileString = (file != null && file.isValid()) ? file.getText() : "";
            throw new IllegalStateException("KtElement not inside KtFile: " + file + fileString +
                                            "for element " + this + " of type " + this.getClass() + " node = " + getNode());
        }
        return (KtFile) file;
    }

    @Override
    public <D> void acceptChildren(@NotNull KtVisitor<Void, D> visitor, D data) {
        KtPsiUtil.visitChildren(this, visitor, data);
    }

    @Override
    public <R, D> R accept(@NotNull KtVisitor<R, D> visitor, D data) {
        return visitor.visitBlockExpression(this, data);
    }

    @Override
    public void delete() throws IncorrectOperationException {
        KtElementUtilsKt.deleteSemicolon(this);
        super.delete();
    }

    @Override
    @SuppressWarnings("deprecation")
    public PsiReference getReference() {
        PsiReference[] references = getReferences();
        if (references.length == 1) return references[0];
        else return null;
    }

    @NotNull
    @Override
    public PsiReference[] getReferences() {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
    }

    @NotNull
    @Override
    public KtElement getPsiOrParent() {
        return this;
    }

    @Override
    public PsiElement getParent() {
        PsiElement substitute = KtPsiUtilKt.getParentSubstitute(this);
        return substitute != null ? substitute : super.getParent();
    }

    @ReadOnly
    @NotNull
    public List<KtExpression> getStatements() {
        return Arrays.asList(findChildrenByClass(KtExpression.class));
    }

    @Nullable
    public TextRange getLastBracketRange() {
        PsiElement rBrace = getRBrace();
        return rBrace != null ? rBrace.getTextRange() : null;
    }

    @Nullable
    public PsiElement getRBrace() {
        return findPsiChildByType(KtTokens.RBRACE);
    }

    @Nullable
    public PsiElement getLBrace() {
        return findPsiChildByType(KtTokens.LBRACE);
    }
}
