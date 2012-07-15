package me.prettyprint.cassandra.locking;

import java.util.Arrays;

import me.prettyprint.cassandra.service.AbstractCluster;
import me.prettyprint.cassandra.service.CassandraHostConfigurator;
import me.prettyprint.cassandra.service.ThriftKsDef;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.ddl.ColumnFamilyDefinition;
import me.prettyprint.hector.api.ddl.ComparatorType;
import me.prettyprint.hector.api.ddl.KeyspaceDefinition;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.locking.HLockManager;
import me.prettyprint.hector.api.locking.HLockManagerConfigurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractLockManager implements HLockManager {

	private static final Logger log = LoggerFactory.getLogger(AbstractLockManager.class);

	protected Cluster cluster;
	protected Keyspace keyspace;
	protected HLockManagerConfigurator lockManagerConfigurator;

	public AbstractLockManager(Cluster cluster, Keyspace keyspace, HLockManagerConfigurator lockManagerConfigurator) {
		if (cluster == null)
			throw new RuntimeException("Cluster cannot be null for LockManager");

		this.cluster = cluster;

		if (lockManagerConfigurator == null) {
			this.lockManagerConfigurator = new HLockManagerConfigurator();
		}

		if (keyspace == null) {
			this.keyspace = HFactory.createKeyspace(lockManagerConfigurator.getKeyspaceName(), cluster);
		} else {
			// Set the Keyspace name in order to keep the info consistent
			lockManagerConfigurator.setKeyspaceName(keyspace.getKeyspaceName());
		}

	}

	public AbstractLockManager(Cluster cluster) {
		this(cluster, null, null);
	}

	public AbstractLockManager(Cluster cluster, Keyspace keyspace) {
		this(cluster, keyspace, null);
	}

	@Override
	public void init() {
		checkCreateLockSchema();
	}

	private void checkCreateLockSchema() {

		KeyspaceDefinition keyspaceDef = cluster.describeKeyspace(keyspace.getKeyspaceName());

		if (keyspaceDef == null) {

			ColumnFamilyDefinition cfDef = createColumnFamilyDefinition();

			KeyspaceDefinition newKeyspace = HFactory.createKeyspaceDefinition(keyspace.getKeyspaceName(),
					ThriftKsDef.DEF_STRATEGY_CLASS,
					lockManagerConfigurator.getReplicationFactor(),
					Arrays.asList(cfDef));

			log.info("Creating Keyspace and Column Family for LockManager with name (KSPS/CF): (" + newKeyspace.getName() + " / " + cfDef.getName());
			cluster.addKeyspace(newKeyspace, true);
		} else {
			log.info("Keyspace for LockManager already exists. Skipping creation.");

			// The Keyspace exists but we don't know anything about the CF yet.
			if (!doesLockCFExist(keyspaceDef)) {
				// create it
				ColumnFamilyDefinition cfDef = createColumnFamilyDefinition();
				log.info("Creating Column Family for LockManager with name: " + cfDef.getName());
				cluster.addColumnFamily(cfDef, true);
			} else {
				log.info("Column Family for LockManager already exists. Skipping creation.");
			}
		}

	}

	private ColumnFamilyDefinition createColumnFamilyDefinition() {
		return HFactory.createColumnFamilyDefinition(keyspace.getKeyspaceName(),
				lockManagerConfigurator.getLockManagerCF(),
				ComparatorType.BYTESTYPE);
	}

	private boolean doesLockCFExist(KeyspaceDefinition keyspaceDef) {
		for (ColumnFamilyDefinition cfdef : keyspaceDef.getCfDefs()) {
			if (cfdef.getName().equals(lockManagerConfigurator.getLockManagerCF())) {
				return true;
			}
		}
		return false;
	}

	private CassandraHostConfigurator getConfigurator() {
		return ((AbstractCluster) cluster).getConfigurator();
	}

	@Override
	public Cluster getCluster() {
		return cluster;
	}

	public void setCluster(Cluster cluster) {
		this.cluster = cluster;
	}

	@Override
	public Keyspace getKeyspace() {
		return keyspace;
	}

	public void setKeyspace(Keyspace keyspace) {
		this.keyspace = keyspace;
	}

	@Override
	public HLockManagerConfigurator getLockManagerConfigurator() {
		return lockManagerConfigurator;
	}

	public void setLockManagerConfigurator(HLockManagerConfigurator lockManagerConfigurator) {
		this.lockManagerConfigurator = lockManagerConfigurator;
	}

}
