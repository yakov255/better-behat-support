package com.github.yakov255.betterbehatsupport

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.ui.JBColor
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import java.util.regex.Pattern

class CustomAnnotator : Annotator {

    companion object {
        private val PATTERN = Pattern.compile("в таблице (\\w+) есть данные: -timestamps")
        private val HIGHLIGHT_KEY = TextAttributesKey.createTextAttributesKey(
            "CUSTOM_HIGHLIGHT", TextAttributes(JBColor.RED, null, null, null, 0)
        )
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is GherkinStep) {
            val text = element.text
            val matcher = PATTERN.matcher(text)
            if (matcher.find()) {
                val start = matcher.start(1)
                val end = matcher.end(1)
                val textRange = TextRange(element.textRange.startOffset + start, element.textRange.startOffset + end)
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(textRange)
                    .textAttributes(HIGHLIGHT_KEY)
                    .create()
            }
        }
    }
}
