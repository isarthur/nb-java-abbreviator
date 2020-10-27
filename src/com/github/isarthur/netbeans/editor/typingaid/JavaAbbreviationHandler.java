/*
 * Copyright 2020 Arthur Sadykov.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.isarthur.netbeans.editor.typingaid;

import com.github.isarthur.netbeans.editor.typingaid.spi.AbbreviationHandler;
import com.github.isarthur.netbeans.editor.typingaid.spi.Abbreviation;
import com.github.isarthur.netbeans.editor.typingaid.spi.CodeFragment;
import com.github.isarthur.netbeans.editor.typingaid.codefragment.FieldAccess;
import com.github.isarthur.netbeans.editor.typingaid.codefragment.Keyword;
import com.github.isarthur.netbeans.editor.typingaid.codefragment.LocalElement;
import com.github.isarthur.netbeans.editor.typingaid.codefragment.MethodCall;
import com.github.isarthur.netbeans.editor.typingaid.codefragment.Type;
import com.github.isarthur.netbeans.editor.typingaid.settings.Settings;
import com.github.isarthur.netbeans.editor.typingaid.ui.GenerateCodePanel;
import com.github.isarthur.netbeans.editor.typingaid.ui.PopupUtil;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.Element;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.openide.util.Exceptions;

/**
 *
 * @author Arthur Sadykov
 */
public class JavaAbbreviationHandler implements AbbreviationHandler {

    private final JavaSourceHelper helper;
    private final JTextComponent component;
    private final Document document;

    public JavaAbbreviationHandler(JavaSourceHelper helper) {
        this.helper = helper;
        this.component = helper.getComponent();
        this.document = helper.getDocument();
    }

    @Override
    public List<CodeFragment> process(Abbreviation abbreviation) {
        String abbreviationContent = abbreviation.getContent();
        helper.setTypedAbbreviation(abbreviationContent);
        helper.collectLocalElements(abbreviation.getStartOffset());
        if (abbreviation.getContent().contains(".")) { //NOI18N
            String scopeAbbreviation = abbreviationContent.substring(0, abbreviationContent.indexOf('.'));
            String nameAbbreviation = abbreviationContent.substring(abbreviationContent.indexOf('.') + 1);
            List<Element> elements = helper.getElementsByAbbreviation(scopeAbbreviation);
            if (!elements.isEmpty()) {
                List<MethodCall> methodCalls = Collections.emptyList();
                if (Settings.getSettingForMethodInvocation()) {
                    methodCalls = helper.findMethodCalls(elements, nameAbbreviation);
                }
                List<MethodCall> staticMethodCalls = Collections.emptyList();
                if (Settings.getSettingForStaticMethodInvocation()) {
                    staticMethodCalls = helper.findStaticMethodCalls(scopeAbbreviation, nameAbbreviation);
                }
                List<FieldAccess> fieldAccesses = Collections.emptyList();
                if (Settings.getSettingForStaticFieldAccess()) {
                    fieldAccesses = helper.findFieldAccesses(scopeAbbreviation, nameAbbreviation);
                }
                int matchesCount = methodCalls.size() + staticMethodCalls.size() + fieldAccesses.size();
                switch (matchesCount) {
                    case 0: {
                        return null;
                    }
                    case 1: {
                        if (!methodCalls.isEmpty()) {
                            return helper.insertMethodCall(methodCalls.get(0));
                        } else if (!staticMethodCalls.isEmpty()) {
                            return helper.insertMethodCall(staticMethodCalls.get(0));
                        } else {
                            return helper.insertFieldAccess(fieldAccesses.get(0));
                        }
                    }
                    default: {
                        List<CodeFragment> codeFragments = new ArrayList<>();
                        codeFragments.addAll(methodCalls);
                        codeFragments.addAll(staticMethodCalls);
                        codeFragments.addAll(fieldAccesses);
                        showPopup(codeFragments);
                        return codeFragments;
                    }
                }
            } else {
                List<MethodCall> staticMethodCalls = Collections.emptyList();
                if (Settings.getSettingForStaticMethodInvocation()) {
                    staticMethodCalls = helper.findStaticMethodCalls(scopeAbbreviation, nameAbbreviation);
                }
                List<FieldAccess> fieldAccesses = Collections.emptyList();
                if (Settings.getSettingForStaticFieldAccess()) {
                    fieldAccesses = helper.findFieldAccesses(scopeAbbreviation, nameAbbreviation);
                }
                int matchesCount = staticMethodCalls.size() + fieldAccesses.size();
                switch (matchesCount) {
                    case 0: {
                        return null;
                    }
                    case 1: {
                        if (!staticMethodCalls.isEmpty()) {
                            return helper.insertMethodCall(staticMethodCalls.get(0));
                        } else {
                            return helper.insertFieldAccess(fieldAccesses.get(0));
                        }
                    }
                    default: {
                        List<CodeFragment> codeFragments = new ArrayList<>();
                        codeFragments.addAll(staticMethodCalls);
                        codeFragments.addAll(fieldAccesses);
                        showPopup(codeFragments);
                        return codeFragments;
                    }
                }
            }
        } else {
            if (helper.isMemberSelection()) {
                List<MethodCall> chainedMethodCalls = Collections.emptyList();
                if (Settings.getSettingForChainedMethodInvocation()) {
                    chainedMethodCalls = helper.findChainedMethodCalls(abbreviationContent);
                }
                int matchesCount = chainedMethodCalls.size();
                switch (matchesCount) {
                    case 0: {
                        return null;
                    }
                    case 1: {
                        return helper.insertChainedMethodCall(chainedMethodCalls.get(0));
                    }
                    default: {
                        ArrayList<CodeFragment> codeFragments = new ArrayList<>(chainedMethodCalls);
                        showPopup(codeFragments);
                        return codeFragments;
                    }
                }
            } else {
                List<LocalElement> localElements = Collections.emptyList();
                if (Settings.getSettingForLocalVariable()) {
                    localElements = helper.findLocalElements(abbreviationContent);
                }
                List<MethodCall> localMethodCalls = Collections.emptyList();
                if (Settings.getSettingForLocalMethodInvocation()) {
                    localMethodCalls = helper.findLocalMethodCalls(abbreviationContent);
                }
                List<Type> types = Collections.emptyList();
                if (Settings.getSettingForExternalType()) {
                    types = helper.findTypes(abbreviationContent);
                }
                Keyword keyword = null;
                if (Settings.getSettingForKeyword()) {
                    keyword = helper.findKeyword(abbreviationContent);
                }
                int matchesCount = localElements.size() + localMethodCalls.size() + types.size()
                        + (keyword == null ? 0 : 1);
                switch (matchesCount) {
                    case 0: {
                        return null;
                    }
                    case 1: {
                        if (!localElements.isEmpty()) {
                            return helper.insertLocalElement(localElements.get(0));
                        } else if (!localMethodCalls.isEmpty()) {
                            return helper.insertMethodCall(localMethodCalls.get(0));
                        } else if (!types.isEmpty()) {
                            return helper.insertType(types.get(0));
                        } else {
                            return keyword != null ? helper.insertKeyword(keyword) : null;
                        }
                    }
                    default: {
                        List<CodeFragment> codeFragments = new ArrayList<>();
                        codeFragments.addAll(localElements);
                        codeFragments.addAll(localMethodCalls);
                        codeFragments.addAll(types);
                        if (keyword != null) {
                            codeFragments.add(keyword);
                        }
                        showPopup(codeFragments);
                        return codeFragments;
                    }
                }
            }
        }
    }

    private void showPopup(List<CodeFragment> codeFragments) {
        SwingUtilities.invokeLater(() -> {
            try {
                Rectangle caretRectangle = component.modelToView(component.getCaretPosition());
                if (caretRectangle == null) {
                    return;
                }
                Point where = new Point((int) caretRectangle.getX(), (int) (caretRectangle.getY()
                        + caretRectangle.getHeight()));
                SwingUtilities.convertPointToScreen(where, component);
                PopupUtil.showPopup(
                        new GenerateCodePanel(component, codeFragments, helper),
                        (Frame) SwingUtilities.getAncestorOfClass(Frame.class, component),
                        where.getX(),
                        where.getY(),
                        true,
                        caretRectangle.getHeight());
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
    }

    public Document getDocument() {
        return document;
    }
}
