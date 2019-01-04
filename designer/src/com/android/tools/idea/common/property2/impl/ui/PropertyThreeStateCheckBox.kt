/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.property2.impl.ui

import com.android.SdkConstants
import com.android.annotations.VisibleForTesting
import com.android.tools.idea.common.property2.impl.model.ThreeStateBooleanPropertyEditorModel
import com.android.tools.idea.common.property2.impl.support.EditorFocusListener
import com.android.tools.idea.common.property2.impl.support.HelpSupportBinding
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.util.ui.ThreeStateCheckBox

/**
 * A standard control for editing a boolean property value with 3 states: on/off/unset.
 */
class PropertyThreeStateCheckBox(model: ThreeStateBooleanPropertyEditorModel) :
  PropertyTextFieldWithLeftButton(model, CustomThreeStateCheckBox(model)) {

  private val checkBox = leftComponent as CustomThreeStateCheckBox

  @VisibleForTesting
  var state: ThreeStateCheckBox.State
    get() = checkBox.state
    set(value) { checkBox.state = value }

  override fun updateFromModel() {
    super.updateFromModel()
    checkBox.updateFromModel()
  }
}

private class CustomThreeStateCheckBox(private val propertyModel: ThreeStateBooleanPropertyEditorModel) : ThreeStateCheckBox(),
                                                                                                          DataProvider {
  private var stateChangeFromModel = false

  init {
    state = toThreeStateValue(propertyModel.value)
    HelpSupportBinding.registerHelpKeyActions(this, { propertyModel.property })

    addFocusListener(EditorFocusListener(this, propertyModel))
    addPropertyChangeListener { event ->
      if (!stateChangeFromModel && event.propertyName == THREE_STATE_CHECKBOX_STATE) {
        propertyModel.value = fromThreeStateValue(event.newValue)
      }
    }
    PropertyTextField.addBorderAtTextFieldBorderSize(this)
  }

  fun updateFromModel() {
    stateChangeFromModel = true
    try {
      state = toThreeStateValue(propertyModel.value)
    }
    finally {
      stateChangeFromModel = false
    }
  }

  override fun getToolTipText(): String? {
    return propertyModel.tooltip
  }

  override fun getData(dataId: String): Any? {
    return propertyModel.getData(dataId)
  }

  private fun toThreeStateValue(value: String?) =
    when (value) {
      "", null -> ThreeStateCheckBox.State.DONT_CARE
      SdkConstants.VALUE_TRUE -> ThreeStateCheckBox.State.SELECTED
      else -> ThreeStateCheckBox.State.NOT_SELECTED
    }

  private fun fromThreeStateValue(value: Any?) =
    when (value) {
      ThreeStateCheckBox.State.SELECTED -> SdkConstants.VALUE_TRUE
      ThreeStateCheckBox.State.NOT_SELECTED -> SdkConstants.VALUE_FALSE
      else -> ""
    }
}
