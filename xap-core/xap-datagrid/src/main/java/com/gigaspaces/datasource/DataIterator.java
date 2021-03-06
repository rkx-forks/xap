/*
 * Copyright (c) 2008-2016, GigaSpaces Technologies, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.gigaspaces.datasource;

import java.util.Iterator;

/**
 * DataIterator iterates the data provided by the {@link DataProvider} <br> and the {@link
 * SQLDataProvider}<br><br>
 *
 * Data iterator is closed after use.
 *
 * @author anna
 * @since 6.0
 */
public interface DataIterator<T>
        extends Iterator<T> {

    /**
     * Clean up after any resources associated with this iterator The iterator can be closed even if
     * the iterator wasn't iterated over all of its elements.
     */
    void close();
}
