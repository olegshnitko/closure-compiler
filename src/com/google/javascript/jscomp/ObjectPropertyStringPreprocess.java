/*
 * Copyright 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;



/**
 * Rewrites <code>new goog.testing.ObjectPropertyString(foo, 'bar')</code> to
 * <code>new JSCompiler_ObjectPropertyString(window, foo.bar)</code>.
 *
 * These two passes are for use with goog.testing.PropertyReplacer.
 *
 * <code>
 * var ops = new goog.testing.ObjectPropertyString(foo.prototype, 'bar');
 * propertyReplacer.set(ops,object, ops.propertyString, baz);
 * </code>
 *
 * @see ObjectPropertyStringPostprocess
 *
*
 */
public class ObjectPropertyStringPreprocess implements CompilerPass {
  static final String OBJECT_PROPERTY_STRING =
      "goog.testing.ObjectPropertyString";

  public static final String EXTERN_OBJECT_PROPERTY_STRING =
      "JSCompiler_ObjectPropertyString";

  static final DiagnosticType INVALID_NUM_ARGUMENTS_ERROR =
      DiagnosticType.error("JSC_OBJECT_PROPERTY_STRING_NUM_ARGS",
          "goog.testing.ObjectPropertyString instantiated with \"{0}\" " +
          "arguments, expected 2.");

  static final DiagnosticType QUALIFIED_NAME_EXPECTED_ERROR =
      DiagnosticType.error("JSC_OBJECT_PROPERTY_STRING_QUALIFIED_NAME_EXPECTED",
          "goog.testing.ObjectPropertyString instantiated with invalid " +
          "argument, qualified name expected. Was \"{0}\".");

  static final DiagnosticType STRING_LITERAL_EXPECTED_ERROR =
      DiagnosticType.error("JSC_OBJECT_PROPERTY_STRING_STRING_LITERAL_EXPECTED",
          "goog.testing.ObjectPropertyString instantiated with invalid " +
          "argument, string literal expected. Was \"{0}\".");

  private final AbstractCompiler compiler;

  ObjectPropertyStringPreprocess(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  public void process(Node externs, Node root) {
    externs.addChildToBack(
        new Node(Token.VAR,
            Node.newString(Token.NAME, EXTERN_OBJECT_PROPERTY_STRING)));
    NodeTraversal.traverse(compiler, root, new Callback());
  }

  private class Callback extends AbstractPostOrderCallback {
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (OBJECT_PROPERTY_STRING.equals(n.getQualifiedName())) {
        Node newName =
            Node.newString(Token.NAME, EXTERN_OBJECT_PROPERTY_STRING);
        newName.copyInformationFrom(n);
        parent.replaceChild(n, newName);
        compiler.reportCodeChange();
        return;
      }

      // Rewrite "new goog.testing.ObjectPropertyString(foo, 'bar')" to
      // "new goog.testing.ObjectPropertyString(window, foo.bar)" and
      // issues errors if bad arguments are encountered.
      if (n.getType() != Token.NEW) {
        return;
      }

      Node objectName = n.getFirstChild();

      if (!EXTERN_OBJECT_PROPERTY_STRING.equals(
              objectName.getQualifiedName())) {
        return;
      }

      if (n.getChildCount() != 3) {
        compiler.report(t.makeError(n, INVALID_NUM_ARGUMENTS_ERROR,
            "" + n.getChildCount()));
        return;
      }

      Node firstArgument = objectName.getNext();
      if (!firstArgument.isQualifiedName()) {
        compiler.report(t.makeError(firstArgument,
            QUALIFIED_NAME_EXPECTED_ERROR,
            Token.name(firstArgument.getType())));
        return;
      }

      Node secondArgument = firstArgument.getNext();
      if (secondArgument.getType() != Token.STRING) {
        compiler.report(t.makeError(secondArgument,
            STRING_LITERAL_EXPECTED_ERROR,
            Token.name(secondArgument.getType())));
        return;
      }

      Node newFirstArgument = NodeUtil.newQualifiedNameNode(
          compiler.getCodingConvention().getGlobalObject(),
          firstArgument.getLineno(), firstArgument.getCharno());
      Node newSecondArgument = NodeUtil.newQualifiedNameNode(
          firstArgument.getQualifiedName() + "." +
          firstArgument.getNext().getString(),
          secondArgument.getLineno(), secondArgument.getCharno());
      n.replaceChild(firstArgument, newFirstArgument);
      n.replaceChild(secondArgument, newSecondArgument);

      compiler.reportCodeChange();
    }
  }
}
