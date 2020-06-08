/*
 * Copyright (c) Facebook, Inc. and its affiliates.
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

package com.facebook.buck.core.model.targetgraph.impl;

import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.path.ForwardRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.collect.ImmutableSet;

/** Generic interface to perform some checks of paths. */
public interface PathsChecker {

  /** Check a set of paths relative to the given file system. */
  void checkPaths(
      ProjectFilesystem projectFilesystem,
      BuildTarget buildTarget,
      ImmutableSet<ForwardRelativePath> paths);

  void checkFilePaths(
      ProjectFilesystem projectFilesystem,
      BuildTarget buildTarget,
      ImmutableSet<ForwardRelativePath> filePaths);

  void checkDirPaths(
      ProjectFilesystem projectFilesystem,
      BuildTarget buildTarget,
      ImmutableSet<ForwardRelativePath> dirPaths);
}
