/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2019 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package com.questdb.cairo;

import com.questdb.cairo.sql.DataFrameCursor;
import com.questdb.std.LongList;

public class IntervalFwdDataFrameCursorFactory extends AbstractDataFrameCursorFactory {
    private final IntervalFwdDataFrameCursor cursor;

    public IntervalFwdDataFrameCursorFactory(CairoEngine engine, String tableName, long tableVersion, LongList intervals) {
        super(engine, tableName, tableVersion);
        this.cursor = new IntervalFwdDataFrameCursor(intervals);
    }

    @Override
    public DataFrameCursor getCursor(CairoSecurityContext securityContext) {
        cursor.of(getReader(securityContext));
        return cursor;
    }
}
