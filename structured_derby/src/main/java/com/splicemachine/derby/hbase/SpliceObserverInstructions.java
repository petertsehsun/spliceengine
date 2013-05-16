package com.splicemachine.derby.hbase;

import com.google.common.collect.Maps;
import com.splicemachine.derby.iapi.sql.execute.OperationResultSet;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.impl.store.access.SpliceTransaction;
import com.splicemachine.derby.impl.store.access.SpliceTransactionManager;
import com.splicemachine.derby.impl.store.access.SpliceTransactionManagerContext;
import com.splicemachine.derby.utils.SerializingExecRow;
import com.splicemachine.derby.utils.SerializingIndexRow;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.services.io.ArrayUtil;
import org.apache.derby.iapi.sql.Activation;
import org.apache.derby.iapi.sql.ParameterValueSet;
import org.apache.derby.iapi.sql.ResultSet;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.sql.conn.StatementContext;
import org.apache.derby.iapi.sql.execute.ExecIndexRow;
import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.derby.iapi.store.access.AccessFactoryGlobals;
import org.apache.derby.iapi.store.access.Qualifier;
import org.apache.derby.iapi.types.DataValueDescriptor;
import org.apache.derby.iapi.types.DataValueFactory;
import org.apache.derby.impl.sql.GenericActivationHolder;
import org.apache.derby.impl.sql.GenericStorablePreparedStatement;
import org.apache.log4j.Logger;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
/**
 * 
 * Class utilized to serialize the Splice Operation onto the scan for hbase.  It attaches the 
 * GenericStorablePreparedStatement and the Top Operation.
 * 
 * @author johnleach
 *
 */
public class SpliceObserverInstructions implements Externalizable {
	private static final long serialVersionUID = 4l;
	private static Logger LOG = Logger.getLogger(SpliceObserverInstructions.class);
	protected GenericStorablePreparedStatement statement;
	protected SpliceOperation topOperation;
    private ActivationContext activationContext;

    // propagate transactionId to all co-processors running operations for this SQL statement
    private String transactionId;

	public SpliceObserverInstructions() {
		super();
		SpliceLogUtils.trace(LOG, "instantiated");
	}

	public SpliceObserverInstructions(GenericStorablePreparedStatement statement,  SpliceOperation topOperation,
                                      ActivationContext activationContext, String transactionId ) {
		SpliceLogUtils.trace(LOG, "instantiated with statement " + statement);
		this.statement = statement;
		this.topOperation = topOperation;
        this.activationContext = activationContext;
        this.transactionId = transactionId;
	}

    public String getTransactionId() {
        return transactionId;
    }

    @Override
	public void readExternal(ObjectInput in) throws IOException,ClassNotFoundException {
		SpliceLogUtils.trace(LOG, "readExternal");
		this.statement = (GenericStorablePreparedStatement) in.readObject();
		this.topOperation = (SpliceOperation) in.readObject();
        this.activationContext = (ActivationContext)in.readObject();
        this.transactionId = (String) in.readObject();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SpliceLogUtils.trace(LOG, "writeExternal");
		out.writeObject(statement);
		out.writeObject(topOperation);
        out.writeObject(activationContext);
        out.writeObject(transactionId);
	}
	/**
	 * Retrieve the GenericStorablePreparedStatement: This contains the byte code for the activation.
	 *
	 * @return
	 */
	public GenericStorablePreparedStatement getStatement() {
		SpliceLogUtils.trace(LOG, "getStatement " + statement);
		return statement;
	}

    public Activation getActivation(LanguageConnectionContext lcc){
        try{
            Activation activation = ((GenericActivationHolder)statement.getActivation(lcc,false)).ac;
            return activationContext.populateActivation(activation,statement,topOperation);
        } catch (StandardException e) {
            SpliceLogUtils.logAndThrowRuntime(LOG,e);
            return null; //never happen
        }
    }

	/**
	 * Retrieve the Top Operation: This can recurse to creat the operational stack for hbase for the scan.
	 *
	 * @return
	 */
	public SpliceOperation getTopOperation() {
		SpliceLogUtils.trace(LOG, "getTopOperation " + topOperation);
		return topOperation;
    }

    public static SpliceObserverInstructions create(Activation activation,
                                                    SpliceOperation topOperation) {
        ActivationContext activationContext = ActivationContext.create(activation,topOperation);
        final String transactionID = getTransactionId(activation);

        return new SpliceObserverInstructions(
				(GenericStorablePreparedStatement) activation.getPreparedStatement(),
				topOperation,activationContext, transactionID);
	}

    private static String getTransactionId(Activation activation) {
        final LanguageConnectionContext languageConnectionContext = activation.getLanguageConnectionContext();
        return getTransactionId(languageConnectionContext);
    }

    public static String getTransactionId(LanguageConnectionContext languageConnectionContext) {
        final ContextManager contextManager = languageConnectionContext.getContextManager();
        SpliceTransactionManagerContext stmc = (SpliceTransactionManagerContext) contextManager.getContext(AccessFactoryGlobals.RAMXACT_CONTEXT_ID);
        final SpliceTransactionManager transactionManager = stmc.getTransactionManager();
        final SpliceTransaction transaction = (SpliceTransaction) transactionManager.getRawStoreXact();
        return transaction.getTransactionId().getTransactionIdString();
    }

    /*
     * Serializer class for Activation objects, to ensure that they are properly sent over the wire
     * while retaining their state completely.
     *
     * A lot of this crap is reflective Field setting, because there's currently no other way to make
     * the activation properly serialize. Someday, it would be nice to move all of this into making
     * Activation properly serializable.
     */
    private static class ActivationContext implements Externalizable{
        private static final long serialVersionUID = 2l;
        private ExecRow[] currentRows;
        private Map<String,Integer> setOps;
        private boolean statementAtomic;
        private boolean statementReadOnly;
        private String stmtText;
        private boolean stmtRollBackParentContext;
        private long stmtTimeout;
        private ParameterValueSet pvs;
        private Map<String, Serializable> storedValues;
        private Map<String, Integer> nullDvds;


        @SuppressWarnings("unused")
		public ActivationContext() { 
        	
        }

        public ActivationContext(ExecRow[] currentRows, ParameterValueSet pvs, Map<String, Integer> setOps,
                                 boolean statementAtomic, boolean statementReadOnly,
                                 String stmtText, boolean stmtRollBackParentContext, long stmtTimeout,
                                 Map<String,Serializable>storedValues,
                                 Map<String,Integer> nullDvds) {
            this.currentRows = currentRows;
            this.pvs = pvs;
            this.setOps = setOps;
            this.statementAtomic = statementAtomic;
            this.statementReadOnly = statementReadOnly;
            this.stmtText = stmtText;
            this.stmtRollBackParentContext = stmtRollBackParentContext;
            this.stmtTimeout = stmtTimeout;
            this.storedValues = storedValues;
            this.nullDvds = nullDvds;
        }

        public static ActivationContext create(Activation activation,SpliceOperation topOperation){
            List<SpliceOperation> operations = new ArrayList<SpliceOperation>();
            Map<String,Integer> setOps = new HashMap<String,Integer>(operations.size());
            topOperation.generateLeftOperationStack(operations);
            Map<String,Serializable> storedValues = new HashMap<String,Serializable>();
            Map<String,Integer> nullDvds = new HashMap<String,Integer>();
            for(Field field:activation.getClass().getDeclaredFields()){
                if(!field.isAccessible())field.setAccessible(true); //make it accessible to me
                if(isQualifierField(field.getType()))
                    continue; //don't serialize qualifiers
                try{
                    if(ResultSet.class.isAssignableFrom(field.getType())){

                        final Object fieldValue = field.get(activation);
                        SpliceOperation op;
                        if (fieldValue instanceof OperationResultSet) {
                            op = ((OperationResultSet) fieldValue).getTopOperation();
                        } else {
                            op = (SpliceOperation) fieldValue;
                        }
                        for(int i=0;i<operations.size();i++){
                            if(op == operations.get(i)){
                                setOps.put(field.getName(),i);
                                break;
                            }
                        }
                    }else if(DataValueDescriptor.class.isAssignableFrom(field.getType())){
                        Object o = field.get(activation);
                        if(o!=null){
                            DataValueDescriptor dvd = (DataValueDescriptor)o;
                            if(!dvd.isNull())
                                storedValues.put(field.getName(),dvd);
                            else
                                nullDvds.put(field.getName(),dvd.getTypeFormatId());
                        }
                    }else if(Serializable.class.isAssignableFrom(field.getType())){
                        Object o = field.get(activation);
                        if(o!=null)
                            storedValues.put(field.getName(),(Serializable)o);
                    }else if(ExecIndexRow.class.isAssignableFrom(field.getType())){
                        Object o = field.get(activation);
                        if(o!=null)
                            storedValues.put(field.getName(),new SerializingIndexRow((ExecIndexRow)o));
                    }else if(ExecRow.class.isAssignableFrom(field.getType())){
                        Object o = field.get(activation);
                        if(o!=null)
                            storedValues.put(field.getName(),new SerializingExecRow((ExecRow)o));
                    }
                } catch (IllegalAccessException e) {
                    SpliceLogUtils.logAndThrowRuntime(LOG,e);
                }
            }

		/*
		 * Serialize out any non-null result rows that are currently stored in the activation.
		 *
		 * This is necessary if you are pushing out a set of Operation to a Table inside of a Sink.
		 */
            int rowPos=1;
            Map<Integer,ExecRow> rowMap = new HashMap<Integer,ExecRow>();
            boolean shouldContinue=true;
            while(shouldContinue){
                try{
                    ExecRow row = (ExecRow)activation.getCurrentRow(rowPos);
                    if(row!=null){
                        rowMap.put(rowPos,row);
                    }
                    rowPos++;
                }catch(IndexOutOfBoundsException ie){
                    //we've reached the end of the row group in activation, so stop
                    shouldContinue=false;
                }
            }
            ExecRow[] currentRows = new ExecRow[rowPos];
            for(Integer rowPosition:rowMap.keySet()){
                ExecRow row = new SerializingExecRow(rowMap.get(rowPosition));
                if(row instanceof ExecIndexRow)
                    currentRows[rowPosition] = new SerializingIndexRow(row);
                else
                    currentRows[rowPosition] =  row;
            }
            SpliceLogUtils.trace(LOG,"serializing current rows: %s", Arrays.toString(currentRows));

        /*
         * Serialize out all the pieces of the StatementContext so that it can be recreated on the
         * other side
         */
            StatementContext context = activation.getLanguageConnectionContext().getStatementContext();
            boolean statementAtomic = context.isAtomic();
            boolean statementReadOnly = context.isForReadOnly();
            String stmtText = context.getStatementText();
            boolean stmtRollBackParentContext = true; //todo -sf- this is wrong, but okay for now
            long stmtTimeout = 0; //timeouts handled by RPC --probably wrong, but also okay for now

            ParameterValueSet pvs = activation.getParameterValueSet().getClone();
            return new ActivationContext(currentRows,pvs,setOps,statementAtomic,statementReadOnly,
                    stmtText,stmtRollBackParentContext,stmtTimeout,storedValues,nullDvds);
        }

        private static boolean isQualifierField(Class clazz) {
            if(Qualifier.class.isAssignableFrom(clazz)) return true;
            else if(clazz.isArray()){
               return isQualifierField(clazz.getComponentType());
            }else return false;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(setOps);
            out.writeBoolean(statementAtomic);
            out.writeBoolean(statementReadOnly);
            out.writeUTF(stmtText);
            out.writeBoolean(stmtRollBackParentContext);
            out.writeLong(stmtTimeout);
            out.writeBoolean(pvs!=null);
            if(pvs!=null)
                out.writeObject(pvs);
            out.writeBoolean(currentRows!=null);
            if(currentRows!=null){
                ArrayUtil.writeArray(out,currentRows);
            }
            out.writeInt(storedValues.size());
            for(String storedFieldName:storedValues.keySet()){
                out.writeUTF(storedFieldName);
                out.writeObject(storedValues.get(storedFieldName));
            }
            out.writeInt(nullDvds.size());
            for(String nullDvdFieldName:nullDvds.keySet()){
                out.writeUTF(nullDvdFieldName);
                out.writeInt(nullDvds.get(nullDvdFieldName));
            }
        }

        @SuppressWarnings("unchecked")
		@Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            this.setOps = (Map<String,Integer>)in.readObject();
            this.statementAtomic = in.readBoolean();
            this.statementReadOnly = in.readBoolean();
            this.stmtText = in.readUTF();
            this.stmtRollBackParentContext = in.readBoolean();
            this.stmtTimeout = in.readLong();
            if(in.readBoolean()){
                this.pvs = (ParameterValueSet)in.readObject();
            }
            if(in.readBoolean()){
                this.currentRows = new ExecRow[in.readInt()];
                ArrayUtil.readArrayItems(in,currentRows);
            }

            int storedValuesSize = in.readInt();
            storedValues = Maps.newHashMapWithExpectedSize(storedValuesSize);
            for(int i=0;i<storedValuesSize;i++){
                String fieldName = in.readUTF();
                Serializable s = (Serializable)in.readObject();
                storedValues.put(fieldName,s);
            }

            int nullDvdsSize = in.readInt();
            nullDvds = Maps.newHashMapWithExpectedSize(nullDvdsSize);
            for(int i=0;i<nullDvdsSize;i++){
                String fieldName = in.readUTF();
                int typeId = in.readInt();
                nullDvds.put(fieldName,typeId);
            }
        }

        public Activation populateActivation(Activation activation,GenericStorablePreparedStatement statement,SpliceOperation topOperation) throws StandardException {
		/*
		 * Set any currently populated rows back on to the activation
		 */
            try{
                if(currentRows!=null){
                    for(int i=0;i<currentRows.length;i++){
                        SerializingExecRow row = (SerializingExecRow)currentRows[i];
                        if(row!=null){
                            row.populateNulls(activation.getDataValueFactory());
                            activation.setCurrentRow(row,i);
                        }
                    }
                }

			/*
			 * Set the populated operations with their comparable operation
			 */
                List<SpliceOperation> ops = new ArrayList<SpliceOperation>();
                topOperation.generateLeftOperationStack(ops);
                for(String setField:setOps.keySet()){
                    SpliceOperation op = ops.get(setOps.get(setField));
                    Field fieldToSet = activation.getClass().getDeclaredField(setField);
                    if(!fieldToSet.isAccessible())fieldToSet.setAccessible(true);
                    fieldToSet.set(activation, op);
                }

                /*
                 * Set the stored values into the object reflectively
                 */
                Class activationClass = activation.getClass();
                DataValueFactory dvf = activation.getDataValueFactory();
                for(String storedValueFieldName:storedValues.keySet()){
                    Object o = storedValues.get(storedValueFieldName);
                    if(o instanceof SerializingExecRow){
                        ((SerializingExecRow)o).populateNulls(dvf);
                    }else if(o instanceof SerializingIndexRow){
                        ((SerializingIndexRow)o).populateNullValues(dvf);
                    }
                    Field field = activationClass.getDeclaredField(storedValueFieldName);
                    if(!field.isAccessible()){
                        field.setAccessible(true);
                        field.set(activation,o);
                        field.setAccessible(false);
                    }else
                        field.set(activation, o);
                }

                for(String nullDvdFieldName:nullDvds.keySet()){
                    DataValueDescriptor dvd = dvf.getNull(nullDvds.get(nullDvdFieldName),0);
                    Field field = activationClass.getDeclaredField(nullDvdFieldName);
                    if(!field.isAccessible()){
                        field.setAccessible(true);
                        field.set(activation,dvd);
                        field.setAccessible(false);
                    }else
                        field.set(activation,dvd);
                }

                if(pvs!=null)activation.setParameters(pvs,statement.getParameterTypes());
                /*
                 * Push the StatementContext
                 */
                activation.getLanguageConnectionContext().pushStatementContext(statementAtomic,
                        statementReadOnly,stmtText,pvs,stmtRollBackParentContext,stmtTimeout);
                return activation;
            }catch (NoSuchFieldException e) {
                SpliceLogUtils.logAndThrowRuntime(LOG, e);
            } catch (IllegalAccessException e) {
                SpliceLogUtils.logAndThrowRuntime(LOG, e);
            }
            return null;
        }
    }
}