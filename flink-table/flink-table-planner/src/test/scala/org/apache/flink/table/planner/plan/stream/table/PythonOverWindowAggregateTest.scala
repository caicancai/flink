/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.table.planner.plan.stream.table

import org.apache.flink.table.api._
import org.apache.flink.table.planner.runtime.utils.JavaUserDefinedAggFunctions.PandasAggregateFunction
import org.apache.flink.table.planner.utils.TableTestBase

import org.junit.jupiter.api.Test

class PythonOverWindowAggregateTest extends TableTestBase {

  @Test
  def testPandasRowTimeBoundedPartitionedRangesOver(): Unit = {
    val util = streamTestUtil()
    val sourceTable =
      util.addTableSource[(Int, Long, Int, Long)]("MyTable", 'a, 'b, 'c, 'rowtime.rowtime)
    val func = new PandasAggregateFunction

    val resultTable = sourceTable
      .window(Over.partitionBy('b).orderBy('rowtime).preceding(10.second).as('w))
      .select('b, func('a, 'c).over('w))

    util.verifyExecPlan(resultTable)
  }

  @Test
  def testPandasProcTimeBoundedPartitionedRangesOver(): Unit = {
    val util = streamTestUtil()
    val sourceTable =
      util.addTableSource[(Int, Long, Int)]("MyTable", 'a, 'b, 'c, 'proctime.proctime)
    val func = new PandasAggregateFunction

    val resultTable = sourceTable
      .window(Over.partitionBy('b).orderBy('proctime).preceding(10.second).as('w))
      .select('b, func('a, 'c).over('w))

    util.verifyExecPlan(resultTable)
  }

  @Test
  def testPandasRowTimeBoundedPartitionedRowsOver(): Unit = {
    val util = streamTestUtil()
    val sourceTable =
      util.addTableSource[(Int, Long, Int, Long)]("MyTable", 'a, 'b, 'c, 'rowtime.rowtime)
    val func = new PandasAggregateFunction

    val resultTable = sourceTable
      .window(Over.partitionBy('b).orderBy('rowtime).preceding(10.rows).as('w))
      .select('b, func('a, 'c).over('w))

    util.verifyExecPlan(resultTable)
  }

  @Test
  def testPandasProcTimeBoundedPartitionedRowsOver(): Unit = {
    val util = streamTestUtil()
    val sourceTable =
      util.addTableSource[(Int, Long, Int)]("MyTable", 'a, 'b, 'c, 'proctime.proctime)
    val func = new PandasAggregateFunction

    val resultTable = sourceTable
      .window(Over.partitionBy('b).orderBy('proctime).preceding(10.rows).as('w))
      .select('b, func('a, 'c).over('w))

    util.verifyExecPlan(resultTable)
  }
}
