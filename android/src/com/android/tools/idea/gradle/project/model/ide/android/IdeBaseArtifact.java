/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Creates a deep copy of {@link BaseArtifact}.
 *
 * @see IdeAndroidProject
 */
public abstract class IdeBaseArtifact implements BaseArtifact, Serializable {
  @NotNull private final String myName;
  @NotNull private final String myCompileTaskName;
  @NotNull private final String myAssembleTaskName;
  @NotNull private final File myClassesFolder;
  @NotNull private final File myJavaResourcesFolder;
  @NotNull private final Dependencies myDependencies;
  @NotNull private final Dependencies myCompileDependencies;
  @NotNull private final DependencyGraphs myDependencyGraphs;
  @NotNull private final Set<String> myIdeSetupTaskNames;
  @NotNull private final Collection<File> myGeneratedSourceFolders;
  @Nullable private final IdeSourceProvider myVariantSourceProvider;
  @Nullable private final IdeSourceProvider myMultiFlavorSourceProvider;

  public IdeBaseArtifact(@NotNull BaseArtifact artifact, @NotNull ModelCache seen, @NotNull GradleVersion gradleVersion) {
    myName = artifact.getName();
    myCompileTaskName = artifact.getCompileTaskName();
    myAssembleTaskName = artifact.getAssembleTaskName();
    myClassesFolder = artifact.getClassesFolder();
    myJavaResourcesFolder = artifact.getJavaResourcesFolder();
    myDependencies = new IdeDependencies(artifact.getDependencies(), seen, gradleVersion);
    //noinspection deprecation
    myCompileDependencies = new IdeDependencies(artifact.getCompileDependencies(), seen, gradleVersion);

    if (gradleVersion.isAtLeast(2, 3, 0)) {
      myDependencyGraphs = new IdeDependencyGraphs(artifact.getDependencyGraphs());
    }
    else {
      myDependencyGraphs = new IdeDependencyGraphs(null);
    }
    myIdeSetupTaskNames = new HashSet<>(artifact.getIdeSetupTaskNames());
    myGeneratedSourceFolders = new ArrayList<>(artifact.getGeneratedSourceFolders());
    myVariantSourceProvider = createSourceProvider(artifact.getVariantSourceProvider());
    myMultiFlavorSourceProvider = createSourceProvider(artifact.getMultiFlavorSourceProvider());
  }

  @Nullable
  private static IdeSourceProvider createSourceProvider(@Nullable SourceProvider original) {
    return original != null ? new IdeSourceProvider(original) : null;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getCompileTaskName() {
    return myCompileTaskName;
  }

  @Override
  @NotNull
  public String getAssembleTaskName() {
    return myAssembleTaskName;
  }

  @Override
  @NotNull
  public File getClassesFolder() {
    return myClassesFolder;
  }

  @Override
  @NotNull
  public File getJavaResourcesFolder() {
    return myJavaResourcesFolder;
  }

  @Override
  @NotNull
  public Dependencies getDependencies() {
    return myDependencies;
  }

  @Override
  @NotNull
  public Dependencies getCompileDependencies() {
    return myCompileDependencies;
  }

  @Override
  @NotNull
  public DependencyGraphs getDependencyGraphs() {
    return myDependencyGraphs;
  }

  @Override
  @NotNull
  public Set<String> getIdeSetupTaskNames() {
    return myIdeSetupTaskNames;
  }

  @Override
  @NotNull
  public Collection<File> getGeneratedSourceFolders() {
    return myGeneratedSourceFolders;
  }

  @Override
  @Nullable
  public IdeSourceProvider getVariantSourceProvider() {
    return myVariantSourceProvider;
  }

  @Override
  @Nullable
  public IdeSourceProvider getMultiFlavorSourceProvider() {
    return myMultiFlavorSourceProvider;
  }
}
