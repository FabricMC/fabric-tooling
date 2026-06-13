package net.fabricmc.annotater.database;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jspecify.annotations.Nullable;

import net.fabricmc.annotater.git.CliGitClient;
import net.fabricmc.annotater.git.GitClient;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.enigma.EnigmaDirReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class GitJavadocDatabase implements JavadocDatabase {
	private static final String SOURCE_NAMESPACE = "official";
	private static final String TARGET_NAMESPACE = "named";
	private static final String AUTHOR_NAME = "Fabric Javadoc Annotater";
	private static final String AUTHOR_EMAIL = "noreply@fabricmc.net";

	private final Path repository;
	private final Path mappings;
	private final GitClient gitClient;
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private final MemoryMappingTree tree;

	public GitJavadocDatabase(Path repository) {
		this(repository, new CliGitClient(repository));
	}

	public GitJavadocDatabase(Path repository, GitClient gitClient) {
		this.repository = repository.toAbsolutePath().normalize();
		this.mappings = this.repository.resolve("mappings");
		this.gitClient = gitClient;
		this.tree = loadMappings();
	}

	@Override
	@Nullable
	public JavadocClassEntry getJavadoc(String className) {
		lock.readLock().lock();

		try {
			MappingTree.ClassMapping cls = tree.getClass(className);

			if (cls == null) {
				return null;
			}

			Map<String, String> methods = new HashMap<>();
			Map<String, String> fields = new HashMap<>();

			for (MappingTree.MethodMapping method : cls.getMethods()) {
				if (method.getComment() != null) {
					methods.put(method.getSrcName() + method.getSrcDesc(), method.getComment());
				}
			}

			for (MappingTree.FieldMapping field : cls.getFields()) {
				if (field.getComment() != null) {
					fields.put(field.getSrcName() + field.getSrcDesc(), field.getComment());
				}
			}

			if (cls.getComment() == null && methods.isEmpty() && fields.isEmpty()) {
				return null;
			}

			return new JavadocClassEntry(cls.getComment(), methods, fields);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public void setClassJavadoc(String className, @Nullable String documentation) {
		update(className, () -> {
			MappingTree.ClassMapping cls = getOrCreateClass(className);
			cls.setComment(documentation);
			removeIfEmpty(cls);
		});
	}

	@Override
	public void setMethodJavadoc(String className, String methodName, String methodDescriptor, @Nullable String documentation) {
		update(className, () -> {
			MappingTree.ClassMapping cls = getOrCreateClass(className);

			if (documentation == null) {
				cls.removeMethod(methodName, methodDescriptor);
			} else {
				MappingTree.MethodMapping method = cls.getMethod(methodName, methodDescriptor);

				if (method == null) {
					method = createMethod(className, methodName, methodDescriptor);
				}

				method.setComment(documentation);
			}

			removeIfEmpty(cls);
		});
	}

	@Override
	public void setFieldJavadoc(String className, String fieldName, String fieldDescriptor, @Nullable String documentation) {
		update(className, () -> {
			MappingTree.ClassMapping cls = getOrCreateClass(className);

			if (documentation == null) {
				cls.removeField(fieldName, fieldDescriptor);
			} else {
				MappingTree.FieldMapping field = cls.getField(fieldName, fieldDescriptor);

				if (field == null) {
					field = createField(className, fieldName, fieldDescriptor);
				}

				field.setComment(documentation);
			}

			removeIfEmpty(cls);
		});
	}

	private MemoryMappingTree loadMappings() {
		try {
			if (!gitClient.isRepository()) {
				throw new IllegalArgumentException("Javadoc repository is not a Git repository: " + repository);
			}

			Files.createDirectories(mappings);

			MemoryMappingTree mappingTree = new MemoryMappingTree();
			EnigmaDirReader.read(mappings, SOURCE_NAMESPACE, TARGET_NAMESPACE, mappingTree);
			return mappingTree;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void update(String className, MappingUpdate update) {
		lock.writeLock().lock();

		try {
			update.run();
			writeMappings();
			gitClient.addAll(mappings);

			if (gitClient.hasStagedChanges(mappings)) {
				gitClient.commit("Update javadocs for " + className, AUTHOR_NAME, AUTHOR_EMAIL);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			lock.writeLock().unlock();
		}
	}

	private MappingTree.ClassMapping getOrCreateClass(String className) throws IOException {
		MappingTree.ClassMapping cls = tree.getClass(className);

		if (cls != null) {
			return cls;
		}

		tree.visitClass(className);
		tree.visitElementContent(MappedElementKind.CLASS);
		return Objects.requireNonNull(tree.getClass(className));
	}

	private MappingTree.MethodMapping createMethod(String className, String methodName, String methodDescriptor) throws IOException {
		tree.visitClass(className);
		tree.visitMethod(methodName, methodDescriptor);
		tree.visitElementContent(MappedElementKind.METHOD);
		return Objects.requireNonNull(tree.getClass(className)).getMethod(methodName, methodDescriptor);
	}

	private MappingTree.FieldMapping createField(String className, String fieldName, String fieldDescriptor) throws IOException {
		tree.visitClass(className);
		tree.visitField(fieldName, fieldDescriptor);
		tree.visitElementContent(MappedElementKind.FIELD);
		return Objects.requireNonNull(tree.getClass(className)).getField(fieldName, fieldDescriptor);
	}

	private void removeIfEmpty(MappingTree.ClassMapping cls) {
		if (cls.getComment() == null && cls.getMethods().isEmpty() && cls.getFields().isEmpty()) {
			tree.removeClass(cls.getSrcName());
		}
	}

	private void writeMappings() throws IOException {
		try (MappingWriter writer = Objects.requireNonNull(MappingWriter.create(mappings, MappingFormat.ENIGMA_DIR))) {
			tree.accept(writer);
		}
	}

	@FunctionalInterface
	private interface MappingUpdate {
		void run() throws IOException;
	}
}
