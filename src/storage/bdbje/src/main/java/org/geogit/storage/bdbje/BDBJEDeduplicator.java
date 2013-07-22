package org.geogit.storage.bdbje;

import java.util.Iterator;
import java.util.List;

import org.geogit.api.ObjectId;
import org.geogit.storage.Deduplicator;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

public class BDBJEDeduplicator implements Deduplicator {
    private static final DatabaseEntry DUMMY_DATA = new DatabaseEntry(new byte[0]);
    private Database objectDb;
    private final BDBJEDeduplicationService service;

    public BDBJEDeduplicator(Database database, BDBJEDeduplicationService service) {
        this.objectDb = database;
        this.service = service;
    }
    
    @Override
    public boolean isDuplicate(ObjectId id) {
        return safeTest(id);
    }

    @Override
    public void removeDuplicates(List<ObjectId> ids) {
        Iterator<ObjectId> iterator = ids.iterator();
        while (iterator.hasNext()) {
            if (safeTest(iterator.next())) {
                iterator.remove();
            }
        }
    }
    
    @Override
    public void reset() {
    	objectDb.close();
    	service.reset(this);
    }
    
    @Override
    public void release() {
        objectDb.close();
        service.deregister(this);
    }
    
    protected void setDatabase(Database db) {
    	this.objectDb = db;
    }
    
    private boolean destructiveTest(final ObjectId id) {
        OperationStatus status = objectDb.putNoOverwrite(null, asDatabaseEntry(id), DUMMY_DATA);
        return status == OperationStatus.KEYEXIST;
    }
    
    private boolean safeTest(final ObjectId id) {
        OperationStatus status = objectDb.getSearchBoth(null, asDatabaseEntry(id), DUMMY_DATA, LockMode.DEFAULT);
        return status == OperationStatus.SUCCESS;
    }
    
    private DatabaseEntry asDatabaseEntry(final ObjectId id) {
        return new DatabaseEntry(id.getRawValue());
    }

    @Override
    public boolean visit(ObjectId id) {
        return destructiveTest(id);
    }
}
