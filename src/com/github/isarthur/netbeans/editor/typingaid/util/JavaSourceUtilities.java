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
package com.github.isarthur.netbeans.editor.typingaid.util;

import com.github.isarthur.netbeans.editor.typingaid.abbreviation.api.Abbreviation;
import com.github.isarthur.netbeans.editor.typingaid.constants.ConstantDataManager;
import com.github.isarthur.netbeans.editor.typingaid.preferences.Preferences;
import com.github.isarthur.netbeans.editor.typingaid.request.api.CodeCompletionRequest;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import static com.sun.source.tree.Tree.Kind.BLOCK;
import static com.sun.source.tree.Tree.Kind.CASE;
import static com.sun.source.tree.Tree.Kind.METHOD;
import static com.sun.source.tree.Tree.Kind.SWITCH;
import static com.sun.source.tree.Tree.Kind.VARIABLE;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.swing.text.Document;
import org.netbeans.api.java.lexer.JavaTokenId;
import static org.netbeans.api.java.lexer.JavaTokenId.EXTENDS;
import static org.netbeans.api.java.lexer.JavaTokenId.IDENTIFIER;
import static org.netbeans.api.java.lexer.JavaTokenId.IMPLEMENTS;
import static org.netbeans.api.java.lexer.JavaTokenId.LBRACE;
import static org.netbeans.api.java.lexer.JavaTokenId.THROWS;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CodeStyle;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.ElementUtilities;
import org.netbeans.api.java.source.TreeUtilities;
import org.netbeans.api.java.source.TypeUtilities;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.api.java.source.ui.ElementHeaders;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.api.lexer.TokenSequence;
import org.openide.util.Exceptions;

/**
 *
 * @author Arthur Sadykov
 */
public class JavaSourceUtilities {

    private JavaSourceUtilities() {
    }

    private static int findInsertIndexForArgument(List<? extends ExpressionTree> arguments) {
        if (arguments.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i).toString();
            if (argument.contains(ConstantDataManager.PARENTHESIZED_ERROR)
                    || argument.contains(ConstantDataManager.ANGLED_ERROR)) {
                return i;
            }
        }
        return -1;
    }

    public static int findInsertIndexForInvocationArgument(MethodInvocationTree methodInvocationTree) {
        return findInsertIndexForArgument(methodInvocationTree.getArguments());
    }

    public static int findInsertIndexForInvocationArgument(NewClassTree newClassTree) {
        return findInsertIndexForArgument(newClassTree.getArguments());
    }

    public static int findInsertIndexForMethodParameter(MethodTree methodTree) {
        List<? extends VariableTree> parameters = methodTree.getParameters();
        if (parameters.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < parameters.size(); i++) {
            String argument = parameters.get(i).toString();
            if (argument.contains(ConstantDataManager.PARENTHESIZED_ERROR)
                    || argument.contains(ConstantDataManager.ANGLED_ERROR)) {
                return i;
            }
        }
        return -1;
    }

    public static int findInsertIndexForParameterizedType(
            ParameterizedTypeTree parameterizedTypeTree, CodeCompletionRequest request) {
        List<? extends Tree> typeArguments = parameterizedTypeTree.getTypeArguments();
        WorkingCopy workingCopy = request.getWorkingCopy();
        TreeUtilities treeUtilities = workingCopy.getTreeUtilities();
        int i;
        for (i = 0; i < typeArguments.size(); i++) {
            if (treeUtilities.hasError(typeArguments.get(i))) {
                break;
            }
        }
        return i != typeArguments.size() ? i : -1;
    }

    private static Set<ElementKind> getAllLocalElementKinds() {
        Set<ElementKind> elementKinds = new HashSet<>(Byte.SIZE);
        if (Preferences.getLocalVariableFlag()) {
            elementKinds.add(ElementKind.LOCAL_VARIABLE);
        }
        if (Preferences.getFieldFlag()) {
            elementKinds.add(ElementKind.FIELD);
        }
        if (Preferences.getParameterFlag()) {
            elementKinds.add(ElementKind.PARAMETER);
        }
        if (Preferences.getEnumConstantFlag()) {
            elementKinds.add(ElementKind.ENUM_CONSTANT);
        }
        if (Preferences.getExceptionParameterFlag()) {
            elementKinds.add(ElementKind.EXCEPTION_PARAMETER);
        }
        if (Preferences.getResourceVariableFlag()) {
            elementKinds.add(ElementKind.RESOURCE_VARIABLE);
        }
        return Collections.unmodifiableSet(elementKinds);
    }

    public static List<ExecutableElement> getConstructors(Element type, WorkingCopy copy) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(type.getEnclosedElements());
        return Collections.unmodifiableList(constructors);
    }

    public static List<ExecutableElement> filterAccessibleConstructors(
            List<ExecutableElement> constructors, WorkingCopy copy) {
        CompilationUnitTree compilationUnit = copy.getCompilationUnit();
        List<? extends Tree> typeDecls = compilationUnit.getTypeDecls();
        if (typeDecls.isEmpty()) {
            return Collections.emptyList();
        }
        Tree topLevelClassTree = typeDecls.get(0);
        Trees trees = copy.getTrees();
        Element topLevelTypeElement = trees.getElement(TreePath.getPath(compilationUnit, topLevelClassTree));
        Elements elements = copy.getElements();
        ElementUtilities elementUtilities = copy.getElementUtilities();
        ExpressionTree packageName = compilationUnit.getPackageName();
        PackageElement packageElement;
        if (packageName == null) {
            packageElement = null;
        } else {
            packageElement = elements.getPackageElement(packageName.toString());
        }
        List<ExecutableElement> accessibleConstructors = constructors.stream()
                .filter(constructor -> {
                    PackageElement constructorPackageElement = elements.getPackageOf(constructor);
                    TypeElement constructorOutermostTypeElement = elementUtilities.outermostTypeElement(constructor);
                    if (!elements.isDeprecated(constructor)) {
                        if (constructor.getModifiers().contains(Modifier.PUBLIC)) {
                            return true;
                        } else if (topLevelTypeElement.equals(constructorOutermostTypeElement)) {
                            return true;
                        } else if (!constructor.getModifiers().contains(Modifier.PRIVATE)) {
                            if (packageElement == null) {
                                return false;
                            } else {
                                if (packageElement.equals(constructorPackageElement)) {
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
        return Collections.unmodifiableList(accessibleConstructors);
    }

    public static ExecutableElement getTargetConstructor(List<ExecutableElement> constructors) {
        int minNumberOfParameters = Integer.MAX_VALUE;
        ExecutableElement targetConstructor = null;
        for (ExecutableElement constructor : constructors) {
            int currentNumberOfParameters = constructor.getParameters().size();
            if (currentNumberOfParameters < minNumberOfParameters) {
                minNumberOfParameters = currentNumberOfParameters;
                targetConstructor = constructor;
            }
        }
        return targetConstructor;
    }

    public static Tree.Kind getCurrentTreeKind(CodeCompletionRequest request) {
        WorkingCopy copy = request.getWorkingCopy();
        TreeUtilities treeUtilities = copy.getTreeUtilities();
        Abbreviation abbreviation = request.getAbbreviation();
        TreePath currentPath = treeUtilities.pathFor(abbreviation.getStartOffset());
        if (currentPath == null) {
            return Tree.Kind.OTHER;
        }
        return currentPath.getLeaf().getKind();
    }

    private static TreePath getCurrentPath(CodeCompletionRequest request) {
        WorkingCopy copy = request.getWorkingCopy();
        TreeUtilities treeUtilities = copy.getTreeUtilities();
        Abbreviation abbreviation = request.getAbbreviation();
        return treeUtilities.pathFor(abbreviation.getStartOffset());
    }

    public static Tree getCurrentTree(CodeCompletionRequest request) {
        TreePath currentPath = getCurrentPath(request);
        if (currentPath == null) {
            return null;
        }
        return currentPath.getLeaf();
    }

    public static boolean getCurrentTreeOfKind(Set<Tree.Kind> kinds, CodeCompletionRequest request) {
        Tree.Kind currentContext = getCurrentTreeKind(request);
        if (currentContext == null) {
            return false;
        }
        return kinds.contains(currentContext);
    }

    public static TreePath getParentPathOfKind(Set<Tree.Kind> kinds, CodeCompletionRequest request) {
        TreePath currentPath = getCurrentPath(request);
        if (currentPath == null) {
            return null;
        }
        WorkingCopy copy = request.getWorkingCopy();
        TreeUtilities treeUtilities = copy.getTreeUtilities();
        return treeUtilities.getPathElementOfKind(kinds, currentPath);
    }

    public static boolean getParentTreeOfKind(Set<Tree.Kind> kinds, CodeCompletionRequest request) {
        TreePath currentPath = getCurrentPath(request);
        if (currentPath == null) {
            return false;
        }
        WorkingCopy copy = request.getWorkingCopy();
        TreeUtilities treeUtilities = copy.getTreeUtilities();
        TreePath parentPath = treeUtilities.getPathElementOfKind(kinds, currentPath);
        return parentPath != null;
    }

    public static TypeMirror getTypeInContext(CodeCompletionRequest request) {
        WorkingCopy copy = request.getWorkingCopy();
        TreeUtilities treeUtilities = copy.getTreeUtilities();
        TypeUtilities typeUtilities = copy.getTypeUtilities();
        Trees trees = copy.getTrees();
        Types types = copy.getTypes();
        Abbreviation abbreviation = request.getAbbreviation();
        TreePath currentPath = treeUtilities.pathFor(abbreviation.getStartOffset());
        if (currentPath == null) {
            return null;
        }
        Tree currentTree = currentPath.getLeaf();
        TreePath path;
        ExpressionTree expression;
        Element currentElement;
        int insertIndex;
        switch (currentTree.getKind()) {
            case AND:
            case AND_ASSIGNMENT:
            case ARRAY_ACCESS:
            case BITWISE_COMPLEMENT:
            case LEFT_SHIFT:
            case LEFT_SHIFT_ASSIGNMENT:
            case OR:
            case OR_ASSIGNMENT:
            case POSTFIX_DECREMENT:
            case POSTFIX_INCREMENT:
            case PREFIX_DECREMENT:
            case PREFIX_INCREMENT:
            case RIGHT_SHIFT:
            case RIGHT_SHIFT_ASSIGNMENT:
            case UNSIGNED_RIGHT_SHIFT:
            case UNSIGNED_RIGHT_SHIFT_ASSIGNMENT:
            case XOR:
            case XOR_ASSIGNMENT:
                return types.getPrimitiveType(TypeKind.LONG);
            case ASSIGNMENT:
                AssignmentTree assignmentTree = (AssignmentTree) currentTree;
                ExpressionTree variable = assignmentTree.getVariable();
                path = TreePath.getPath(currentPath, variable);
                return trees.getElement(path).asType();
            case BLOCK:
            case PARENTHESIZED:
                return null;
            case CASE:
                TreePath switchPath = treeUtilities.getPathElementOfKind(Tree.Kind.SWITCH, currentPath);
                if (switchPath == null) {
                    break;
                }
                SwitchTree switchTree = (SwitchTree) switchPath.getLeaf();
                expression = switchTree.getExpression();
                TreePath expressionPath = TreePath.getPath(switchPath, expression);
                return trees.getTypeMirror(expressionPath);
            case CONDITIONAL_AND:
            case CONDITIONAL_OR:
            case LOGICAL_COMPLEMENT:
                return types.getPrimitiveType(TypeKind.BOOLEAN);
            case DIVIDE:
            case EQUAL_TO:
            case GREATER_THAN:
            case GREATER_THAN_EQUAL:
            case LESS_THAN:
            case LESS_THAN_EQUAL:
            case MINUS:
            case MULTIPLY:
            case NOT_EQUAL_TO:
            case PLUS:
            case REMAINDER:
                return types.getPrimitiveType(TypeKind.DOUBLE);
            case EXPRESSION_STATEMENT:
                TokenSequence<JavaTokenId> tokenSequence = treeUtilities.tokensFor(currentTree);
                tokenSequence.moveStart();
                if (tokenSequence.moveNext()) {
                    if (tokenSequence.token().id() == JavaTokenId.IDENTIFIER) {
                        TreePath typeTreePath = treeUtilities.pathFor(tokenSequence.offset() + 1);
                        if (typeTreePath == null) {
                            return null;
                        }
                        return trees.getTypeMirror(typeTreePath);
                    }
                }
                return null;
            case MEMBER_SELECT:
                expression = ((MemberSelectTree) currentTree).getExpression();
                path = TreePath.getPath(currentPath, expression);
                return trees.getTypeMirror(path);
            case METHOD_INVOCATION:
                insertIndex = findInsertIndexForInvocationArgument((MethodInvocationTree) currentTree);
                if (insertIndex == -1) {
                    return null;
                }
                currentElement = trees.getElement(currentPath);
                if (currentElement.getKind() == ElementKind.METHOD) {
                    List<? extends VariableElement> parameters = ((ExecutableElement) currentElement).getParameters();
                    VariableElement parameter = parameters.get(insertIndex);
                    return typeUtilities.getDenotableType(parameter.asType());
                }
                return null;
            case NEW_CLASS:
                insertIndex = findInsertIndexForInvocationArgument((NewClassTree) currentTree);
                if (insertIndex == -1) {
                    return null;
                }
                currentElement = trees.getElement(currentPath);
                if (currentElement.getKind() == ElementKind.CONSTRUCTOR) {
                    List<? extends VariableElement> parameters = ((ExecutableElement) currentElement).getParameters();
                    VariableElement parameter = parameters.get(insertIndex);
                    return typeUtilities.getDenotableType(parameter.asType());
                }
                return null;
            case RETURN:
                return type(owningMethodType(request), request);
            case VARIABLE:
                VariableTree variableTree = (VariableTree) currentTree;
                Tree type = variableTree.getType();
                path = TreePath.getPath(currentPath, type);
                return trees.getElement(path).asType();
        }
        return null;
    }

    public static String owningMethodType(CodeCompletionRequest request) {
        WorkingCopy copy = request.getWorkingCopy();
        TreeUtilities treeUtilities = copy.getTreeUtilities();
        Trees trees = copy.getTrees();
        TreePath currentPath = request.getCurrentPath();
        TreePath path = treeUtilities.getPathElementOfKind(
                EnumSet.of(Tree.Kind.LAMBDA_EXPRESSION, Tree.Kind.METHOD), currentPath);
        if (path == null) {
            return null;
        }
        Tree tree = path.getLeaf();
        switch (tree.getKind()) {
            case METHOD:
                ExecutableElement method = (ExecutableElement) trees.getElement(path);
                TypeMirror returnType = method.getReturnType();
                if (returnType != null) {
                    return returnType.toString();
                }
                break;
        }
        return null;
    }

    public static TypeMirror type(String typeName, CodeCompletionRequest request) {
        WorkingCopy copy = request.getWorkingCopy();
        TreeUtilities treeUtilities = copy.getTreeUtilities();
        Trees trees = copy.getTrees();
        Abbreviation abbreviation = request.getAbbreviation();
        Scope scope = treeUtilities.scopeFor(abbreviation.getStartOffset());
        String type = typeName.trim();
        if (type.isEmpty()) {
            return null;
        }
        TreePath currentPath = treeUtilities.pathFor(abbreviation.getStartOffset());
        if (currentPath == null) {
            return null;
        }
        TypeElement enclosingClass = scope.getEnclosingClass();
        SourcePositions[] sourcePositions = new SourcePositions[1];
        StatementTree statement = treeUtilities.parseStatement("{" + type + " a;}", sourcePositions); //NOI18N
        if (statement.getKind() == Tree.Kind.BLOCK) {
            List<? extends StatementTree> statements = ((BlockTree) statement).getStatements();
            if (!statements.isEmpty()) {
                StatementTree variable = statements.get(0);
                if (variable.getKind() == Tree.Kind.VARIABLE) {
                    treeUtilities.attributeTree(statement, scope);
                    return trees.getTypeMirror(new TreePath(currentPath, ((VariableTree) variable).getType()));
                }
            }
        }
        return treeUtilities.parseType(type, enclosingClass);
    }

    public static int findInsertIndexForTree(int offset, List<? extends Tree> trees, WorkingCopy copy) {
        SourcePositions sourcePositions = copy.getTrees().getSourcePositions();
        CompilationUnitTree compilationUnit = copy.getCompilationUnit();
        long previousStartPosition;
        long currentStartPosition;
        int size = trees.size();
        switch (size) {
            case 0:
                return 0;
            case 1:
                currentStartPosition = sourcePositions.getStartPosition(compilationUnit, trees.get(0));
                return offset < currentStartPosition ? 0 : 1;
            case 2:
                previousStartPosition = sourcePositions.getStartPosition(compilationUnit, trees.get(0));
                currentStartPosition = sourcePositions.getStartPosition(compilationUnit, trees.get(1));
                if (offset < previousStartPosition) {
                    return 0;
                } else if (currentStartPosition < offset) {
                    return size;
                } else {
                    return 1;
                }
            default:
                for (int i = 1; i < size; i++) {
                    previousStartPosition = sourcePositions.getStartPosition(compilationUnit, trees.get(i - 1));
                    currentStartPosition = sourcePositions.getStartPosition(compilationUnit, trees.get(i));
                    if (i < size - 1) {
                        if (offset < previousStartPosition) {
                            return i - 1;
                        } else if (previousStartPosition < offset && offset < currentStartPosition) {
                            return i;
                        }
                    } else {
                        return offset < currentStartPosition ? size - 1 : size;
                    }
                }
        }
        return -1;
    }

    public static String returnVar(String methodType) {
        if (methodType == null || methodType.equals(ConstantDataManager.VOID)) {
            return null;
        }
        switch (methodType) {
            case ConstantDataManager.BYTE:
            case ConstantDataManager.SHORT:
            case ConstantDataManager.INT:
                return ConstantDataManager.ZERO;
            case ConstantDataManager.LONG:
                return ConstantDataManager.ZERO_L;
            case ConstantDataManager.FLOAT:
                return ConstantDataManager.ZERO_DOT_ZERO_F;
            case ConstantDataManager.DOUBLE:
                return ConstantDataManager.ZERO_DOT_ZERO;
            case ConstantDataManager.CHAR:
                return ConstantDataManager.EMPTY_CHAR;
            case ConstantDataManager.BOOLEAN:
                return ConstantDataManager.TRUE;
            case ConstantDataManager.STRING:
                return ConstantDataManager.EMPTY_STRING;
            default:
                return ConstantDataManager.NULL;
        }
    }

    public static String returnVar(CodeCompletionRequest request) {
        String methodType = owningMethodType(request);
        if (methodType == null || methodType.equals(ConstantDataManager.VOID)) {
            return null;
        }
        VariableElement variable = instanceOf(methodType, "", request); //NOI18N
        if (variable != null) {
            return variable.getSimpleName().toString();
        } else {
            switch (methodType) {
                case ConstantDataManager.BYTE:
                case ConstantDataManager.SHORT:
                case ConstantDataManager.INT:
                    return ConstantDataManager.ZERO;
                case ConstantDataManager.LONG:
                    return ConstantDataManager.ZERO_L;
                case ConstantDataManager.FLOAT:
                    return ConstantDataManager.ZERO_DOT_ZERO_F;
                case ConstantDataManager.DOUBLE:
                    return ConstantDataManager.ZERO_DOT_ZERO;
                case ConstantDataManager.CHAR:
                    return ConstantDataManager.EMPTY_CHAR;
                case ConstantDataManager.BOOLEAN:
                    return ConstantDataManager.FALSE;
                case ConstantDataManager.STRING:
                    return ConstantDataManager.EMPTY_STRING;
                default:
                    return ConstantDataManager.NULL;
            }
        }
    }

    private static VariableElement instanceOf(String typeName, String name, CodeCompletionRequest request) {
        VariableElement closest = null;
        List<Element> localElements = new ArrayList<>();
        WorkingCopy copy = request.getWorkingCopy();
        TreeUtilities treeUtilities = copy.getTreeUtilities();
        Types types = copy.getTypes();
        ElementUtilities elementUtilities = copy.getElementUtilities();
        Elements elements = copy.getElements();
        Abbreviation abbreviation = request.getAbbreviation();
        Scope scope = treeUtilities.scopeFor(abbreviation.getStartOffset());
        Iterable<? extends Element> localMembersAndVars =
                elementUtilities.getLocalMembersAndVars(scope, (e, type) -> {
                    return (!elements.isDeprecated(e))
                            && !e.getSimpleName().toString().equals(ConstantDataManager.THIS)
                            && !e.getSimpleName().toString().equals(ConstantDataManager.SUPER)
                            && JavaSourceUtilities.getAllLocalElementKinds().contains(e.getKind());
                });
        localMembersAndVars.forEach(localElements::add);
        TypeMirror type = type(typeName, request);
        if (type == null) {
            return null;
        }
        int distance = Integer.MAX_VALUE;
        for (Element element : localElements) {
            if (VariableElement.class.isInstance(element)
                    && !ConstantDataManager.ANGLED_ERROR.contentEquals(element.getSimpleName())
                    && element.asType().getKind() != TypeKind.ERROR
                    && types.isAssignable(element.asType(), type)) {
                if (name.isEmpty()) {
                    return (VariableElement) element;
                }
                int d = ElementHeaders.getDistance(element.getSimpleName().toString().toLowerCase(), name.toLowerCase());
                if (isSameType(element.asType(), type, types)) {
                    d -= 1000;
                }
                if (d < distance) {
                    distance = d;
                    closest = (VariableElement) element;
                }
            }
        }
        return closest;
    }

    private static boolean isSameType(TypeMirror t1, TypeMirror t2, Types types) {
        if (types.isSameType(t1, t2)) {
            return true;
        }
        if (t1.getKind().isPrimitive() && types.isSameType(types.boxedClass((PrimitiveType) t1).asType(), t2)) {
            return true;
        }
        return t2.getKind().isPrimitive() && types.isSameType(t1, types.boxedClass((PrimitiveType) t1).asType());
    }

    public static List<ExpressionTree> evaluateMethodArguments(ExecutableElement method, CodeCompletionRequest request) {
        List<ExpressionTree> arguments = new ArrayList<>();
        List<? extends VariableElement> parameters = method.getParameters();
        parameters.stream()
                .map(parameter -> parameter.asType())
                .forEachOrdered(elementType -> {
                    AtomicReference<IdentifierTree> identifierTree = new AtomicReference<>();
                    VariableElement variableElement =
                            instanceOf(elementType.toString(), "", request); //NOI18N
                    if (variableElement != null) {
                        identifierTree.set(JavaSourceMaker.makeIdentifierTree(variableElement, request));
                        arguments.add(identifierTree.get());
                    } else {
                        switch (elementType.getKind()) {
                            case BOOLEAN:
                                identifierTree.set(JavaSourceMaker.makeIdentifierTree(ConstantDataManager.FALSE, request));
                                break;
                            case BYTE:
                            case SHORT:
                            case INT:
                                identifierTree.set(JavaSourceMaker.makeIdentifierTree(ConstantDataManager.ZERO, request));
                                break;
                            case LONG:
                                identifierTree.set(JavaSourceMaker.makeIdentifierTree(ConstantDataManager.ZERO_L, request));
                                break;
                            case FLOAT:
                                identifierTree.set(JavaSourceMaker.makeIdentifierTree(
                                        ConstantDataManager.ZERO_DOT_ZERO_F, request));
                                break;
                            case DOUBLE:
                                identifierTree.set(JavaSourceMaker.makeIdentifierTree(
                                        ConstantDataManager.ZERO_DOT_ZERO, request));
                                break;
                            default:
                                identifierTree.set(JavaSourceMaker.makeIdentifierTree(ConstantDataManager.NULL, request));
                        }
                        arguments.add(identifierTree.get());
                    }
                });
        return Collections.unmodifiableList(arguments);
    }

    public static boolean isCaseStatement(int offset, WorkingCopy copy) {
        TreeUtilities treeUtilities = copy.getTreeUtilities();
        TreePath currentPath = treeUtilities.pathFor(offset);
        if (currentPath == null) {
            return false;
        } else {
            TokenSequence<?> sequence = copy.getTokenHierarchy().tokenSequence();
            sequence.move(offset);
            while (sequence.movePrevious() && sequence.token().id() == JavaTokenId.WHITESPACE) {
            }
            return currentPath.getLeaf().getKind() == Tree.Kind.CASE
                    && sequence.token().id() != JavaTokenId.CASE;
        }
    }

    public static String getVariableName(TypeMirror typeMirror, CodeCompletionRequest request) {
        List<Element> localElements = new ArrayList<>();
        WorkingCopy copy = request.getWorkingCopy();
        TreeUtilities treeUtilities = copy.getTreeUtilities();
        ElementUtilities elementUtilities = copy.getElementUtilities();
        Elements elements = copy.getElements();
        Abbreviation abbreviation = request.getAbbreviation();
        Scope scope = treeUtilities.scopeFor(abbreviation.getStartOffset());
        Iterable<? extends Element> localMembersAndVars =
                elementUtilities.getLocalMembersAndVars(scope, (e, type) -> {
                    return (!elements.isDeprecated(e))
                            && !e.getSimpleName().toString().equals(ConstantDataManager.THIS)
                            && !e.getSimpleName().toString().equals(ConstantDataManager.SUPER)
                            && getAllLocalElementKinds().contains(e.getKind());
                });
        localMembersAndVars.forEach(localElements::add);
        Document document;
        try {
            document = copy.getDocument();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return ""; //NOI18N
        }
        List<String> nameSuggestions = Utilities.varNamesSuggestions(typeMirror, ElementKind.FIELD,
                Collections.emptySet(), null, null, copy.getTypes(), copy.getElements(), localElements,
                CodeStyle.getDefault(document));
        return nameSuggestions.isEmpty() ? "" : nameSuggestions.get(0); //NOI18N
    }

    public static boolean isMethodSection(ClassTree classInterfaceOrEnumTree, CodeCompletionRequest request) {
        WorkingCopy copy = request.getWorkingCopy();
        Trees trees = copy.getTrees();
        CompilationUnitTree compilationUnit = copy.getCompilationUnit();
        List<? extends Tree> members = classInterfaceOrEnumTree.getMembers();
        SourcePositions sourcePositions = trees.getSourcePositions();
        int size = members.size();
        Tree currentMember;
        Tree previousMember;
        long previousStartOffset;
        long currentStartOffset;
        Abbreviation abbreviation = request.getAbbreviation();
        switch (size) {
            case 0:
            case 1:
                return false;
            case 2:
                currentMember = members.get(1);
                currentStartOffset = sourcePositions.getStartPosition(compilationUnit, currentMember);
                if (abbreviation.getStartOffset() < currentStartOffset) {
                    return false;
                } else {
                    return currentStartOffset < abbreviation.getStartOffset() && currentMember.getKind() == METHOD;
                }
            default:
                for (int i = 1; i < size; i++) {
                    previousMember = members.get(i - 1);
                    previousStartOffset = sourcePositions.getStartPosition(compilationUnit, previousMember);
                    currentMember = members.get(i);
                    currentStartOffset = sourcePositions.getStartPosition(compilationUnit, currentMember);
                    if (i == 1 && abbreviation.getStartOffset() < currentStartOffset) {
                        return false;
                    }
                    if (previousStartOffset < abbreviation.getStartOffset()
                            && abbreviation.getStartOffset() < currentStartOffset
                            && previousMember.getKind() == METHOD
                            && currentMember.getKind() == METHOD) {
                        return true;
                    } else if (currentStartOffset < abbreviation.getStartOffset() && currentMember.getKind() == METHOD) {
                        return true;
                    }
                }
        }
        return false;
    }

    public static boolean isMethodReturnVoid(ExecutableElement method) {
        return method.getReturnType().getKind() == TypeKind.VOID;
    }

    public static boolean isMemberSelection(CodeCompletionRequest request) {
        return request.getCurrentKind() == Tree.Kind.MEMBER_SELECT;
    }

    public static List<ExecutableElement> getMethodsByAbbreviation(List<ExecutableElement> methods,
            Abbreviation abbreviation) {
        List<ExecutableElement> result = new ArrayList<>();
        methods.forEach(method -> {
            String methodAbbreviation = StringUtilities.getMethodAbbreviation(method.getSimpleName().toString());
            if (methodAbbreviation.equals(abbreviation.getIdentifier())) {
                result.add(method);
            }
        });
        return Collections.unmodifiableList(result);
    }

    public static List<ExecutableElement> getMethodsInClassHierarchy(Element element, WorkingCopy copy) {
        List<ExecutableElement> methods = new ArrayList<>();
        ElementUtilities elementUtilities = copy.getElementUtilities();
        Elements elements = copy.getElements();
        TypeMirror typeMirror = element.asType();
        Iterable<? extends Element> members;
        try {
            members = elementUtilities.getMembers(typeMirror, (e, t) -> {
                return e.getKind() == ElementKind.METHOD && !elements.isDeprecated(e);
            });
        } catch (AssertionError error) {
            return Collections.emptyList();
        }
        members.forEach(member -> methods.add((ExecutableElement) member));
        return Collections.unmodifiableList(methods);
    }

    public static List<ExecutableElement> getMethodsInCurrentClassHierarchy(WorkingCopy copy) {
        List<ExecutableElement> methods = new ArrayList<>();
        Elements elements = copy.getElements();
        ElementUtilities elementUtilities = copy.getElementUtilities();
        TypeMirror typeMirror = getTypeMirrorOfCurrentClass(copy);
        if (typeMirror == null) {
            return Collections.emptyList();
        }
        Iterable<? extends Element> members = elementUtilities.getMembers(typeMirror, (e, t) -> {
            return e.getKind() == ElementKind.METHOD && !elements.isDeprecated(e);
        });
        methods.addAll(ElementFilter.methodsIn(members));
        return Collections.unmodifiableList(methods);
    }

    private static TypeMirror getTypeMirrorOfCurrentClass(WorkingCopy copy) {
        Trees trees = copy.getTrees();
        CompilationUnitTree compilationUnit = copy.getCompilationUnit();
        Tree tree = compilationUnit.getTypeDecls().get(0);
        if (tree.getKind() == Tree.Kind.CLASS) {
            return trees.getTypeMirror(TreePath.getPath(compilationUnit, tree));
        }
        return null;
    }

    public static List<Element> getElementsByAbbreviation(WorkingCopy copy, Abbreviation abbreviation) {
        List<Element> localElements = new ArrayList<>();
        TreeUtilities treeUtilities = copy.getTreeUtilities();
        ElementUtilities elementUtilities = copy.getElementUtilities();
        Elements elements = copy.getElements();
        Scope scope = treeUtilities.scopeFor(abbreviation.getStartOffset());
        TreePath path = treeUtilities.pathFor(abbreviation.getStartOffset());
        if (path.getLeaf().getKind() == CASE) {
            path = treeUtilities.getPathElementOfKind(SWITCH, path);
            Trees trees = copy.getTrees();
            SourcePositions sourcePositions = trees.getSourcePositions();
            long startPosition = sourcePositions.getStartPosition(copy.getCompilationUnit(), path.getLeaf());
            scope = treeUtilities.scopeFor((int) startPosition);
        }
        Iterable<? extends Element> localMembersAndVars =
                elementUtilities.getLocalMembersAndVars(scope, (element, type) -> {
                    return (!elements.isDeprecated(element))
                            && !element.getSimpleName().toString().equals(ConstantDataManager.THIS)
                            && !element.getSimpleName().toString().equals(ConstantDataManager.SUPER)
                            && getAllLocalElementKinds().contains(element.getKind());
                });
        localMembersAndVars.forEach(localElements::add);
        localElements.removeIf(element -> {
            String elementName = element.getSimpleName().toString();
            String elementAbbreviation = StringUtilities.getElementAbbreviation(elementName);
            return !elementAbbreviation.equals(abbreviation.getScope());
        });
        return Collections.unmodifiableList(localElements);
    }

    public static List<ExecutableElement> getNonStaticMethodsInClassHierarchy(
            Element element, WorkingCopy copy) {
        List<ExecutableElement> methods = JavaSourceUtilities.getMethodsInClassHierarchy(element, copy);
        Function<List<ExecutableElement>, List<ExecutableElement>> filterNonStaticMethods = allMethods -> {
            return allMethods.stream()
                    .filter(method -> (!method.getModifiers().contains(Modifier.STATIC)))
                    .collect(Collectors.toList());
        };
        methods = filterNonStaticMethods.apply(methods);
        return Collections.unmodifiableList(methods);
    }

    public static List<ExecutableElement> getStaticMethodsInClass(TypeElement element) {
        Iterable<? extends Element> members;
        try {
            members = element.getEnclosedElements();
        } catch (AssertionError error) {
            return Collections.emptyList();
        }
        List<ExecutableElement> methods = ElementFilter.methodsIn(members);
        methods = filterStaticMethods(methods);
        return Collections.unmodifiableList(methods);
    }

    private static List<ExecutableElement> filterStaticMethods(List<ExecutableElement> methods) {
        List<ExecutableElement> staticMethods = new ArrayList<>();
        methods.stream().filter(method -> (method.getModifiers().contains(Modifier.STATIC)))
                .forEachOrdered(method -> {
                    staticMethods.add(method);
                });
        return Collections.unmodifiableList(staticMethods);
    }

    public static List<TypeElement> collectExternalTypes(WorkingCopy copy, Abbreviation abbreviation) {
        ClasspathInfo classpathInfo = copy.getClasspathInfo();
        ClassIndex classIndex = classpathInfo.getClassIndex();
        Set<ElementHandle<TypeElement>> declaredTypes = classIndex.getDeclaredTypes(
                abbreviation.getScope().toUpperCase(),
                ClassIndex.NameKind.CAMEL_CASE,
                EnumSet.of(ClassIndex.SearchScope.SOURCE, ClassIndex.SearchScope.DEPENDENCIES));
        List<TypeElement> typeElements = new ArrayList<>();
        Elements elements = copy.getElements();
        declaredTypes.forEach(type -> {
            TypeElement typeElement = type.resolve(copy);
            if (typeElement != null) {
                String typeName = typeElement.getSimpleName().toString();
                String typeAbbreviation = StringUtilities.getElementAbbreviation(typeName);
                if (typeAbbreviation.equals(abbreviation.getScope())) {
                    if (!elements.isDeprecated(typeElement)) {
                        typeElements.add(typeElement);
                    }
                }
            }
        });
        return Collections.unmodifiableList(typeElements);
    }

    public static Iterable<? extends TypeElement> collectGlobalTypeElements(WorkingCopy copy, Abbreviation abbreviation) {
        ElementUtilities elementUtilities = copy.getElementUtilities();
        return elementUtilities.getGlobalTypes((element, type) -> {
            String typeAbbreviation = StringUtilities.getElementAbbreviation(element.getSimpleName().toString());
            return typeAbbreviation.equals(abbreviation.getScope());
        });
    }

    public static boolean isModifier(TokenId tokenId) {
        return tokenId == JavaTokenId.ABSTRACT
                || tokenId == JavaTokenId.FINAL
                || tokenId == JavaTokenId.NATIVE
                || tokenId == JavaTokenId.PRIVATE
                || tokenId == JavaTokenId.PROTECTED
                || tokenId == JavaTokenId.PUBLIC
                || tokenId == JavaTokenId.STATIC
                || tokenId == JavaTokenId.STRICTFP
                || tokenId == JavaTokenId.SYNCHRONIZED
                || tokenId == JavaTokenId.TRANSIENT
                || tokenId == JavaTokenId.VOLATILE;
    }

    public static boolean isInSamePackageAsCurrentFile(Element element, CodeCompletionRequest request) {
        WorkingCopy copy = request.getWorkingCopy();
        ExpressionTree expressionTree = copy.getCompilationUnit().getPackageName();
        if (expressionTree == null) {
            return false;
        }
        String packageName = expressionTree.toString();
        Elements elements = copy.getElements();
        PackageElement currentPackageElement = elements.getPackageElement(packageName);
        PackageElement packageElement = elements.getPackageOf(element);
        if (currentPackageElement == null || packageElement == null) {
            return false;
        }
        return packageElement.equals(currentPackageElement);
    }

    public static List<TypeElement> collectExternalTypeElements(CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        Abbreviation abbreviation = request.getAbbreviation();
        ClasspathInfo classpathInfo = workingCopy.getClasspathInfo();
        ClassIndex classIndex = classpathInfo.getClassIndex();
        Set<ElementHandle<TypeElement>> declaredTypes = classIndex.getDeclaredTypes(
                abbreviation.getScope().toUpperCase(),
                ClassIndex.NameKind.CAMEL_CASE,
                EnumSet.of(ClassIndex.SearchScope.SOURCE, ClassIndex.SearchScope.DEPENDENCIES));
        List<TypeElement> types = new ArrayList<>();
        Elements elements = workingCopy.getElements();
        declaredTypes.forEach(externalType -> {
            TypeElement typeElement = externalType.resolve(workingCopy);
            if (typeElement == null) {
                return;
            }
            if (elements.isDeprecated(typeElement)) {
                return;
            }
            String typeName = typeElement.getSimpleName().toString();
            String typeAbbreviation = StringUtilities.getElementAbbreviation(typeName);
            if (!typeAbbreviation.equals(abbreviation.getScope())) {
                return;
            }
            if (typeElement.getModifiers().contains(Modifier.PRIVATE)) {
                return;
            } else {
                if (!typeElement.getModifiers().contains(Modifier.PUBLIC)) {
                    if (!JavaSourceUtilities.isInSamePackageAsCurrentFile(typeElement, request)) {
                        return;
                    }
                }
            }
            types.add(typeElement);
        });
        return Collections.unmodifiableList(types);
    }

    public static List<TypeElement> collectGlobalTypeElements(CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        Elements elements = workingCopy.getElements();
        Abbreviation abbreviation = request.getAbbreviation();
        ElementUtilities elementUtilities = workingCopy.getElementUtilities();
        Iterable<? extends TypeElement> globalTypes =
                elementUtilities.getGlobalTypes((element, type) -> {
                    if (elements.isDeprecated(element)) {
                        return false;
                    }
                    String typeAbbreviation = StringUtilities.getElementAbbreviation(element.getSimpleName().toString());
                    if (!typeAbbreviation.equals(abbreviation.getScope())) {
                        return false;
                    }
                    if (element.getModifiers().contains(Modifier.PUBLIC)) {
                        return true;
                    } else if (element.getModifiers().contains(Modifier.PRIVATE)) {
                        return false;
                    } else {
                        if (JavaSourceUtilities.isInSamePackageAsCurrentFile(element, request)) {
                            return true;
                        }
                    }
                    return false;
                });
        List<TypeElement> types = new ArrayList<>();
        globalTypes.forEach(types::add);
        return Collections.unmodifiableList(types);
    }

    public static List<TypeElement> collectInternalTypeElements(CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        ElementUtilities elementUtilities = workingCopy.getElementUtilities();
        Elements elements = workingCopy.getElements();
        CompilationUnitTree compilationUnit = workingCopy.getCompilationUnit();
        List<? extends Tree> typeDecls = compilationUnit.getTypeDecls();
        Tree topLevelClassInterfaceOrEnumTree = typeDecls.get(0);
        Element topLevelElement = workingCopy.getTrees().getElement(
                TreePath.getPath(compilationUnit, topLevelClassInterfaceOrEnumTree));
        List<TypeElement> types = new ArrayList<>();
        Abbreviation abbreviation = request.getAbbreviation();
        String topLevelElementAbbreviation =
                StringUtilities.getElementAbbreviation(topLevelElement.getSimpleName().toString());
        if (topLevelElementAbbreviation.equals(abbreviation.getScope())) {
            types.add((TypeElement) topLevelElement);
        }
        Iterable<? extends Element> internalTypes =
                elementUtilities.getMembers(topLevelElement.asType(), (element, type) -> {
                    if (elements.isDeprecated(element)) {
                        return false;
                    }
                    String typeAbbreviation = StringUtilities.getElementAbbreviation(element.getSimpleName().toString());
                    if (!typeAbbreviation.equals(abbreviation.getScope())) {
                        return false;
                    }
                    return element.getKind() == ElementKind.CLASS
                            || element.getKind() == ElementKind.ENUM
                            || element.getKind() == ElementKind.INTERFACE;
                });
        internalTypes.forEach(internalType -> types.add((TypeElement) internalType));
        return Collections.unmodifiableList(types);
    }

    public static Map<TypeElement, List<TypeElement>> collectExternalInnerTypeElements(CodeCompletionRequest request) {
        return collectInnerTypeElements(true, request);
    }

    public static Map<TypeElement, List<TypeElement>> collectGlobalInnerTypeElements(CodeCompletionRequest request) {
        return collectInnerTypeElements(false, request);
    }

    public static Map<TypeElement, List<TypeElement>> collectInnerTypeElements(
            boolean external, CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        Elements elements = workingCopy.getElements();
        ElementUtilities elementUtilities = workingCopy.getElementUtilities();
        Abbreviation abbreviation = request.getAbbreviation();
        List<TypeElement> types;
        if (external) {
            types = collectExternalTypeElements(request);
        } else {
            types = collectGlobalTypeElements(request);
        }
        Map<TypeElement, List<TypeElement>> innerTypeElementsByTopLevelTypeElements = new HashMap<>();
        for (TypeElement type : types) {
            Iterable<? extends Element> innerTypeElements =
                    elementUtilities.getMembers(type.asType(), (element, typeMirror) -> {
                        if (elements.isDeprecated(element)) {
                            return false;
                        }
                        if (element.getKind() != ElementKind.ENUM && element.getKind() != ElementKind.CLASS) {
                            return false;
                        }
                        String innerTypeAbbreviation =
                                StringUtilities.getElementAbbreviation(element.getSimpleName().toString());
                        if (!innerTypeAbbreviation.equals(abbreviation.getIdentifier())) {
                            return false;
                        }
                        if (element.getModifiers().contains(Modifier.PUBLIC)) {
                            return true;
                        } else if (element.getModifiers().contains(Modifier.PRIVATE)) {
                            return false;
                        } else {
                            if (JavaSourceUtilities.isInSamePackageAsCurrentFile(type, request)) {
                                return true;
                            }
                        }
                        return false;
                    });
            List<TypeElement> innerTypes = new ArrayList<>();
            innerTypeElements.forEach(innerTypeElement -> innerTypes.add((TypeElement) innerTypeElement));
            innerTypeElementsByTopLevelTypeElements.put(type, innerTypes);
        }
        return Collections.unmodifiableMap(innerTypeElementsByTopLevelTypeElements);
    }

    public static boolean isAdjacentToModifiersTreeSpan(CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        Abbreviation abbreviation = request.getAbbreviation();
        TokenSequence<?> tokenSequence = workingCopy.getTokenHierarchy().tokenSequence();
        tokenSequence.move(abbreviation.getStartOffset());
        while (tokenSequence.movePrevious() && tokenSequence.token().id() == JavaTokenId.WHITESPACE) {
        }
        Token<?> token = tokenSequence.token();
        return token != null && isModifier(token.id());
    }

    public static boolean isPositionOfExtendsKeywordInClassOrInterfaceDeclaration(CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        TreeUtilities treeUtilities = workingCopy.getTreeUtilities();
        Tree currentTree = request.getCurrentTree();
        TokenSequence<JavaTokenId> tokenSequence = treeUtilities.tokensFor(currentTree);
        tokenSequence.moveStart();
        Map<JavaTokenId, Integer> offsetsByTokenIds = new HashMap<>();
        OUTER:
        while (tokenSequence.moveNext()) {
            switch (tokenSequence.token().id()) {
                case EXTENDS:
                    return false;
                case IDENTIFIER:
                    if (offsetsByTokenIds.get(IDENTIFIER) == null) {
                        offsetsByTokenIds.put(IDENTIFIER, tokenSequence.offset());
                    }
                    break;
                case IMPLEMENTS:
                    offsetsByTokenIds.put(IMPLEMENTS, tokenSequence.offset());
                    break;
                case LBRACE:
                    offsetsByTokenIds.put(LBRACE, tokenSequence.offset());
                    break OUTER;
            }
        }
        Abbreviation abbreviation = request.getAbbreviation();
        int abbreviationStartOffset = abbreviation.getStartOffset();
        if (abbreviationStartOffset > offsetsByTokenIds.getOrDefault(LBRACE, Integer.MAX_VALUE)) {
            return false;
        }
        if (abbreviationStartOffset < offsetsByTokenIds.getOrDefault(IDENTIFIER, Integer.MIN_VALUE)) {
            return false;
        }
        Integer implementsTokenOffset = offsetsByTokenIds.get(IMPLEMENTS);
        if (implementsTokenOffset != null) {
            if (abbreviationStartOffset > implementsTokenOffset) {
                return false;
            }
        }
        return true;
    }

    public static boolean isInsideExtendsTreeSpan(CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        TreeUtilities treeUtilities = workingCopy.getTreeUtilities();
        Tree currentTree = request.getCurrentTree();
        TokenSequence<JavaTokenId> tokenSequence = treeUtilities.tokensFor(currentTree);
        tokenSequence.moveStart();
        Map<JavaTokenId, Integer> offsetsByTokenIds = new HashMap<>();
        OUTER:
        while (tokenSequence.moveNext()) {
            switch (tokenSequence.token().id()) {
                case EXTENDS:
                    offsetsByTokenIds.put(EXTENDS, tokenSequence.offset());
                    break;
                case IDENTIFIER:
                    if (offsetsByTokenIds.get(IDENTIFIER) == null) {
                        offsetsByTokenIds.put(IDENTIFIER, tokenSequence.offset());
                    }
                    break;
                case IMPLEMENTS:
                    offsetsByTokenIds.put(IMPLEMENTS, tokenSequence.offset());
                    break;
                case LBRACE:
                    offsetsByTokenIds.put(LBRACE, tokenSequence.offset());
                    break OUTER;
            }
        }
        Abbreviation abbreviation = request.getAbbreviation();
        int abbreviationStartOffset = abbreviation.getStartOffset();
        if (abbreviationStartOffset > offsetsByTokenIds.getOrDefault(LBRACE, Integer.MAX_VALUE)) {
            return false;
        }
        if (abbreviationStartOffset < offsetsByTokenIds.getOrDefault(IDENTIFIER, Integer.MIN_VALUE)) {
            return false;
        }
        Integer extendsTokenOffset = offsetsByTokenIds.get(EXTENDS);
        if (extendsTokenOffset != null) {
            if (abbreviationStartOffset < extendsTokenOffset) {
                return false;
            }
        }
        Integer implementsTokenOffset = offsetsByTokenIds.get(IMPLEMENTS);
        if (implementsTokenOffset != null) {
            if (abbreviationStartOffset > implementsTokenOffset) {
                return false;
            }
        }
        if (extendsTokenOffset != null) {
            if (abbreviationStartOffset > extendsTokenOffset) {
                tokenSequence.move(extendsTokenOffset);
                tokenSequence.moveNext();
                while (tokenSequence.moveNext() && tokenSequence.token().id() == JavaTokenId.WHITESPACE) {
                }
                Token<JavaTokenId> token = tokenSequence.token();
                return token != null && tokenSequence.offset() == abbreviation.getStartOffset();
            }
            return false;
        }
        return true;
    }

    public static boolean isInsideImplementsTreeSpan(CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        TreeUtilities treeUtilities = workingCopy.getTreeUtilities();
        Tree currentTree = request.getCurrentTree();
        TokenSequence<JavaTokenId> tokenSequence = treeUtilities.tokensFor(currentTree);
        tokenSequence.moveStart();
        Map<JavaTokenId, Integer> offsetsByTokenIds = new HashMap<>();
        OUTER:
        while (tokenSequence.moveNext()) {
            switch (tokenSequence.token().id()) {
                case EXTENDS:
                    offsetsByTokenIds.put(EXTENDS, tokenSequence.offset());
                    break;
                case IDENTIFIER:
                    if (offsetsByTokenIds.get(IDENTIFIER) == null) {
                        offsetsByTokenIds.put(IDENTIFIER, tokenSequence.offset());
                    }
                    break;
                case IMPLEMENTS:
                    offsetsByTokenIds.put(IMPLEMENTS, tokenSequence.offset());
                    break;
                case LBRACE:
                    offsetsByTokenIds.put(LBRACE, tokenSequence.offset());
                    break OUTER;
            }
        }
        Abbreviation abbreviation = request.getAbbreviation();
        int abbreviationStartOffset = abbreviation.getStartOffset();
        if (abbreviationStartOffset > offsetsByTokenIds.getOrDefault(LBRACE, Integer.MAX_VALUE)) {
            return false;
        }
        if (abbreviationStartOffset < offsetsByTokenIds.getOrDefault(IDENTIFIER, Integer.MIN_VALUE)) {
            return false;
        }
        Integer extendsTokenOffset = offsetsByTokenIds.get(EXTENDS);
        if (extendsTokenOffset != null) {
            if (abbreviationStartOffset < extendsTokenOffset) {
                return false;
            }
        }
        Integer implementsTokenOffset = offsetsByTokenIds.get(IMPLEMENTS);
        return implementsTokenOffset != null && abbreviation.getStartOffset() > implementsTokenOffset;
    }

    public static boolean isInsideClassEnumOrInterfaceBodySpan(ClassTree classOrInterface, CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        TreeUtilities treeUtilities = workingCopy.getTreeUtilities();
        Abbreviation abbreviation = request.getAbbreviation();
        int[] bodySpan = treeUtilities.findBodySpan(classOrInterface);
        return bodySpan[0] < abbreviation.getStartOffset() && abbreviation.getStartOffset() < bodySpan[1];
    }

    public static boolean isNextToken(TokenId tokenId, CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        TokenSequence<?> tokenSequence = workingCopy.getTokenHierarchy().tokenSequence();
        Abbreviation abbreviation = request.getAbbreviation();
        tokenSequence.move(abbreviation.getStartOffset());
        if (tokenSequence.moveNext()) {
            return tokenSequence.token().id() == tokenId;
        }
        return false;
    }

    public static boolean isPositionOfImplementsKeywordInClassOrEnumDeclaration(CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        TreeUtilities treeUtilities = workingCopy.getTreeUtilities();
        Tree currentTree = request.getCurrentTree();
        TokenSequence<JavaTokenId> tokenSequence = treeUtilities.tokensFor(currentTree);
        tokenSequence.moveStart();
        Map<JavaTokenId, Integer> offsetsByTokenIds = new HashMap<>();
        OUTER:
        while (tokenSequence.moveNext()) {
            switch (tokenSequence.token().id()) {
                case EXTENDS:
                    offsetsByTokenIds.put(EXTENDS, tokenSequence.offset());
                    break;
                case IDENTIFIER:
                    if (offsetsByTokenIds.get(IDENTIFIER) == null) {
                        offsetsByTokenIds.put(IDENTIFIER, tokenSequence.offset());
                    }
                    break;
                case IMPLEMENTS:
                    return false;
                case LBRACE:
                    offsetsByTokenIds.put(LBRACE, tokenSequence.offset());
                    break OUTER;
            }
        }
        Abbreviation abbreviation = request.getAbbreviation();
        int abbreviationStartOffset = abbreviation.getStartOffset();
        if (abbreviationStartOffset > offsetsByTokenIds.getOrDefault(LBRACE, Integer.MAX_VALUE)) {
            return false;
        }
        if (abbreviationStartOffset < offsetsByTokenIds.getOrDefault(IDENTIFIER, Integer.MIN_VALUE)) {
            return false;
        }
        Integer extendsTokenOffset = offsetsByTokenIds.get(EXTENDS);
        if (extendsTokenOffset != null) {
            if (abbreviationStartOffset < extendsTokenOffset) {
                return false;
            }
        }
        return true;
    }

    public static boolean isInsideMethodBodySpan(CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        TreeUtilities treeUtilities = workingCopy.getTreeUtilities();
        Abbreviation abbreviation = request.getAbbreviation();
        TokenSequence<JavaTokenId> tokenSequence = treeUtilities.tokensFor(request.getCurrentTree());
        tokenSequence.moveStart();
        while (tokenSequence.moveNext() && tokenSequence.token().id() != JavaTokenId.LBRACE) {
        }
        Token<JavaTokenId> token = tokenSequence.token();
        if (token != null && token.id() == JavaTokenId.LBRACE) {
            if (tokenSequence.offset() < abbreviation.getStartOffset()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPositionOfThrowsKeyword(CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        TreeUtilities treeUtilities = workingCopy.getTreeUtilities();
        TokenSequence<JavaTokenId> tokenSequence = treeUtilities.tokensFor(request.getCurrentTree());
        tokenSequence.moveStart();
        Map<JavaTokenId, Integer> offsetsByTokenIds = new HashMap<>();
        OUTER:
        while (tokenSequence.moveNext()) {
            switch (tokenSequence.token().id()) {
                case IDENTIFIER:
                    if (offsetsByTokenIds.get(IDENTIFIER) == null) {
                        offsetsByTokenIds.put(IDENTIFIER, tokenSequence.offset());
                    }
                    break;
                case THROWS:
                    return false;
                case LBRACE:
                    offsetsByTokenIds.put(LBRACE, tokenSequence.offset());
                    break OUTER;
            }
        }
        Abbreviation abbreviation = request.getAbbreviation();
        int abbreviationStartOffset = abbreviation.getStartOffset();
        if (abbreviationStartOffset > offsetsByTokenIds.getOrDefault(LBRACE, Integer.MAX_VALUE)) {
            return false;
        }
        return abbreviationStartOffset > offsetsByTokenIds.getOrDefault(IDENTIFIER, Integer.MAX_VALUE);
    }

    public static boolean isInsideThrowsTreeSpan(CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        TreeUtilities treeUtilities = workingCopy.getTreeUtilities();
        Tree currentTree = request.getCurrentTree();
        TokenSequence<JavaTokenId> tokenSequence = treeUtilities.tokensFor(currentTree);
        tokenSequence.moveStart();
        Map<JavaTokenId, Integer> offsetsByTokenIds = new HashMap<>();
        OUTER:
        while (tokenSequence.moveNext()) {
            switch (tokenSequence.token().id()) {
                case IDENTIFIER:
                    if (offsetsByTokenIds.get(IDENTIFIER) == null) {
                        offsetsByTokenIds.put(IDENTIFIER, tokenSequence.offset());
                    }
                    break;
                case THROWS:
                    offsetsByTokenIds.put(THROWS, tokenSequence.offset());
                    break;
                case LBRACE:
                    offsetsByTokenIds.put(LBRACE, tokenSequence.offset());
                    break OUTER;
            }
        }
        Abbreviation abbreviation = request.getAbbreviation();
        int abbreviationStartOffset = abbreviation.getStartOffset();
        if (abbreviationStartOffset > offsetsByTokenIds.getOrDefault(LBRACE, Integer.MAX_VALUE)) {
            return false;
        }
        if (abbreviationStartOffset < offsetsByTokenIds.getOrDefault(IDENTIFIER, Integer.MIN_VALUE)) {
            return false;
        }
        Integer throwsTokenOffset = offsetsByTokenIds.get(THROWS);
        return throwsTokenOffset != null && abbreviation.getStartOffset() > throwsTokenOffset;
    }

    public static boolean isInsideMethodParameterTreeSpan(CodeCompletionRequest request) {
        WorkingCopy workingCopy = request.getWorkingCopy();
        TreeUtilities treeUtilities = workingCopy.getTreeUtilities();
        int[] methodParameterSpan = treeUtilities.findMethodParameterSpan((MethodTree) request.getCurrentTree());
        Abbreviation abbreviation = request.getAbbreviation();
        return methodParameterSpan[0] < abbreviation.getStartOffset()
                && abbreviation.getStartOffset() <= methodParameterSpan[1];
    }
}
