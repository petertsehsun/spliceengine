/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.splicemachine.db.catalog.types;

import com.splicemachine.db.catalog.Statistics;
import com.splicemachine.db.iapi.services.io.Formatable;
import com.splicemachine.db.iapi.services.io.StoredFormatIds;
import com.splicemachine.db.iapi.services.io.FormatableHashtable;

import java.io.ObjectOutput;
import java.io.ObjectInput;
import java.io.IOException;

public class StatisticsImpl	implements Statistics, Formatable {
	/* total count of rows for which this statistic was created-- this
	   is not the same as the total number of rows in the conglomerate
	   currently, but a snapshot; i.e the  number of rows when this
	   statistic was created/updated.
	*/

	private long numRows;
	
	/* total count of unique values for the keys 
	 */
	private long numUnique;

	private transient long conglomerateNumber;
	private transient int columnCount;
	/**
	 * Constructor for StatisticsImpl.
	 * 
	 * @param numRows	number of rows in the conglomerate for which
	 * this statistic is being created.
	 * @param numUnique number of unique values in the key for which
	 * this statistic is being created.
	 */
	public StatisticsImpl(long numRows, long numUnique)
	{
		this.numRows = numRows;
		this.numUnique = numUnique;
	}

	/** Zero argument constructor for Formatable Interface */
	public StatisticsImpl()
	{}

	@Override public long getConglomerateId(){ return conglomerateNumber; }

	@Override public int getColumnCount(){ return columnCount; }

	/** {@inheritDoc} */
    public long getRowEstimate() {
        return numRows;
    }

	/** @see Statistics#selectivity */
	public double selectivity(Object[] predicates)
	{
		if (numRows == 0.0)
			return 0.1;

		/* xxxSTATresolve: for small values of numRows, should we do something
		 * special? 
		 */
		return (double)(1/(double)numUnique);
	}

	/*------------------ Externalizable Interface ------------------*/
	
	/**
	 * @see java.io.Externalizable#readExternal
	 */
	public void readExternal(ObjectInput in) 
		throws IOException, ClassNotFoundException
	{
		FormatableHashtable fh = (FormatableHashtable)in.readObject();
		numRows = fh.getLong("numRows");
		numUnique = fh.getLong("numUnique");
	}

	/**
	 * Write this object to a stream of stored objects.
	 *
	 * @param out write bytes here.
	 *
	 * @exception IOException		thrown on error
	 */
	public void writeExternal(ObjectOutput out)
		 throws IOException
	{
		FormatableHashtable fh = new FormatableHashtable();
		fh.putLong("numRows", numRows);
		fh.putLong("numUnique", numUnique);
		out.writeObject(fh);
	}
		
	/*------------------- Formatable Interface ------------------*/
	/**
	 * @return the format id which corresponds to this class.
	 */
	public int getTypeFormatId()
	{
		return StoredFormatIds.STATISTICS_IMPL_V01_ID;
	}

	
	/** @see java.lang.Object#toString */
	public String toString()
	{
		return "numunique= " + numUnique + " numrows= " + numRows;
	}
	
}
