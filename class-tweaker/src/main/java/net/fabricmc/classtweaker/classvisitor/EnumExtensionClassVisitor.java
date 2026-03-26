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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.api.EnumExtension;

public class EnumExtensionClassVisitor extends ClassVisitor {
	private final ClassTweaker classTweaker;
	private final Set<String> addedConstants = new LinkedHashSet<>();
	private final List<FieldNode> existingConstants = new ArrayList<>();
	private final List<Runnable> postVisitTasks = new ArrayList<>();
	private Type currentType;

	public EnumExtensionClassVisitor(int api, ClassVisitor classVisitor, ClassTweaker classTweaker) {
		super(api, classVisitor);
		this.classTweaker = classTweaker;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		currentType = Type.getObjectType(name);
		List<EnumExtension> enumExtensions = classTweaker.getEnumExtensions(name);

		if (enumExtensions.isEmpty()) {
			super.visit(version, access, name, signature, superName, interfaces);
			return;
		}

		for (EnumExtension extension : enumExtensions) {
			addedConstants.add(extension.getAddedConstant());
		}

		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		if (currentType.getDescriptor().equals(descriptor)) {
			// Can't add a conflicting constant
			addedConstants.remove(name);
		}

		FieldNode node = new FieldNode(access, name, descriptor, signature, value);

		if ((access & Opcodes.ACC_ENUM) != 0) {
			// Existing enum constant in the class
			existingConstants.add(node);
		} else {
			// Delay the field until after our added constants
			postVisitTasks.add(() -> node.accept(cv));
		}

		return node;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodNode node = new MethodNode(access, name, descriptor, signature, exceptions);
		// Delay the method until after our added constants
		postVisitTasks.add(() -> node.accept(cv));
		return node;
	}

	@Override
	public void visitEnd() {
		if (cv == null) {
			// Nothing to do
			return;
		}

		// Preserve the existing constants first
		for (FieldNode existingConstant : existingConstants) {
			existingConstant.accept(cv);
		}

		// Then add our constants
		for (String addedConstant : addedConstants) {
			FieldVisitor visitor = super.visitField(
					Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC | Opcodes.ACC_ENUM,
					addedConstant,
					currentType.getDescriptor(),
					null,
					null
			);
			visitor.visitAttribute(new StubEnumConstantAttribute());
			visitor.visitEnd();
		}

		// Then add the remaining methods/fields
		for (Runnable task : postVisitTasks) {
			task.run();
		}

		super.visitEnd();
	}

	private static final class StubEnumConstantAttribute extends Attribute {
		StubEnumConstantAttribute() {
			super("org.spongepowered.asm.mixin.StubEnumConstant");
		}

		@Override
		protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
			return new ByteVector();
		}
	}
}
