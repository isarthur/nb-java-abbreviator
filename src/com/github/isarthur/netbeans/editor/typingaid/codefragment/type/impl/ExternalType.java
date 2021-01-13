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
package com.github.isarthur.netbeans.editor.typingaid.codefragment.type.impl;

import com.github.isarthur.netbeans.editor.typingaid.codefragment.type.api.AbstractType;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.java.source.ElementHandle;

/**
 *
 * @author Arthur Sadykov
 */
public class ExternalType extends AbstractType {

    public ExternalType(ElementHandle<TypeElement> identifier, int typeParametersCount) {
        super(identifier, typeParametersCount);
    }

    @Override
    public Kind getKind() {
        return Kind.EXTERNAL_TYPE;
    }
}
