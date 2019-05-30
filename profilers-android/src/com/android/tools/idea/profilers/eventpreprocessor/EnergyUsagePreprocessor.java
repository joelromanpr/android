/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.profilers.eventpreprocessor;

import com.android.tools.datastore.LogService;
import com.android.tools.datastore.database.UnifiedEventsTable;
import com.android.tools.datastore.energy.BatteryModel;
import com.android.tools.datastore.energy.CpuConfig;
import com.android.tools.datastore.energy.PowerProfile;
import com.android.tools.idea.transport.TransportEventPreprocessor;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Network;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A preprocessor that handles CPU, network and location events to calculate energy usage and insert energy usage events into
 * {@link UnifiedEventsTable}.
 */
public class EnergyUsagePreprocessor implements TransportEventPreprocessor {
  private static final long DEFAULT_SAMPLE_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(200);

  @NotNull private final LogService myLogService;
  @NotNull private final BatteryModel myBatteryModel;
  private long mySampleInterval;

  private long myDataStartTimestampNs = Long.MIN_VALUE;
  @Nullable private CpuConfig myCpuConfig = null;
  @Nullable private Cpu.CpuUsageData myLastCpuUsageData = null;
  @NotNull private PowerProfile.NetworkType myLastNetworkType = PowerProfile.NetworkType.NONE;

  public EnergyUsagePreprocessor(@NotNull LogService logService) {
    this(logService, new BatteryModel(), DEFAULT_SAMPLE_INTERVAL_NS);
  }

  @VisibleForTesting
  public EnergyUsagePreprocessor(@NotNull LogService logService,
                                 @NotNull BatteryModel batteryModel,
                                 long sampleInterval) {
    myLogService = logService;
    myBatteryModel = batteryModel;
    mySampleInterval = sampleInterval;
  }

  @Override
  public boolean shouldPreprocess(Common.Event event) {
    // TODO(b/129355112): add location events.
    switch (event.getKind()) {
      case CPU_CORE_CONFIG:
      case CPU_USAGE:
      case NETWORK_TYPE:
      case NETWORK_SPEED:
        return true;
      default:
        return false;
    }
  }

  @Override
  public Iterable<Common.Event> preprocessEvent(Common.Event event) {
    // TODO(b/129355112): handle location events.
    switch (event.getKind()) {
      case CPU_CORE_CONFIG:
        myCpuConfig = new CpuConfig(event.getCpuCoreConfig(), myLogService);
        break;
      case CPU_USAGE:
        if (myCpuConfig == null) {
          // CPU core config is required to calculate CPU energy usage.
          break;
        }
        if (myLastCpuUsageData != null) {
          myBatteryModel.handleEvent(event.getTimestamp(), BatteryModel.Event.CPU_USAGE,
                                     myCpuConfig.getCpuCoreUsages(myLastCpuUsageData, event.getCpuUsage()));
        }
        myLastCpuUsageData = event.getCpuUsage();
        return generateEnergyUsageEvents(event);
      case NETWORK_TYPE:
        myLastNetworkType = PowerProfile.NetworkType.from(event.getNetworkType().getNetworkType());
        break;
      case NETWORK_SPEED:
        Network.NetworkSpeedData speedData = event.getNetworkSpeed();
        long rxSpeed = 0;
        long txSpeed = 0;
        if (event.getGroupId() == Common.Event.EventGroupIds.NETWORK_RX_VALUE) {
          rxSpeed = speedData.getThroughput();
        }
        else if (event.getGroupId() == Common.Event.EventGroupIds.NETWORK_TX_VALUE) {
          txSpeed = speedData.getThroughput();
        }
        myBatteryModel.handleEvent(event.getTimestamp(), BatteryModel.Event.NETWORK_USAGE,
                                   new PowerProfile.NetworkStats(myLastNetworkType, rxSpeed, txSpeed));
        return generateEnergyUsageEvents(event);
      default:
        break;
    }
    return Collections.emptyList();
  }

  /**
   * If the latest event has been more than 200 ms since the last generation, generate new usage events from energy samples.
   *
   * @return list of energy usage events generated from energy samples.
   */
  private List<Common.Event> generateEnergyUsageEvents(Common.Event event) {
    if (myDataStartTimestampNs == Long.MIN_VALUE) {
      myDataStartTimestampNs = event.getTimestamp();
    }
    if (event.getTimestamp() - myDataStartTimestampNs > mySampleInterval) {
      List<Common.Event> result = new ArrayList<>();
      myBatteryModel.getSamplesBetween(myDataStartTimestampNs, event.getTimestamp()).forEach(
        sample -> result.add(Common.Event.newBuilder()
                               .setPid(event.getPid())
                               .setTimestamp(sample.getTimestamp())
                               .setKind(Common.Event.Kind.ENERGY_USAGE)
                               .setEnergyUsage(sample.getEnergyUsage())
                               .build()));
      myDataStartTimestampNs = event.getTimestamp();
      return result;
    }
    return Collections.emptyList();
  }
}
