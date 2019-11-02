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
package com.android.build.attribution.ui.tree

import com.android.build.attribution.ui.colorIcon
import com.android.build.attribution.ui.data.CriticalPathTasksUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.panels.AbstractBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.ChartBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.CriticalPathChartLegend
import com.android.build.attribution.ui.panels.TimeDistributionChart
import com.android.build.attribution.ui.panels.criticalPathHeader
import com.android.build.attribution.ui.panels.headerLabel
import com.android.build.attribution.ui.panels.taskInfoPanel
import com.intellij.ui.treeStructure.SimpleNode
import javax.swing.Icon
import javax.swing.JComponent

class CriticalPathTasksRoot(
  private val data: CriticalPathTasksUiData,
  parent: SimpleNode
) : AbstractBuildAttributionNode(parent, "Critical Path Tasks") {
  private val chartItems: List<TimeDistributionChart.ChartDataItem<TaskUiData>> = createTaskChartItems(data)

  override val presentationIcon: Icon? = null

  override val issuesCountsSuffix: String? = null

  override val timeSuffix: String? = data.criticalPathDuration.durationString()

  override fun buildChildren(): Array<SimpleNode> {
    val nodes = mutableListOf<SimpleNode>()
    for (item in chartItems) {
      when (item) {
        is TimeDistributionChart.SingularChartDataItem<TaskUiData> ->
          nodes.add(TaskNode(item.underlyingData, chartItems, item, this))
        is TimeDistributionChart.AggregatedChartDataItem<TaskUiData> ->
          item.underlyingData.forEach { taskData -> nodes.add(TaskNode(taskData, chartItems, item, this)) }
      }
    }
    return nodes.toTypedArray()
  }

  override fun createComponent(): AbstractBuildAttributionInfoPanel {
    return object : ChartBuildAttributionInfoPanel() {

      override fun createHeader(): JComponent {
        return criticalPathHeader("Tasks", data.criticalPathDuration.durationString())
      }

      override fun createChart(): JComponent {
        return TimeDistributionChart(chartItems, null, true)
      }

      override fun createLegend(): JComponent {
        return CriticalPathChartLegend.createTasksLegendPanel()
      }

      override fun createRightInfoPanel(): JComponent? {
        return null
      }
    }
  }
}

private class TaskNode(
  private val taskData: TaskUiData,
  private val chartItems: List<TimeDistributionChart.ChartDataItem<TaskUiData>>,
  private val selectedChartItem: TimeDistributionChart.ChartDataItem<TaskUiData>,
  parent: SimpleNode
) : AbstractBuildAttributionNode(parent, taskData.taskPath) {

  override val presentationIcon: Icon? = colorIcon(selectedChartItem.legendColor)

  override val issuesCountsSuffix: String? = null

  override val timeSuffix: String? = taskData.executionTime.durationString()

  override fun createComponent(): AbstractBuildAttributionInfoPanel {
    return object : ChartBuildAttributionInfoPanel() {
      override fun createChart(): JComponent {
        return TimeDistributionChart(chartItems, selectedChartItem, false)
      }

      override fun createLegend(): JComponent {
        return CriticalPathChartLegend.createTasksLegendPanel()
      }

      override fun createRightInfoPanel(): JComponent {
        return taskInfoPanel(taskData)
      }

      override fun createHeader(): JComponent {
        return headerLabel(taskData.taskPath)
      }
    }
  }

  override fun buildChildren(): Array<SimpleNode> = emptyArray()
}
