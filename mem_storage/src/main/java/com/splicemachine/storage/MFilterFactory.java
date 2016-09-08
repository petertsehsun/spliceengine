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

package com.splicemachine.storage;

import java.io.IOException;

/**
 * @author Scott Fines
 *         Date: 12/16/15
 */
public class MFilterFactory implements DataFilterFactory{
    public static final DataFilterFactory INSTANCE= new MFilterFactory();

    private MFilterFactory(){}

    @Override
    public DataFilter singleColumnEqualsValueFilter(byte[] family,byte[] qualifier,byte[] value){
        throw new UnsupportedOperationException("IMPLEMENT");
    }

    @Override
    public DataFilter allocatedFilter(byte[] localAddress){
        //TODO -sf- implement?
        return new DataFilter(){
            @Override
            public ReturnCode filterCell(DataCell keyValue) throws IOException{
                return ReturnCode.INCLUDE;
            }

            @Override
            public boolean filterRow() throws IOException{
                return false;
            }

            @Override
            public void reset() throws IOException{

            }
        };
    }
}