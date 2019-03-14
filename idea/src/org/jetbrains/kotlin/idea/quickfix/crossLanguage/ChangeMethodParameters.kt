/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */
@file:Suppress("MissingRecentApi")

package org.jetbrains.kotlin.idea.quickfix.crossLanguage

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.lang.jvm.actions.ChangeParametersRequest
import com.intellij.lang.jvm.actions.ExpectedParameter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JvmPsiConversionHelper
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.quickfix.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.load.java.NOT_NULL_ANNOTATIONS
import org.jetbrains.kotlin.load.java.NULLABLE_ANNOTATIONS
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

internal class ChangeMethodParameters(
    target: KtNamedFunction,
    val request: ChangeParametersRequest
) : KotlinQuickFixAction<KtNamedFunction>(target) {


    override fun getText(): String {

        val target = element ?: return "<not available>"

        val helper = JvmPsiConversionHelper.getInstance(target.project)

        val parametersString = request.expectedParameters.joinToString(", ", "(", ")") { ep ->
            val kotlinType =
                ep.expectedTypes.firstOrNull()?.theType?.let { helper.convertType(it).resolveToKotlinType(target.getResolutionFacade()) }
            "${ep.semanticNames.firstOrNull() ?: "parameter"}: ${kotlinType?.let {
                IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(it)
            } ?: "<error>"}"
        }

        val shortenParameterString = StringUtil.shortenTextWithEllipsis(parametersString, 30, 5)
        return QuickFixBundle.message("change.method.parameters.text", shortenParameterString)
    }

    override fun getFamilyName(): String = QuickFixBundle.message("change.method.parameters.family")

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = element != null && request.isValid


    private fun getParameterDescriptors(
        containingDeclaration: CallableDescriptor,
        currentParameters: List<KtParameter>,
        expectedParameters: List<ExpectedParameter>,
        index: Int = 0,
        collected: List<ValueParameterDescriptor> = ArrayList(expectedParameters.size)
    ): List<ValueParameterDescriptor> {

        val target = element ?: return emptyList()

        val currentHead = currentParameters.firstOrNull()
        val expectedHead = expectedParameters.firstOrNull() ?: return collected

        if (expectedHead is ChangeParametersRequest.ExistingParameterWrapper) {
            val ktParameter = (expectedHead.existingParameter as? KtLightElement<*, *>)?.kotlinOrigin as? KtParameter
            if (ktParameter != null && ktParameter == currentHead)
                return getParameterDescriptors(
                    containingDeclaration,
                    currentParameters.subList(1, currentParameters.size),
                    expectedParameters.subList(1, expectedParameters.size),
                    index + 1,
                    collected + (ktParameter.resolveToDescriptorIfAny(BodyResolveMode.FULL) as ValueParameterDescriptor).run {
                        copy(newOwner = containingDeclaration, newIndex = index, newName = name)
                    }
                )
            else
                throw UnsupportedOperationException("processing of existing params in different order is not implemented yet")
        }

        val helper = JvmPsiConversionHelper.getInstance(target.project)

        val theType = expectedHead.expectedTypes.firstOrNull()?.theType ?: return emptyList()

        val kotlinType = helper.convertType(theType).resolveToKotlinType(target.getResolutionFacade()) ?: return emptyList()


        return getParameterDescriptors(
            containingDeclaration,
            currentParameters,
            expectedParameters.subList(1, expectedParameters.size),
            index + 1,
            collected + ValueParameterDescriptorImpl(
                containingDeclaration, null, index, Annotations.EMPTY,
                Name.identifier(expectedHead.semanticNames.firstOrNull() ?: "param$index"),
                kotlinType, false,
                false, false, null, SourceElement.NO_SOURCE
            )
        )


//        val name = expectedHead.semanticNames.first()
//        val psiType = helper.convertType(expectedHead.expectedTypes.first().theType)
//        val newParameter = factory.createParameter(name, psiType)
//
//        for (annotationRequest in expectedHead.expectedAnnotations) {
//            addAnnotationToModifierList(newParameter.modifierList!!, annotationRequest)
//        }
//
//        if (currentHead == null)
//            target.parameterList.add(newParameter)
//        else
//            target.parameterList.addBefore(newParameter, currentHead)


    }



    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val target = element ?: return
        val functionDescriptor = target.resolveToDescriptorIfAny(BodyResolveMode.FULL) ?: return

        val newFunctionDescriptor = SimpleFunctionDescriptorImpl.create(
            functionDescriptor.containingDeclaration,
            functionDescriptor.annotations,
            functionDescriptor.name,
            functionDescriptor.kind,
            SourceElement.NO_SOURCE
        ).apply {
            initialize(
                functionDescriptor.extensionReceiverParameter?.copy(this),
                functionDescriptor.dispatchReceiverParameter,
                functionDescriptor.typeParameters,
                getParameterDescriptors(this, target.valueParameters, request.expectedParameters),
 //               request.withIndex().map { (index, parameter) ->
//                    ValueParameterDescriptorImpl(
//                        this, null, index, Annotations.EMPTY,
//                        Name.identifier(parameter.first.toString()),
//                        parameter.second, false,
//                        false, false, null, SourceElement.NO_SOURCE
//                    )
//                },
                functionDescriptor.returnType,
                functionDescriptor.modality,
                functionDescriptor.visibility
            )
        }


        val renderer = IdeDescriptorRenderers.SOURCE_CODE.withOptions {
            defaultParameterValueRenderer = null
            annotationArgumentsRenderingPolicy
        }

        val newFunction = KtPsiFactory(project).createFunction(renderer.render(newFunctionDescriptor)).apply {
            valueParameters.forEach { param ->
                param.annotationEntries.forEach { a ->
                    a.typeReference?.run {
                        val fqName = FqName(this.text)
                        if (fqName in (NULLABLE_ANNOTATIONS + NOT_NULL_ANNOTATIONS)) a.delete()
                    }
                }
            }
        }

        val newParameterList = target.valueParameterList!!.replace(newFunction.valueParameterList!!) as KtParameterList
        ShortenReferences.DEFAULT.process(newParameterList)
    }

    companion object {
        fun create(ktNamedFunction: KtNamedFunction, request: ChangeParametersRequest): ChangeMethodParameters? {
            return ChangeMethodParameters(ktNamedFunction, request)
        }
    }


}