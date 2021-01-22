/*
 * Copyright 2021 Arthur Sadykov.
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
package com.github.isarthur.netbeans.editor.typingaid.collector.filter.impl;

import com.github.isarthur.netbeans.editor.typingaid.collector.filter.api.Filter;
import com.github.isarthur.netbeans.editor.typingaid.request.api.CodeCompletionRequest;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.netbeans.api.java.source.WorkingCopy;

/**
 *
 * @author Arthur Sadykov
 */
public class ThrowableFilter implements Filter {

    private final CodeCompletionRequest request;

    public ThrowableFilter(CodeCompletionRequest request) {
        this.request = request;
    }

    @Override
    public List<TypeElement> meetCriteria(List<TypeElement> typeElements) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        Elements elements = workingCopy.getElements();
        TypeMirror throwableTypeMirror = elements.getTypeElement("java.lang.Throwable").asType(); //NOI18N
        Types types = workingCopy.getTypes();
        return typeElements.stream()
                .filter(typeElement -> types.isAssignable(typeElement.asType(), throwableTypeMirror))
                .collect(Collectors.toList());
    }
}
