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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

public class EnumExtensionClassVisitorTest extends ClassVisitorTest {
	@Test
	void testSimple() throws Exception {
		classTweaker.visitEnumExtension("test/EnumTests", "CONSTANT3", false);
		Class<?> testClass = applyTransformer("test/EnumTests");
		assertThat(testClass.getField("CONSTANT3"))
				.satisfies(field -> assertThat(field.getModifiers()).isEqualTo(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM));
	}

	@Test
	void testMultipleConstants() throws Exception {
		classTweaker.visitEnumExtension("test/EnumTests", "CONSTANT3", false);
		classTweaker.visitEnumExtension("test/EnumTests", "CONSTANT4", false);
		Class<?> testClass = applyTransformer("test/EnumTests");
		assertThat(List.of("CONSTANT1", "CONSTANT2", "CONSTANT3", "CONSTANT4"))
				.map(testClass::getField)
				.allSatisfy(field -> assertThat(field.getModifiers()).isEqualTo(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM));
	}

	@Test
	void testAbstract() throws Exception {
		classTweaker.visitEnumExtension("test/AbstractEnumTests", "CONSTANT3", false);
		Class<?> testClass = applyTransformer("test/AbstractEnumTests");
		assertThat(testClass.getField("CONSTANT3"))
				.satisfies(field -> assertThat(field.getModifiers()).isEqualTo(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM));
	}

	@Test
	void testExistingConstant() throws Exception {
		classTweaker.visitEnumExtension("test/EnumTests", "CONSTANT1", false);
		Class<?> testClass = applyTransformer("test/EnumTests");
		// Mostly checking for VerifyError
		assertThat(testClass.getField("CONSTANT1").get(null)).isNotNull();
	}
}
