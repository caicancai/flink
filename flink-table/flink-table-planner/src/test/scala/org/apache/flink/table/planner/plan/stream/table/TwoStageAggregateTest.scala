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
import org.apache.flink.table.api.config.{AggregatePhaseStrategy, ExecutionConfigOptions, OptimizerConfigOptions}
import org.apache.flink.table.planner.utils.{StreamTableTestUtil, TableTestBase}

import org.junit.jupiter.api.{BeforeEach, Test}

import java.time.Duration

class TwoStageAggregateTest extends TableTestBase {

  private var util: StreamTableTestUtil = _
  @BeforeEach
  def before(): Unit = {
    util = streamTestUtil()
    util.tableEnv.getConfig
      .setIdleStateRetention(Duration.ofHours(1))
    util.tableEnv.getConfig
      .set(ExecutionConfigOptions.TABLE_EXEC_MINIBATCH_ALLOW_LATENCY, Duration.ofSeconds(1))
      .set(ExecutionConfigOptions.TABLE_EXEC_MINIBATCH_ENABLED, Boolean.box(true))
      .set(ExecutionConfigOptions.TABLE_EXEC_MINIBATCH_SIZE, Long.box(3))
      .set(
        OptimizerConfigOptions.TABLE_OPTIMIZER_AGG_PHASE_STRATEGY,
        AggregatePhaseStrategy.TWO_PHASE)
  }

  @Test
  def testGroupAggregate(): Unit = {
    val table = util.addTableSource[(Long, Int, String)]('a, 'b, 'c)
    val resultTable = table
      .groupBy('b)
      .select('a.count)

    util.verifyExecPlan(resultTable)
  }

  @Test
  def testGroupAggregateWithConstant1(): Unit = {
    val table = util.addTableSource[(Long, Int, String)]('a, 'b, 'c)
    val resultTable = table
      .select('a, 4.as('four), 'b)
      .groupBy('four, 'a)
      .select('four, 'b.sum)

    util.verifyExecPlan(resultTable)
  }

  @Test
  def testGroupAggregateWithConstant2(): Unit = {
    val table = util.addTableSource[(Long, Int, String)]('a, 'b, 'c)
    val resultTable = table
      .select('b, 4.as('four), 'a)
      .groupBy('b, 'four)
      .select('four, 'a.sum)

    util.verifyExecPlan(resultTable)
  }

  @Test
  def testGroupAggregateWithExpressionInSelect(): Unit = {
    val table = util.addTableSource[(Long, Int, String)]('a, 'b, 'c)
    val resultTable = table
      .select('a.as('a), ('b % 3).as('d), 'c.as('c))
      .groupBy('d)
      .select('c.min, 'a.avg)

    util.verifyExecPlan(resultTable)
  }

  @Test
  def testGroupAggregateWithFilter(): Unit = {
    val table = util.addTableSource[(Long, Int, String)]('a, 'b, 'c)
    val resultTable = table
      .groupBy('b)
      .select('b, 'a.sum)
      .where('b === 2)

    util.verifyExecPlan(resultTable)
  }

  @Test
  def testGroupAggregateWithAverage(): Unit = {
    val table = util.addTableSource[(Long, Int, String)]('a, 'b, 'c)
    val resultTable = table
      .groupBy('b)
      .select('b, 'a.cast(DataTypes.DOUBLE()).avg)

    util.verifyExecPlan(resultTable)
  }
}
