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

package org.apache.flink.table.runtime.operators.window.tvf.operator;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.operators.OneInputStreamOperator;
import org.apache.flink.streaming.api.operators.TimestampedCollector;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.TimestampData;
import org.apache.flink.table.data.utils.JoinedRowData;
import org.apache.flink.table.runtime.operators.TableStreamOperator;
import org.apache.flink.table.runtime.operators.window.TimeWindow;
import org.apache.flink.table.runtime.operators.window.groupwindow.assigners.GroupWindowAssigner;

import java.time.ZoneId;
import java.util.Collection;

import static org.apache.flink.table.runtime.util.TimeWindowUtil.toEpochMills;
import static org.apache.flink.util.Preconditions.checkArgument;

/**
 * The {@link WindowTableFunctionOperatorBase} acts as a table-valued function to assign windows for
 * input row. Output row includes the original columns as well additional 3 columns named {@code
 * window_start}, {@code window_end}, {@code window_time} to indicate the assigned window.
 */
public abstract class WindowTableFunctionOperatorBase extends TableStreamOperator<RowData>
        implements OneInputStreamOperator<RowData, RowData> {

    private static final String NULL_ROW_TIME_ELEMENTS_DROPPED_METRIC_NAME =
            "numNullRowTimeRecordsDropped";

    /**
     * The shift timezone of the window, if the proctime or rowtime type is TIMESTAMP_LTZ, the shift
     * timezone is the timezone user configured in TableConfig, other cases the timezone is UTC
     * which means never shift when assigning windows.
     */
    protected final ZoneId shiftTimeZone;

    protected final int rowtimeIndex;

    protected final GroupWindowAssigner<TimeWindow> windowAssigner;

    /** This is used for emitting elements with a given timestamp. */
    private transient TimestampedCollector<RowData> collector;

    private transient JoinedRowData outRow;
    private transient GenericRowData windowProperties;

    // ------------------------------------------------------------------------
    // Metrics
    // ------------------------------------------------------------------------

    protected transient Counter numNullRowTimeRecordsDropped;

    public WindowTableFunctionOperatorBase(
            GroupWindowAssigner<TimeWindow> windowAssigner,
            int rowtimeIndex,
            ZoneId shiftTimeZone) {
        this.shiftTimeZone = shiftTimeZone;
        this.rowtimeIndex = rowtimeIndex;
        this.windowAssigner = windowAssigner;
        checkArgument(!windowAssigner.isEventTime() || rowtimeIndex >= 0);
    }

    @Override
    public void open() throws Exception {
        super.open();
        this.collector = new TimestampedCollector<>(output);
        collector.eraseTimestamp();

        outRow = new JoinedRowData();
        windowProperties = new GenericRowData(3);

        // metrics
        this.numNullRowTimeRecordsDropped =
                metrics.counter(NULL_ROW_TIME_ELEMENTS_DROPPED_METRIC_NAME);
    }

    @Override
    public void close() throws Exception {
        super.close();
        if (collector != null) {
            collector.close();
        }
    }

    protected void collect(RowData inputRow, Collection<TimeWindow> allWindows) {
        for (TimeWindow window : allWindows) {
            windowProperties.setField(0, TimestampData.fromEpochMillis(window.getStart()));
            windowProperties.setField(1, TimestampData.fromEpochMillis(window.getEnd()));
            windowProperties.setField(
                    2,
                    TimestampData.fromEpochMillis(
                            toEpochMills(window.maxTimestamp(), shiftTimeZone)));
            outRow.replace(inputRow, windowProperties);
            outRow.setRowKind(inputRow.getRowKind());
            collector.collect(outRow);
        }
    }

    @VisibleForTesting
    public Counter getNumNullRowTimeRecordsDropped() {
        return numNullRowTimeRecordsDropped;
    }
}
