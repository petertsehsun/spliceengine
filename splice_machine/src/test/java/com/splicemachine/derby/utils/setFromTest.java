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

package com.splicemachine.derby.utils;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.io.StoredFormatIds;
import com.splicemachine.db.iapi.services.monitor.ModuleFactory;
import com.splicemachine.db.iapi.services.monitor.Monitor;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.db.iapi.types.J2SEDataValueFactory;
import com.splicemachine.db.iapi.types.StringDataValue;
import com.splicemachine.si.testenv.ArchitectureIndependent;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.*;

import com.splicemachine.db.iapi.types.SQLDate;
import org.junit.experimental.categories.Category;

/**
* Created by yifu on 6/24/14.
*/
@Category(ArchitectureIndependent.class)
public class setFromTest {
    protected static J2SEDataValueFactory dvf = new J2SEDataValueFactory();

    @BeforeClass
    public static void startup() throws StandardException {
        ModuleFactory monitor = Monitor.getMonitorLite();
        Monitor.setMonitor(monitor);
        monitor.setLocale(new Properties(), Locale.getDefault().toString());
        dvf.boot(false, new Properties());
    }
    /*
    As the setFrom method is set as protected in derby, we here use the setValue public method which is what setFrom
     really invokes in its execution
     */
    @Test
    public void testSetFromMethod()throws Exception{
        Timestamp ts;
        DataValueDescriptor des;
        SQLDate test;
        for(int i=0;i<50000;i++){  // reduce from 100000 since we don't go that high yet on dates
            test = new SQLDate();
            Calendar c = Calendar.getInstance();
                c.add(Calendar.DAY_OF_YEAR,i);
                ts = new Timestamp(c.getTime().getTime());
                des = dvf.getNull(StoredFormatIds.SQL_DATE_ID, StringDataValue.COLLATION_TYPE_UCS_BASIC);
                des.setValue(ts);
                if(des!=null) {
                    test.setValue(des);
                }
            /*
            Assert timestamp and the SQLdate equals, proving that setValue method always return a value
             */
            Assert.assertNotNull(test);
          Assert.assertEquals((new Date(ts.getTime())).toString(),test.toString());
        }

    }


}
