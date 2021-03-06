/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.derby.stream.function;

import org.spark_project.guava.base.Function;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.stream.iapi.OperationContext;
import javax.annotation.Nullable;
import java.util.Iterator;

/**
 * Created by dgomezferro on 4/4/14.
 */
public abstract class SpliceFlatMapFunction<Op extends SpliceOperation, From, To>
		extends AbstractSpliceFunction<Op> implements ExternalizableFlatMapFunction<From, To>, Function<From,Iterator<To>> {
	public SpliceFlatMapFunction() {
	}

	public SpliceFlatMapFunction(OperationContext<Op> operationContext) {
        super(operationContext);
	}

    @Nullable
    @Override
    public Iterator<To> apply(@Nullable From  from) {
        try {
            return call(from);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}