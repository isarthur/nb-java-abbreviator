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
package com.github.isarthur.netbeans.typingaid.tree;

import com.github.isarthur.netbeans.typingaid.JavaSourceHelper;
import com.github.isarthur.netbeans.typingaid.codefragment.MethodCall;
import com.github.isarthur.netbeans.typingaid.Utilities;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.api.lexer.TokenSequence;

/**
 *
 * @author Arthur Sadykov
 */
public class MethodInvocation extends InsertableExpressionTree {

    private final MethodInvocationTree current;

    public MethodInvocation(TreePath currentPath, MethodCall wrapper, WorkingCopy copy,
            JavaSourceHelper helper) {
        super(currentPath, wrapper, copy, helper);
        current = (MethodInvocationTree) currentPath.getLeaf();
    }

    @Override
    public void insert(Tree tree) {
        MethodInvocationTree methodInvocationTree = current;
        int insertIndex = helper.findInsertIndexForInvocationArgument(methodInvocationTree);
        if (insertIndex == -1) {
            return;
        }
        ExpressionTree methodCall = helper.createMethodCallWithoutReturnValue(wrapper);
        if (tree != null) {
            String expression = null;
            if (!methodInvocationTree.getArguments().isEmpty()) {
                expression = methodInvocationTree.getArguments().get(insertIndex).toString();
                methodInvocationTree = make.removeMethodInvocationArgument(methodInvocationTree, insertIndex);
            }
            if (expression == null) {
                return;
            }
            expression = Utilities.createExpression(expression, methodCall);
            TokenHierarchy<?> th = copy.getTokenHierarchy();
            TokenSequence<?> ts = th.tokenSequence();
            ts.move(helper.getCaretPosition());
            skipNextWhitespaces(ts);
            TokenId id = ts.token().id();
            methodInvocationTree = make.insertMethodInvocationArgument(
                    methodInvocationTree,
                    insertIndex,
                    make.Identifier(expression + ((id == JavaTokenId.COMMA) ? ", " : ")")));
        } else {
            if (!methodInvocationTree.getArguments().isEmpty()) {
                methodInvocationTree = make.removeMethodInvocationArgument(methodInvocationTree, insertIndex);
            }
            methodInvocationTree = make.insertMethodInvocationArgument(
                    methodInvocationTree,
                    insertIndex,
                    methodCall);
        }
        copy.rewrite(current, methodInvocationTree);
    }

    private void skipNextWhitespaces(TokenSequence<?> ts) {
        while (ts.moveNext()) {
            TokenId id = ts.token().id();
            if (id != JavaTokenId.WHITESPACE) {
                break;
            }
        }
    }
}