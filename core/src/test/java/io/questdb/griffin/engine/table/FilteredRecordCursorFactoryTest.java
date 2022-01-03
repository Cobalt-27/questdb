/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2022 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.griffin.engine.table;

import io.questdb.cairo.AbstractCairoTest;
import io.questdb.cairo.SqlJitMode;
import io.questdb.cairo.sql.RecordCursorFactory;
import io.questdb.cairo.sql.async.*;
import io.questdb.griffin.AbstractGriffinTest;
import io.questdb.mp.RingQueue;
import io.questdb.mp.SCSequence;
import io.questdb.mp.WorkerPool;
import io.questdb.mp.WorkerPoolConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;

public class FilteredRecordCursorFactoryTest extends AbstractGriffinTest {

    @BeforeClass
    public static void setUpStatic() {
        jitMode = SqlJitMode.JIT_MODE_DISABLED;
        AbstractGriffinTest.setUpStatic();
    }

    @Test
    public void testSimple() throws Exception {
        assertMemoryLeak(() -> {
            WorkerPool pool = new WorkerPool(new WorkerPoolConfiguration() {
                @Override
                public int[] getWorkerAffinity() {
                    return new int[]{ -1, -1, -1, -1};
                }

                @Override
                public int getWorkerCount() {
                    return 4;
                }

                @Override
                public boolean haltOnError() {
                    return false;
                }
            });

            pool.assign(new PageFrameDispatchJob(engine.getMessageBus(), pool.getWorkerCount()));
            for (int i = 0, n = pool.getWorkerCount(); i < n; i++) {
                pool.assign(i, new PageFrameReduceJob(
                        engine.getMessageBus(),
                        sqlExecutionContext.getRandom(),
                        pool.getWorkerCount())
                );
            }
            pool.assign(0, new PageFrameCleanupJob(engine.getMessageBus(), sqlExecutionContext.getRandom()));

            pool.start(null);

            try {
                compiler.compile("create table x as (select rnd_double() a, timestamp_sequence(20000000, 100000) t from long_sequence(20000000)) timestamp(t) partition by hour", sqlExecutionContext);
                try (RecordCursorFactory f = compiler.compile("x where a > 0.34", sqlExecutionContext).getRecordCursorFactory()) {

                    LOG.info().$("class name:").$(f.getClass().getName()).$();
                    SCSequence subSeq = new SCSequence();
                    PageFrameSequence<?> frameSequence = f.execute(sqlExecutionContext, subSeq);

                    final long frameSequenceId = frameSequence.getId();
                    final int shard = frameSequence.getShard();
                    final RingQueue<PageFrameReduceTask> queue = messageBus.getPageFrameReduceQueue(shard);
                    int frameCount = 0;

                    while (true) {
                        long cursor = subSeq.nextBully();
                        PageFrameReduceTask task = queue.get(cursor);
                        if (task.getFrameSequence().getId() == frameSequenceId) {
                            frameCount++;
                            if (frameCount == task.getFrameSequence().getFrameCount()) {
                                subSeq.done(cursor);
                                break;
                            }
                        }
                        subSeq.done(cursor);
                    }
                    frameSequence.await();
                }
            } catch (Throwable e){
                e.printStackTrace();
                throw e;
            } finally {
                pool.halt();
            }
        });
    }
}