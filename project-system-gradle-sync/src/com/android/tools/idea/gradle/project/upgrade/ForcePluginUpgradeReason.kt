/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.SdkConstants
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.upgrade.ForcePluginUpgradeReason.MAXIMUM
import com.android.tools.idea.gradle.project.upgrade.ForcePluginUpgradeReason.MINIMUM
import com.android.tools.idea.gradle.project.upgrade.ForcePluginUpgradeReason.NO_FORCE
import com.android.tools.idea.gradle.project.upgrade.ForcePluginUpgradeReason.PREVIEW

enum class ForcePluginUpgradeReason {
  NO_FORCE,
  MINIMUM,
  PREVIEW,
  MAXIMUM,
}

/**
 * AGP [current] and Studio's [latestKnown] can each be of "release class": alpha/beta, rc/release, or snapshot (-dev).
 * Their major/minor release number can be equal, or one major/minor release can precede the other.
 * If they are of the same major/minor and non-snapshot release class, then either can precede the other.  (All snapshots of the same
 * major/minor cycle compare equal, even if they reflect completely different actual code...)
 *
 * That gives 3 release classes * 2 distinct release classes * 3 major/minor orderings
 *          + 3 release classes * 2 major/minor orderings
 *          + 2 release classes * 1 major/minor equality * 2 patchlevel orderings
 *          ---------------------------------------------------------------------
 *          28 cases, not counting the exact equality case or the minimum supported version case
 *
 * Some of the complexity below is handling the fact that the "natural" version comparison places -dev between rc and release, whereas
 * we want to treat rc and release equivalently.
 *
 * Examples below are generated using as reference points:
 * - 7.1.0-alpha01, 7.1.0-rc01, 7.1.0-dev, with additional 7.1.0-alpha02, 7.1.0 when comparing within major/minor/release class
 * - 7.0.0-alpha01, 7.0.0-rc01, 7.0.0-dev
 * - 7.2.0-alpha01, 7.2.0-rc01, 7.2.0-dev
 *
 * There should be 9 cases with current: 7.1.0-alpha01, 9 with current: 7.1.0-rc01, 8 with current: 7.1.0-dev, one each of
 * current: 7.1.0-alpha02 and 7.1.0.
 *
 * There should be 3 cases with latestKnown: 7.0.0-alpha01, 3 with latestKnown: 7.0.0-rc01, 3 with latestKnown: 7.0.0-dev,
 * 3 cases with latestKnown: 7.2.0-alpha01, 3 with latestKnown: 7.2.0-rc01, 3 with latestKnown: 7.2.0-dev,
 * 3 cases with latestKnown: 7.1.0-alpha01, 3 with latestKnown: 7.1.0-rc01, 2 with latestKnown: 7.1.0-dev,
 * 1 with latestKnown: 7.1.0-alpha02, 1 with latestKnown: 7.1.0
 */
fun computeForcePluginUpgradeReason(current: GradleVersion, latestKnown: GradleVersion): ForcePluginUpgradeReason =
  when {
    // If the current and latestKnown are equal, compatible.
    // e.g. current = 7.1.0-alpha09, latestKnown = 7.1.0-alpha09
    current == latestKnown -> NO_FORCE
    // If the current is lower than our minimum supported version, incompatible.
    // e.g. current = 3.1.0, latestKnown = 7.1.0-alpha09
    current < GradleVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION) -> MINIMUM
    // If the current/latestKnown are RC or releases, and of the same major/minor series, compatible. (2)
    // e.g. current = 7.1.0-rc01, latestKnown = 7.1.0
    //      current = 7.1.0, latestKnown = 7.1.0-rc01
    (!latestKnown.isPreview || latestKnown.previewType == "rc") && (!current.isPreview || current.previewType == "rc") &&
    GradleVersion(latestKnown.major, latestKnown.minor) == GradleVersion(current.major, current.minor) -> NO_FORCE
    // If the current is a snapshot and latestKnown is RC or release of the same major/minor series, incompatible. (1)
    // e.g. current = 7.1.0-dev, latestKnown = 7.1.0-rc01
    current.isSnapshot && (!latestKnown.isPreview || latestKnown.previewType == "rc") &&
    GradleVersion(latestKnown.major, latestKnown.minor) == GradleVersion(current.major, current.minor) -> PREVIEW
    // If the current is a snapshot and latestKnown is alpha/beta of the same major/minor series, compatible. (1)
    // e.g. current = 7.1.0-dev, latestKnown = 7.1.0-alpha01
    current.isSnapshot && (latestKnown.previewType == "alpha" || latestKnown.previewType == "beta") &&
    GradleVersion(latestKnown.major, latestKnown.minor) == GradleVersion(current.major, current.minor) -> NO_FORCE
    // If the current is later than latestKnown, incompatible. (11)
    // e.g. current = 7.1.0-dev, latestKnown = 7.0.0-rc01
    //      current = 7.1.0-dev, latestKnown = 7.0.0-dev
    //      current = 7.1.0-dev, latestKnown = 7.0.0-alpha01
    //      current = 7.1.0-alpha01, latestKnown = 7.0.0-rc01
    //      current = 7.1.0-alpha01, latestKnown = 7.0.0-dev
    //      current = 7.1.0-alpha01, latestKnown = 7.0.0-alpha01
    //      current = 7.1.0-rc01, latestKnown = 7.0.0-rc01
    //      current = 7.1.0-rc01, latestKnown = 7.0.0-dev
    //      current = 7.1.0-rc01, latestKnown = 7.0.0-alpha01
    //      current = 7.1.0-rc01, latestKnown = 7.1.0-alpha01
    //      current = 7.1.0-alpha02, latestKnown = 7.1.0-alpha01
    current > latestKnown -> MAXIMUM
    // If the current is a preview and the latest known is not a -dev version, incompatible. (4)
    // e.g. current = 7.1.0-alpha01, latestKnown = 7.1.0-rc01
    //      current = 7.1.0-alpha01, latestKnown = 7.1.0-alpha02
    //      current = 7.1.0-alpha01, latestKnown = 7.2.0-rc01
    //      current = 7.1.0-alpha01, latestKnown = 7.2.0-alpha01
    (current.previewType == "alpha" || current.previewType == "beta") && !latestKnown.isSnapshot -> PREVIEW
    // If the current is a snapshot (and therefore of an earlier series than latestKnown), incompatible. (3)
    // e.g. current = 7.1.0-dev, latestKnown = 7.2.0-rc01
    //      current = 7.1.0-dev, latestKnown = 7.2.0-alpha01
    //      current = 7.1.0-dev, latestKnown = 7.2.0-dev
    current.isSnapshot -> PREVIEW
    // Otherwise, compatible. (6)
    // e.g. current = 7.1.0-rc01, latestKnown = 7.2.0-alpha01
    //      current = 7.1.0-rc01, latestKnown = 7.2.0-rc01
    //      current = 7.1.0-rc01, latestKnown = 7.2.0-dev
    //      current = 7.1.0-rc01, latestKnown = 7.1.0-dev
    //      current = 7.1.0-alpha01, latestKnown = 7.1.0-dev
    //      current = 7.1.0-alpha01, latestKnown = 7.2.0-dev
    else -> NO_FORCE
  }
