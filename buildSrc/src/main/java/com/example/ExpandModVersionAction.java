package com.example;

import org.gradle.api.Action;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.provider.Provider;

import java.io.Serializable;
import java.util.Collections;

public final class ExpandModVersionAction implements Action<FileCopyDetails>, Serializable {
	private final Provider<String> version;

	public ExpandModVersionAction(Provider<String> version) {
		this.version = version;
	}

	@Override
	public void execute(FileCopyDetails details) {
		details.expand(Collections.singletonMap("version", version.get()));
	}
}
