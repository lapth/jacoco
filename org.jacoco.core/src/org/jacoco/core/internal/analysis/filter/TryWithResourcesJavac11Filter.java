/*******************************************************************************
 * Copyright (c) 2009, 2018 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.internal.analysis.filter;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

/**
 * TODO http://hg.openjdk.java.net/jdk/jdk/rev/c2a3a2aa2475
 * https://bugs.openjdk.java.net/browse/JDK-8194978
 */
public final class TryWithResourcesJavac11Filter implements IFilter {

	public void filter(final MethodNode methodNode,
			final IFilterContext context, final IFilterOutput output) {
		for (TryCatchBlockNode t : methodNode.tryCatchBlocks) {
			if ("java/lang/Throwable".equals(t.type)) {
				new Matcher(true).match(t.handler, output);
				new Matcher(false).match(t.handler, output);
			}
		}
	}

	private class Matcher extends AbstractMatcher {
		private final boolean p;

		private String expectedOwner;

		Matcher(boolean p) {
			this.p = p;
		}

		public void match(final AbstractInsnNode start,
				final IFilterOutput output) {
			cursor = start.getPrevious();
			nextIsVar(Opcodes.ASTORE, "primaryExc");
			nextIsJavacClose();
			nextIs(Opcodes.GOTO);
			nextIsVar(Opcodes.ASTORE, "t");
			nextIsVar(Opcodes.ALOAD, "primaryExc");
			nextIsVar(Opcodes.ALOAD, "t");
			nextIsInvokeVirtual("java/lang/Throwable", "addSuppressed"); // primaryExc.addSuppressed(t)
			nextIsVar(Opcodes.ALOAD, "primaryExc");
			nextIs(Opcodes.ATHROW);
			if (cursor == null) {
				return;
			}
			final AbstractInsnNode end = cursor;

			AbstractInsnNode s = start.getPrevious();
			cursor = start.getPrevious();
			while (!nextIsJavacClose()) {
				s = s.getPrevious();
				cursor = s;
				if (cursor == null) {
					return;
				}
			}
			s = s.getNext();

			final AbstractInsnNode m = cursor;
			next();
			if (cursor.getOpcode() != Opcodes.GOTO) {
				cursor = m;
			}

			output.ignore(s, cursor);
			output.ignore(start, end);
		}

		private boolean nextIsJavacClose() {
			if (p) {
				nextIsVar(Opcodes.ALOAD, "r");
				nextIs(Opcodes.IFNULL);
			}
			nextIsClose();
			return cursor != null;
		}

		/**
		 * FIXME duplicates {@link TryWithResourcesJavacFilter.Matcher#nextIsClose()}
		 */
		private void nextIsClose() {
			nextIsVar(Opcodes.ALOAD, "r");
			next();
			if (cursor == null) {
				return;
			}
			if (cursor.getOpcode() != Opcodes.INVOKEINTERFACE
					&& cursor.getOpcode() != Opcodes.INVOKEVIRTUAL) {
				cursor = null;
				return;
			}
			final MethodInsnNode m = (MethodInsnNode) cursor;
			if (!"close".equals(m.name) || !"()V".equals(m.desc)) {
				cursor = null;
				return;
			}
			final String actual = m.owner;
			if (expectedOwner == null) {
				expectedOwner = actual;
			} else if (!expectedOwner.equals(actual)) {
				cursor = null;
			}
		}

	}

}
