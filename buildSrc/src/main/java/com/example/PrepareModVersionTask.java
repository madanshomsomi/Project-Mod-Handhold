package com.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DisableCachingByDefault(because = "The next version depends on the previously recorded source fingerprint")
public abstract class PrepareModVersionTask extends DefaultTask {
	private static final Pattern ALPHA_VERSION = Pattern.compile("(\\d+)\\.(\\d+)-alpha");

	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getSourceFiles();

	@Input
	public abstract Property<String> getBaseVersion();

	@Internal
	public abstract DirectoryProperty getSourceRoot();

	@OutputFile
	public abstract RegularFileProperty getOutputFile();

	@TaskAction
	public void prepareVersion() throws IOException, NoSuchAlgorithmException {
		File stateFile = getOutputFile().get().getAsFile();
		Properties previousState = new Properties();
		if (stateFile.isFile()) {
			try (var input = Files.newInputStream(stateFile.toPath())) {
				previousState.load(input);
			}
		}

		String sourceHash = fingerprintSources();
		String previousHash = previousState.getProperty("sourceHash");
		String previousVersion = previousState.getProperty("version", getBaseVersion().get());
		String version = previousHash == null || previousHash.equals(sourceHash)
				? previousVersion
				: incrementAlphaVersion(previousVersion);

		Properties nextState = new Properties();
		nextState.setProperty("sourceHash", sourceHash);
		nextState.setProperty("version", version);
		Files.createDirectories(stateFile.toPath().getParent());
		try (var output = Files.newOutputStream(stateFile.toPath())) {
			nextState.store(output, "Generated Handhold source version");
		}
		getLogger().lifecycle("Handhold artifact version: {}", version);
	}

	private String fingerprintSources() throws IOException, NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		var sourceRoot = getSourceRoot().get().getAsFile().toPath();
		List<File> sources = getSourceFiles().getFiles().stream()
				.filter(File::isFile)
				.sorted(Comparator.comparing(file -> sourceRoot.relativize(file.toPath()).toString()))
				.toList();

		for (File source : sources) {
			String relativePath = sourceRoot.relativize(source.toPath()).toString().replace(File.separatorChar, '/');
			digest.update(relativePath.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			digest.update(Files.readAllBytes(source.toPath()));
			digest.update((byte) 0);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private String incrementAlphaVersion(String version) {
		Matcher match = ALPHA_VERSION.matcher(version);
		if (!match.matches()) {
			throw new GradleException("Cannot increment unsupported Handhold version: " + version);
		}
		return match.group(1) + "." + (Integer.parseInt(match.group(2)) + 1) + "-alpha";
	}
}
