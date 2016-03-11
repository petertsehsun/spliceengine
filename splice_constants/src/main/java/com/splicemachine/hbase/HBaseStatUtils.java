package com.splicemachine.hbase;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Result;

/**
 * @author Scott Fines
 *         Date: 1/23/14
 */
public class HBaseStatUtils {

		public static void countBytes(com.splicemachine.metrics.Counter counter, Result...results){
				if(counter.isActive()){
						long bytes =0l;
						for(Result result:results){
								for(KeyValue kv:result.raw()){
										bytes+=kv.getLength();
								}
						}
						counter.add(bytes);
				}
		}

}