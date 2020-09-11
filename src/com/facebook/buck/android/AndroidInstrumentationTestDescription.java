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

package com.facebook.buck.android;

import com.facebook.buck.android.toolchain.AndroidPlatformTarget;
import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.description.arg.HasTestTimeout;
import com.facebook.buck.core.description.attr.ImplicitDepsInferringDescription;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.rules.common.BuildRules;
import com.facebook.buck.core.toolchain.ToolchainProvider;
import com.facebook.buck.core.util.immutables.RuleArg;
import com.facebook.buck.downwardapi.config.DownwardApiConfig;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.java.JavaBuckConfig;
import com.facebook.buck.jvm.java.JavaOptions;
import com.facebook.buck.jvm.java.toolchain.JavaOptionsProvider;
import com.facebook.buck.test.config.TestBuckConfig;
import com.google.common.collect.ImmutableCollection.Builder;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class AndroidInstrumentationTestDescription
    implements DescriptionWithTargetGraph<AndroidInstrumentationTestDescriptionArg>,
        ImplicitDepsInferringDescription<AndroidInstrumentationTestDescriptionArg> {

  private final JavaBuckConfig javaBuckConfig;
  private final TestBuckConfig testBuckConfig;
  private final DownwardApiConfig downwardApiConfig;
  private final ConcurrentHashMap<ProjectFilesystem, ConcurrentHashMap<String, PackagedResource>>
      resourceSupplierCache;
  private final Function<TargetConfiguration, JavaOptions> javaOptionsForTests;

  public AndroidInstrumentationTestDescription(
      JavaBuckConfig javaBuckConfig,
      TestBuckConfig testBuckConfig,
      DownwardApiConfig downwardApiConfig,
      ToolchainProvider toolchainProvider) {
    this.javaBuckConfig = javaBuckConfig;
    this.testBuckConfig = testBuckConfig;
    this.downwardApiConfig = downwardApiConfig;
    this.javaOptionsForTests = JavaOptionsProvider.getDefaultJavaOptionsForTests(toolchainProvider);
    this.resourceSupplierCache = new ConcurrentHashMap<>();
  }

  @Override
  public Class<AndroidInstrumentationTestDescriptionArg> getConstructorArgType() {
    return AndroidInstrumentationTestDescriptionArg.class;
  }

  @Override
  public AndroidInstrumentationTest createBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      AndroidInstrumentationTestDescriptionArg args) {
    BuildRule apk = context.getActionGraphBuilder().getRule(args.getApk());
    if (!(apk instanceof HasInstallableApk)) {
      throw new HumanReadableException(
          "In %s, instrumentation_apk='%s' must be an android_binary(), apk_genrule() or "
              + "android_instrumentation_apk(), but was %s().",
          buildTarget, apk.getFullyQualifiedName(), apk.getType());
    }

    ProjectFilesystem projectFilesystem = context.getProjectFilesystem();
    ToolchainProvider toolchainProvider = context.getToolchainProvider();
    return new AndroidInstrumentationTest(
        buildTarget,
        projectFilesystem,
        toolchainProvider.getByName(
            AndroidPlatformTarget.DEFAULT_NAME,
            buildTarget.getTargetConfiguration(),
            AndroidPlatformTarget.class),
        params.copyAppendingExtraDeps(BuildRules.getExportedRules(params.getDeclaredDeps().get())),
        (HasInstallableApk) apk,
        args.getLabels(),
        args.getContacts(),
        javaOptionsForTests
            .apply(buildTarget.getTargetConfiguration())
            .getJavaRuntimeLauncher(
                context.getActionGraphBuilder(), buildTarget.getTargetConfiguration()),
        args.getTestRuleTimeoutMs()
            .map(Optional::of)
            .orElse(testBuckConfig.getDefaultTestRuleTimeoutMs()),
        getRelativePackagedResource(projectFilesystem, "ddmlib.jar"),
        getRelativePackagedResource(projectFilesystem, "kxml2.jar"),
        getRelativePackagedResource(projectFilesystem, "guava.jar"),
        getRelativePackagedResource(projectFilesystem, "android-tools-common.jar"),
        downwardApiConfig.isEnabledForAndroid(),
        javaBuckConfig.getJavaForTestsVersion());
  }

  /**
   * @return The packaged resource with name {@code resourceName} from the same jar as current class
   *     with path relative to this class location.
   *     <p>Since resources like ddmlib.jar are needed for all {@link AndroidInstrumentationTest}
   *     instances it makes sense to memoize them.
   */
  private PackagedResource getRelativePackagedResource(
      ProjectFilesystem projectFilesystem, String resourceName) {
    return resourceSupplierCache
        .computeIfAbsent(projectFilesystem, fs -> new ConcurrentHashMap<>())
        .computeIfAbsent(
            resourceName,
            resource ->
                new PackagedResource(
                    projectFilesystem, AndroidInstrumentationTestDescription.class, resource));
  }

  @Override
  public void findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellNameResolver cellRoots,
      AndroidInstrumentationTestDescriptionArg constructorArg,
      Builder<BuildTarget> extraDepsBuilder,
      Builder<BuildTarget> targetGraphOnlyDepsBuilder) {
    javaOptionsForTests
        .apply(buildTarget.getTargetConfiguration())
        .addParseTimeDeps(targetGraphOnlyDepsBuilder, buildTarget.getTargetConfiguration());
  }

  @RuleArg
  interface AbstractAndroidInstrumentationTestDescriptionArg extends BuildRuleArg, HasTestTimeout {
    BuildTarget getApk();
  }
}
