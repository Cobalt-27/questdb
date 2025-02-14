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

import io.questdb.cairo.ColumnType;
import io.questdb.cairo.GenericRecordMetadata;
import io.questdb.cairo.TableColumnMetadata;
import io.questdb.cairo.sql.Record;
import io.questdb.cairo.sql.RecordCursor;
import io.questdb.cairo.sql.RecordCursorFactory;
import io.questdb.cairo.sql.RecordMetadata;
import io.questdb.griffin.SqlExecutionContext;
import io.questdb.std.Files;
import io.questdb.std.FilesFacade;
import io.questdb.std.str.Path;
import io.questdb.std.str.StringSink;

public class TableListRecordCursorFactory implements RecordCursorFactory {

    public static final String TABLE_NAME_COLUMN = "table";
    private static final RecordMetadata METADATA;

    private final FilesFacade ff;
    private final TableListRecordCursor cursor;
    private Path path;

    public TableListRecordCursorFactory(FilesFacade ff, CharSequence dbRoot) {
        this.ff = ff;
        path = new Path().of(dbRoot).$();
        cursor = new TableListRecordCursor();
    }

    @Override
    public void close() {
        if (null != path) {
            path.close();
            path = null;
        }
    }

    @Override
    public RecordCursor getCursor(SqlExecutionContext executionContext) {
        return cursor.of();
    }

    @Override
    public RecordMetadata getMetadata() {
        return METADATA;
    }

    @Override
    public boolean recordCursorSupportsRandomAccess() {
        return false;
    }

    private class TableListRecordCursor implements RecordCursor {
        private final StringSink sink = new StringSink();
        private final TableListRecord record = new TableListRecord();
        private long findPtr = 0;

        @Override
        public void close() {
            if (findPtr > 0) {
                ff.findClose(findPtr);
                findPtr = 0;
            }
        }

        @Override
        public Record getRecord() {
            return record;
        }

        @Override
        public boolean hasNext() {
            while (true) {
                if (findPtr == 0) {
                    findPtr = ff.findFirst(path);
                    if (findPtr <= 0) {
                        return false;
                    }
                } else {
                    if (ff.findNext(findPtr) <= 0) {
                        return false;
                    }
                }
                if (Files.isDir(ff.findName(findPtr), ff.findType(findPtr), sink)) {
                    return true;
                }
            }
        }

        @Override
        public Record getRecordB() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordAt(Record record, long atRowId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void toTop() {
            close();
        }

        @Override
        public long size() {
            return -1;
        }

        private TableListRecordCursor of() {
            toTop();
            return this;
        }

        public class TableListRecord implements Record {
            @Override
            public CharSequence getStr(int col) {
                if (col == 0) {
                    return sink;
                }
                return null;
            }

            @Override
            public CharSequence getStrB(int col) {
                return getStr(col);
            }

            @Override
            public int getStrLen(int col) {
                return getStr(col).length();
            }
        }
    }

    static {
        final GenericRecordMetadata metadata = new GenericRecordMetadata();
        metadata.add(new TableColumnMetadata(TABLE_NAME_COLUMN, 1, ColumnType.STRING));
        METADATA = metadata;
    }
}
