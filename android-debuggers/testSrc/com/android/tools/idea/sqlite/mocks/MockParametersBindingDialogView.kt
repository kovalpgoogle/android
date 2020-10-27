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
package com.android.tools.idea.sqlite.mocks

import com.android.tools.idea.sqlite.controllers.SqliteParameter
import com.android.tools.idea.sqlite.ui.parametersBinding.ParametersBindingDialogView

open class MockParametersBindingDialogView : ParametersBindingDialogView {
  val listeners = mutableListOf<ParametersBindingDialogView.Listener>()

  override fun show() { }

  override fun showNamedParameters(parameters: Set<SqliteParameter>) { }

  override fun addListener(listener: ParametersBindingDialogView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: ParametersBindingDialogView.Listener) {
    listeners.remove(listener)
  }
}