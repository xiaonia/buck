/*
 * Copyright 2019-present Facebook, Inc.
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

package com.facebook.buck.multitenant.service

import com.facebook.buck.core.model.UnconfiguredBuildTarget
import com.facebook.buck.multitenant.fs.FsAgnosticPath
import java.nio.file.Path

object IndexFactory {

    fun createIndex(): Pair<Index, IndexAppender> {
        val indexGenerationData = DefaultMutableIndexGenerationData()
        return createIndex(indexGenerationData)
    }

    /**
     * Create index, appender and translator
     * @param projectRoot Absolute path to a folder containing Buck project, used to reparse
     * packages. Can point to virtual filesystem.
     * @param buildFileName Name of a build file (for example, BUCK) which defines targets
     */
    fun createIndexComponents(projectRoot: Path, buildFileName: FsAgnosticPath): IndexComponents {
        val indexGenerationData = DefaultMutableIndexGenerationData()
        val (index, indexAppender) = createIndex(indexGenerationData)
        val fsToBuildPackageChangeTranslator =
            DefaultFsToBuildPackageChangeTranslator(indexGenerationData, buildFileName, projectRoot)
        return IndexComponents(index, indexAppender,
            fsToBuildPackageChangeTranslator)
    }

    private fun createIndex(
        indexGenerationData: MutableIndexGenerationData
    ): Pair<Index, IndexAppender> {
        val buildTargetCache = AppendOnlyBidirectionalCache<UnconfiguredBuildTarget>()
        val index = Index(indexGenerationData, buildTargetCache)
        val indexAppender = DefaultIndexAppender(indexGenerationData, buildTargetCache)
        return Pair(index, indexAppender)
    }
}
