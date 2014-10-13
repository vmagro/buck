/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.rules;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.HasOutputName;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.MorePaths;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class SourcePathResolver {

  public static final Function<PathSourcePath, Path> TO_PATH_SOURCEPATH_REFERENCES =
      new Function<PathSourcePath, Path>() {
        @Override
        public Path apply(PathSourcePath input) {
          return input.getRelativePath();
        }
      };
  public static final Function<BuildRuleSourcePath, BuildRule> TO_BUILD_RULE_REFERENCES =
      new Function<BuildRuleSourcePath, BuildRule>() {
        @Override
        public BuildRule apply(BuildRuleSourcePath input) {
          return input.getRule();
        }
      };

  @SuppressWarnings("unused") // Needed once SourcePath embed BuildTarget instead of BuildRule
  private final BuildRuleResolver ruleResolver;

  public SourcePathResolver(BuildRuleResolver ruleResolver) {
    this.ruleResolver = Preconditions.checkNotNull(ruleResolver);
  }

  public Path getPath(SourcePath sourcePath) {
    if (sourcePath instanceof PathSourcePath) {
      return ((PathSourcePath) sourcePath).getRelativePath();
    }
    Preconditions.checkArgument(sourcePath instanceof BuildRuleSourcePath);
    BuildRuleSourcePath buildRuleSourcePath = (BuildRuleSourcePath) sourcePath;
    Optional<Path> resolvedPath = buildRuleSourcePath.getResolvedPath();
    if (resolvedPath.isPresent()) {
      return resolvedPath.get();
    }

    Path path = buildRuleSourcePath.getRule().getPathToOutputFile();
    if (path == null) {
      throw new HumanReadableException("No known output for: %s", buildRuleSourcePath.getRule());
    }

    return path;
  }

  public Function<SourcePath, Path> getPathFunction() {
    return new Function<SourcePath, Path>() {
      @Override
      public Path apply(SourcePath input) {
        return getPath(input);
      }
    };
  }

  public ImmutableList<Path> getAllPaths(Iterable<? extends SourcePath> sourcePaths) {
    // Maintain ordering and duplication if necessary.
    return FluentIterable.from(sourcePaths).transform(getPathFunction()).toList();
  }

  public <T> ImmutableMap<T, Path> getMappedPaths(Map<T, SourcePath> sourcePathMap) {
    ImmutableMap.Builder<T, Path> paths = ImmutableMap.builder();
    for (ImmutableMap.Entry<T, SourcePath> entry : sourcePathMap.entrySet()) {
      paths.put(entry.getKey(), getPath(entry.getValue()));
    }
    return paths.build();
  }

  /**
   * @return An {@link Optional} containing the {@link BuildRule} whose output {@code sourcePath}
   *         refers to, or {@code absent} if {@code sourcePath} doesn't refer to the output of a
   *         {@link BuildRule}.
   */
  public Optional<BuildRule> getRule(SourcePath sourcePath) {
    if (sourcePath instanceof PathSourcePath) {
      return Optional.absent();
    }
    Preconditions.checkState(sourcePath instanceof BuildRuleSourcePath);
    return Optional.of(((BuildRuleSourcePath) sourcePath).getRule());
  }

  /**
   * @return An {@link Optional} containing the {@link Path} the {@code sourcePath} refers to if it
   *         doesn't refer to the output of a {@link BuildRule}, or {@code absent} if it does.
   */
  public Optional<Path> getRelativePath(SourcePath sourcePath) {
    if (sourcePath instanceof BuildRuleSourcePath) {
      return Optional.absent();
    }
    Preconditions.checkState(sourcePath instanceof PathSourcePath);
    return Optional.of(((PathSourcePath) sourcePath).getRelativePath());
  }

  public boolean isSourcePathExtensionInSet(
      SourcePath sourcePath,
      Set<String> extensions) {
    return extensions.contains(Files.getFileExtension(getPath(sourcePath).toString()));
  }

  /**
   * Resolved the logical names for a group of SourcePath objects into a map, throwing an
   * error on duplicates.
   */
  public ImmutableMap<String, SourcePath> getSourcePathNames(
      BuildTarget target,
      String parameter,
      Iterable<SourcePath> sourcePaths) {

    Map<String, SourcePath> resolved = Maps.newHashMap();

    for (SourcePath path : sourcePaths) {
      String name = getSourcePathName(target, path);
      SourcePath old = resolved.put(name, path);
      if (old != null) {
        throw new HumanReadableException(String.format(
            "%s: parameter '%s': duplicate entries for '%s'",
            target,
            parameter,
            name));
      }
    }

    return ImmutableMap.copyOf(resolved);
  }

  public String getSourcePathName(BuildTarget target, SourcePath sourcePath) {
    if (sourcePath instanceof BuildRuleSourcePath) {
      return getNameForRule(((BuildRuleSourcePath) sourcePath).getRule());
    }
    Preconditions.checkArgument(sourcePath instanceof PathSourcePath);
    Path path = ((PathSourcePath) sourcePath).getRelativePath();
    return MorePaths.relativize(target.getBasePath(), path).toString();
  }

  private String getNameForRule(BuildRule rule) {
    // This is called by the constructors before rule has been checked for nullity
    Preconditions.checkNotNull(rule);

    // If this build rule implements `HasOutputName`, then return the output name
    // it provides.
    if (rule instanceof HasOutputName) {
      HasOutputName hasOutputName = (HasOutputName) rule;
      return hasOutputName.getOutputName();
    }

    // Otherwise, fall back to using the short name of rule's build target.
    return rule.getBuildTarget().getShortNameOnly();
  }

  /**
   * Takes an {@link Iterable} of {@link SourcePath} objects and filters those that are suitable to
   * be returned by {@link com.facebook.buck.rules.AbstractBuildRule#getInputsToCompareToOutput()}.
   */
  public ImmutableCollection<Path> filterInputsToCompareToOutput(
      Iterable<? extends SourcePath> sources) {
    // Currently, the only implementation of SourcePath that should be included in the Iterable
    // returned by getInputsToCompareToOutput() is FileSourcePath, so it is safe to filter by that
    // and then use .asReference() to get its path.
    //
    // BuildRuleSourcePath should not be included in the output because it refers to a generated
    // file, and generated files are not hashed as part of a RuleKey.
    return FluentIterable.from(sources)
        .filter(PathSourcePath.class)
        .transform(TO_PATH_SOURCEPATH_REFERENCES)
        .toList();
  }

  public ImmutableCollection<Path> filterInputsToCompareToOutput(SourcePath... sources) {
    return filterInputsToCompareToOutput(Arrays.asList(sources));
  }

  public Collection<BuildRule> filterBuildRuleInputs(
      Iterable<? extends SourcePath> sources) {
    return FluentIterable.from(sources)
        .filter(BuildRuleSourcePath.class)
        .transform(TO_BUILD_RULE_REFERENCES)
        .toList();
  }
}
