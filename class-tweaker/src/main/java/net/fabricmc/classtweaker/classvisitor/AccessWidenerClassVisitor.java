/*
 * Copyright (c) 2020 FabricMC
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

package net.fabricmc.classtweaker.classvisitor;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.classtweaker.api.AccessWidener;
import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.impl.ClassTweakerImpl;
import net.fabricmc.classtweaker.utils.EntryTriple;

/**
 * Applies rules from an {@link ClassTweakerImpl} by transforming Java classes using an ASM {@link ClassVisitor}.
 */
public final class AccessWidenerClassVisitor extends ClassVisitor {
	private final ClassTweaker classTweaker;
	private String className;
	private int classAccess;

	private AccessWidener accessWidener = null;

	private boolean shouldDeferRecordMethods;
	private final StringBuilder recordDescriptor = new StringBuilder("(");
	private final List<MethodNode> recordMethods = new ArrayList<>();

	public AccessWidenerClassVisitor(int api, ClassVisitor classVisitor, ClassTweaker classTweaker) {
		super(api, classVisitor);
		this.classTweaker = classTweaker;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		className = name;
		classAccess = access;
		accessWidener = classTweaker.getAccessWidener(name);

		shouldDeferRecordMethods = (access & Opcodes.ACC_RECORD) != 0;

		super.visit(
				version,
				accessWidener.getClassAccess().apply(access, name, classAccess),
				name,
				signature,
				superName,
				interfaces
		);
	}

	@Override
	public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
		recordDescriptor.append(descriptor);

		return super.visitRecordComponent(name, descriptor, signature);
	}

	@Override
	public void visitPermittedSubclass(String permittedSubclass) {
		final AccessWidener.Access access = accessWidener.getClassAccess();

		if (access.isExtendable()) {
			return;
		}

		super.visitPermittedSubclass(permittedSubclass);
	}

	@Override
	public void visitInnerClass(String name, String outerName, String innerName, int access) {
		super.visitInnerClass(
				name,
				outerName,
				innerName,
				classTweaker.getAccessWidener(name).getClassAccess().apply(access, name, classAccess)
		);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		return super.visitField(
				accessWidener.getFieldAccess(new EntryTriple(className, name, descriptor)).apply(access, name, classAccess),
				name,
				descriptor,
				signature,
				value
		);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		if (shouldDeferRecordMethods) {
			// Defer record method, since we may not know the full record descriptor yet
			// and we cannot immediately widen the canonical constructor therefore.
			// Note that we defer all methods so that method order remains the same.
			MethodNode constructor = new MethodNode(access, name, descriptor, signature, exceptions);
			recordMethods.add(constructor);

			return constructor;
		}

		return new AccessWidenerMethodVisitor(super.visitMethod(
				accessWidener.getMethodAccess(new EntryTriple(className, name, descriptor)).apply(access, name, classAccess),
				name,
				descriptor,
				signature,
				exceptions
		));
	}

	@Override
	public void visitEnd() {
		if (!shouldDeferRecordMethods) {
			super.visitEnd();
			return;
		}

		// By setting this to false now, visitMethod will no longer defer the constructors, and we can
		// re-use `visitMethod` via `MethodNode::accept(ClassVisitor)`
		shouldDeferRecordMethods = false;

		String canonicalDesc = recordDescriptor.append(")V").toString();

		for (MethodNode constructor : recordMethods) {
			// Widen canonical record constructor
			if (constructor.desc.equals(canonicalDesc)) {
				constructor.access = accessWidener.getCanonicalConstructorAccess().apply(constructor.access, constructor.name, classAccess);
			}

			constructor.accept(this);
		}

		super.visitEnd();
	}

	private class AccessWidenerMethodVisitor extends MethodVisitor {
		AccessWidenerMethodVisitor(MethodVisitor methodVisitor) {
			super(AccessWidenerClassVisitor.this.api, methodVisitor);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			if (opcode == Opcodes.INVOKESPECIAL && isTargetMethod(owner, name, descriptor)) {
				opcode = Opcodes.INVOKEVIRTUAL;
			}

			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
			for (int i = 0; i < bootstrapMethodArguments.length; i++) {
				if (bootstrapMethodArguments[i] instanceof Handle) {
					final Handle handle = (Handle) bootstrapMethodArguments[i];

					if (handle.getTag() == Opcodes.H_INVOKESPECIAL && isTargetMethod(handle.getOwner(), handle.getName(), handle.getDesc())) {
						bootstrapMethodArguments[i] = new Handle(Opcodes.H_INVOKEVIRTUAL, handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface());
					}
				}
			}

			super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
		}

		private boolean isTargetMethod(String owner, String name, String descriptor) {
			return owner.equals(className) && !name.equals("<init>") && accessWidener.getMethodAccess(new EntryTriple(owner, name, descriptor)).isChanged();
		}
	}
}
