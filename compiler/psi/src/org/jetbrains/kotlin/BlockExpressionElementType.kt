/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.PsiBuilderFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.ICompositeElementType
import com.intellij.psi.tree.IErrorCounterReparseableElementType
import org.jetbrains.kotlin.KtNodeTypes.BLOCK_CODE_FRAGMENT
import org.jetbrains.kotlin.KtNodeTypes.FUNCTION_LITERAL
import org.jetbrains.kotlin.KtNodeTypes.SCRIPT
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.parsing.KotlinParser
import org.jetbrains.kotlin.psi.KtBlockExpression

class BlockExpressionElementType : IErrorCounterReparseableElementType("BLOCK", KotlinLanguage.INSTANCE), ICompositeElementType {

    override fun createCompositeNode() = KtBlockExpression(null)

    override fun createNode(text: CharSequence?) = KtBlockExpression(text)

    override fun isParsable(parent: ASTNode?, buffer: CharSequence, fileLanguage: Language, project: Project) =
        fileLanguage == KotlinLanguage.INSTANCE &&
                BlockExpressionElementType.isAllowedParentNode(parent) &&
                BlockExpressionElementType.isBlock(buffer) &&
                super.isParsable(buffer, fileLanguage, project)

    override fun getErrorsCount(seq: CharSequence, fileLanguage: Language, project: Project): Int {
        val lexer = KotlinLexer()

        lexer.start(seq)
        if (lexer.tokenType !== KtTokens.LBRACE) return IErrorCounterReparseableElementType.FATAL_ERROR
        lexer.advance()
        var balance = 1
        while (lexer.tokenType != KtTokens.EOF) {
            val type = lexer.tokenType ?: break
            if (balance == 0) {
                return IErrorCounterReparseableElementType.FATAL_ERROR
            }
            if (type === KtTokens.LBRACE) {
                balance++
            } else if (type === KtTokens.RBRACE) {
                balance--
            }
            lexer.advance()
        }
        return balance
    }

    override fun parseContents(chameleon: ASTNode): ASTNode {
        val project = chameleon.psi.project
        val builder = PsiBuilderFactory.getInstance().createBuilder(
            project, chameleon, null, KotlinLanguage.INSTANCE, chameleon.chars
        )

        return KotlinParser.parseBlockExpression(builder).firstChildNode
    }

    companion object {

        private fun isAllowedParentNode(node: ASTNode?) =
            node != null &&
                    SCRIPT != node.elementType &&
                    FUNCTION_LITERAL != node.elementType &&
                    BLOCK_CODE_FRAGMENT != node.elementType

        fun isBlock(blockText: CharSequence): Boolean {
            val lexer = KotlinLexer()
            lexer.start(blockText)

            if (lexer.tokenType != KtTokens.LBRACE) return false

            lexer.advance()

            while (lexer.tokenType != KtTokens.EOF) {
                if (lexer.tokenType == KtTokens.LBRACE) return true
                if (lexer.tokenType == KtTokens.RBRACE) return true
                if (lexer.tokenType == KtTokens.ARROW) return false
                lexer.advance()
            }
            return false
        }
    }
}