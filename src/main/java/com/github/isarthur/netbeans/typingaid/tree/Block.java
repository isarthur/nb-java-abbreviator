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
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import org.netbeans.api.java.source.WorkingCopy;

/**
 *
 * @author Arthur Sadykov
 */
public class Block extends InsertableStatementTree {

    private final BlockTree current;

    public Block(TreePath currentPath, MethodCall wrapper, WorkingCopy copy, JavaSourceHelper helper) {
        super(currentPath, wrapper, copy, helper);
        current = (BlockTree) currentPath.getLeaf();
    }

    @Override
    public void insert(Tree tree) {
        BlockTree newBlockTree = current;
        if (tree != null) {
            int insertIndex = helper.findInsertIndexInBlock(newBlockTree);
            if (insertIndex == -1) {
                return;
            }
            insertIndex--;
            newBlockTree = make.removeBlockStatement(newBlockTree, insertIndex);
            newBlockTree = make.insertBlockStatement(newBlockTree, insertIndex, (StatementTree) tree);
        } else {
            int insertIndex = helper.findInsertIndexInBlock(newBlockTree);
            if (insertIndex == -1) {
                return;
            }
            if (helper.isMethodReturnVoid(wrapper.getMethod())) {
                ExpressionStatementTree methodCall = helper.createVoidMethodCall(wrapper);
                newBlockTree = make.insertBlockStatement(newBlockTree, insertIndex, methodCall);
            } else {
                VariableTree methodCall = helper.createMethodCallWithReturnValue(wrapper);
                newBlockTree = make.insertBlockStatement(newBlockTree, insertIndex, methodCall);
            }
        }
        copy.rewrite(current, newBlockTree);
    }
}