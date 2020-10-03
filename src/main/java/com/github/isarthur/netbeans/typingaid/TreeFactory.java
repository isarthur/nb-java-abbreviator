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
package com.github.isarthur.netbeans.typingaid;

import com.github.isarthur.netbeans.typingaid.codefragment.MethodCall;
import com.github.isarthur.netbeans.typingaid.constants.ConstantDataManager;
import com.github.isarthur.netbeans.typingaid.tree.Assert;
import com.github.isarthur.netbeans.typingaid.tree.Assignment;
import com.github.isarthur.netbeans.typingaid.tree.Block;
import com.github.isarthur.netbeans.typingaid.tree.DoWhile;
import com.github.isarthur.netbeans.typingaid.tree.EnhancedFor;
import com.github.isarthur.netbeans.typingaid.tree.ExpressionStatement;
import com.github.isarthur.netbeans.typingaid.tree.For;
import com.github.isarthur.netbeans.typingaid.tree.If;
import com.github.isarthur.netbeans.typingaid.tree.InsertableExpressionTree;
import com.github.isarthur.netbeans.typingaid.tree.InsertableTree;
import com.github.isarthur.netbeans.typingaid.tree.LambdaExpression;
import com.github.isarthur.netbeans.typingaid.tree.MethodInvocation;
import com.github.isarthur.netbeans.typingaid.tree.NewClass;
import com.github.isarthur.netbeans.typingaid.tree.NullInsertableTree;
import com.github.isarthur.netbeans.typingaid.tree.Parenthesized;
import com.github.isarthur.netbeans.typingaid.tree.Return;
import com.github.isarthur.netbeans.typingaid.tree.Switch;
import com.github.isarthur.netbeans.typingaid.tree.Synchronized;
import com.github.isarthur.netbeans.typingaid.tree.Variable;
import com.github.isarthur.netbeans.typingaid.tree.While;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import static java.util.Objects.requireNonNull;
import org.netbeans.api.java.source.WorkingCopy;

/**
 *
 * @author Arthur Sadykov
 */
public class TreeFactory {

    private TreeFactory() {
    }

    public static InsertableTree create(TreePath currentPath, MethodCall wrapper, WorkingCopy copy,
            JavaSourceHelper helper) {
        requireNonNull(currentPath, () -> String.format(ConstantDataManager.ARGUMENT_MUST_BE_NON_NULL, "currentPath"));
        requireNonNull(wrapper, () -> String.format(ConstantDataManager.ARGUMENT_MUST_BE_NON_NULL, "wrapper"));
        requireNonNull(copy, () -> String.format(ConstantDataManager.ARGUMENT_MUST_BE_NON_NULL, "copy"));
        requireNonNull(helper, () -> String.format(ConstantDataManager.ARGUMENT_MUST_BE_NON_NULL, "helper"));
        Tree currentTree = currentPath.getLeaf();
        Tree.Kind kind = currentTree.getKind();
        switch (kind) {
            case AND:
            case AND_ASSIGNMENT:
            case CONDITIONAL_AND:
            case CONDITIONAL_OR:
            case DIVIDE:
            case DIVIDE_ASSIGNMENT:
            case EQUAL_TO:
            case GREATER_THAN:
            case GREATER_THAN_EQUAL:
            case LEFT_SHIFT:
            case LEFT_SHIFT_ASSIGNMENT:
            case LESS_THAN:
            case LESS_THAN_EQUAL:
            case LOGICAL_COMPLEMENT:
            case MEMBER_SELECT:
            case MINUS:
            case MINUS_ASSIGNMENT:
            case MULTIPLY:
            case MULTIPLY_ASSIGNMENT:
            case NOT_EQUAL_TO:
            case OR:
            case OR_ASSIGNMENT:
            case PLUS:
            case PLUS_ASSIGNMENT:
            case REMAINDER:
            case REMAINDER_ASSIGNMENT:
            case RIGHT_SHIFT:
            case RIGHT_SHIFT_ASSIGNMENT:
            case UNARY_MINUS:
            case UNARY_PLUS:
            case UNSIGNED_RIGHT_SHIFT:
            case UNSIGNED_RIGHT_SHIFT_ASSIGNMENT:
            case XOR:
            case XOR_ASSIGNMENT:
                return new InsertableExpressionTree(currentPath, wrapper, copy, helper);
            case ASSERT:
                return new Assert(currentPath, wrapper, copy, helper);
            case ASSIGNMENT:
                return new Assignment(currentPath, wrapper, copy, helper);
            case BLOCK:
                return new Block(currentPath, wrapper, copy, helper);
            case DO_WHILE_LOOP:
                return new DoWhile(currentPath, wrapper, copy, helper);
            case ENHANCED_FOR_LOOP:
                return new EnhancedFor(currentPath, wrapper, copy, helper);
            case EXPRESSION_STATEMENT:
                return new ExpressionStatement(currentPath, wrapper, copy, helper);
            case FOR_LOOP:
                return new For(currentPath, wrapper, copy, helper);
            case IF:
                return new If(currentPath, wrapper, copy, helper);
            case LAMBDA_EXPRESSION:
                return new LambdaExpression(currentPath, wrapper, copy, helper);
            case METHOD_INVOCATION:
                return new MethodInvocation(currentPath, wrapper, copy, helper);
            case NEW_CLASS:
                return new NewClass(currentPath, wrapper, copy, helper);
            case PARENTHESIZED:
                return new Parenthesized(currentPath, wrapper, copy, helper);
            case RETURN:
                return new Return(currentPath, wrapper, copy, helper);
            case SYNCHRONIZED:
                return new Synchronized(currentPath, wrapper, copy, helper);
            case SWITCH:
                return new Switch(currentPath, wrapper, copy, helper);
            case VARIABLE:
                return new Variable(currentPath, wrapper, copy, helper);
            case WHILE_LOOP:
                return new While(currentPath, wrapper, copy, helper);
            default:
                return NullInsertableTree.getInstance(currentPath, wrapper, copy, helper);
        }
    }
}