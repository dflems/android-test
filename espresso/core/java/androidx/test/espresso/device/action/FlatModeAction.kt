/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.test.espresso.device.action

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.util.Consumer
import androidx.test.espresso.device.context.ActionContext
import androidx.test.espresso.device.controller.DeviceControllerOperationException
import androidx.test.espresso.device.controller.DeviceMode
import androidx.test.espresso.device.util.getResumedActivityOrNull
import androidx.test.espresso.device.util.isRobolectricTest
import androidx.test.platform.device.DeviceController
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor

/** Action to set the test device to be completely flat, like a tablet. */
internal class FlatModeAction() : DeviceAction {
  companion object {
    private val TAG = "FlatModeAction"

    private class WindowLayoutInfoConsumer(
      private val latch: CountDownLatch,
      private val windowInfoTrackerCallbackAdapter: WindowInfoTrackerCallbackAdapter
    ) : Consumer<WindowLayoutInfo> {
      override fun accept(windowLayoutInfo: WindowLayoutInfo) {
        windowLayoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().forEach {
          if (it.state == FoldingFeature.State.FLAT) {
            Log.d(TAG, "Device is in flat mode")
            windowInfoTrackerCallbackAdapter.removeWindowLayoutInfoListener(this)
            latch.countDown()
          }
        }
      }
    }
  }

  override fun perform(context: ActionContext, deviceController: DeviceController) {
    // TODO(b/203801760): Check current device mode and return if already in flat mode.
    if (isRobolectricTest()) {
      deviceController.setDeviceMode(DeviceMode.FLAT.mode)
      return
    }

    val activity =
      getResumedActivityOrNull()
        ?: throw DeviceControllerOperationException(
          "Unable to set device to flat mode because there are no activities in the resumed stage."
        )
    val executor: Executor = Executor { command -> Handler(Looper.getMainLooper()).post(command) }
    val latch: CountDownLatch = CountDownLatch(1)
    val windowInfoTrackerCallbackAdapter =
      WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(activity))
    val windowLayoutInfoConsumer: Consumer<WindowLayoutInfo> =
      WindowLayoutInfoConsumer(
        latch,
        windowInfoTrackerCallbackAdapter,
      )

    windowInfoTrackerCallbackAdapter.addWindowLayoutInfoListener(
      activity,
      executor,
      windowLayoutInfoConsumer
    )

    deviceController.setDeviceMode(DeviceMode.FLAT.mode)
    latch.await()
  }
}
