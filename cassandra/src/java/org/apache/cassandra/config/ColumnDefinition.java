/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.config;

import java.nio.ByteBuffer;
import java.util.*;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;

import org.apache.cassandra.cql3.*;
import org.apache.cassandra.db.*;
import org.apache.cassandra.db.marshal.*;
import org.apache.cassandra.serializers.MarshalException;
import org.apache.cassandra.exceptions.*;
import org.apache.cassandra.thrift.ColumnDef;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.cassandra.utils.FBUtilities;

import static org.apache.cassandra.utils.FBUtilities.json;

public class ColumnDefinition extends ColumnSpecification
{
    // system.schema_columns column names
    private static final String COLUMN_NAME = "column_name";
    private static final String TYPE = "validator";
    private static final String INDEX_TYPE = "index_type";
    private static final String INDEX_OPTIONS = "index_options";
    private static final String INDEX_NAME = "index_name";
    private static final String COMPONENT_INDEX = "component_index";
    private static final String KIND = "type";

    /*
     * The type of CQL3 column this definition represents.
     * There is 3 main type of CQL3 columns: those parts of the partition key,
     * those parts of the clustering key and the other, regular ones.
     * But when COMPACT STORAGE is used, there is by design only one regular
     * column, whose name is not stored in the data contrarily to the column of
     * type REGULAR. Hence the COMPACT_VALUE type to distinguish it below.
     *
     * Note that thrift/CQL2 only know about definitions of type REGULAR (and
     * the ones whose componentIndex == null).
     */
    public enum Kind
    {
        PARTITION_KEY,
        CLUSTERING_COLUMN,
        REGULAR,
        COMPACT_VALUE;

        public String serialize()
        {
            // For backward compatibility we need to special case CLUSTERING_COLUMN
            return this == CLUSTERING_COLUMN ? "clustering_key" : this.toString().toLowerCase();
        }

        public static Kind deserialize(String value)
        {
            if (value.equalsIgnoreCase("clustering_key"))
                return CLUSTERING_COLUMN;
            return Enum.valueOf(Kind.class, value.toUpperCase());
        }
    }

    public final Kind kind;

    private String indexName;
    private IndexType indexType;
    private Map<String,String> indexOptions;

    /*
     * If the column comparator is a composite type, indicates to which
     * component this definition refers to. If null, the definition refers to
     * the full column name.
     */
    private final Integer componentIndex;

    public static ColumnDefinition partitionKeyDef(CFMetaData cfm, ByteBuffer name, AbstractType<?> validator, Integer componentIndex)
    {
        return new ColumnDefinition(cfm, name, validator, componentIndex, Kind.PARTITION_KEY);
    }

    public static ColumnDefinition clusteringKeyDef(CFMetaData cfm, ByteBuffer name, AbstractType<?> validator, Integer componentIndex)
    {
        return new ColumnDefinition(cfm, name, validator, componentIndex, Kind.CLUSTERING_COLUMN);
    }

    public static ColumnDefinition regularDef(CFMetaData cfm, ByteBuffer name, AbstractType<?> validator, Integer componentIndex)
    {
        return new ColumnDefinition(cfm, name, validator, componentIndex, Kind.REGULAR);
    }

    public static ColumnDefinition compactValueDef(CFMetaData cfm, ByteBuffer name, AbstractType<?> validator)
    {
        return new ColumnDefinition(cfm, name, validator, null, Kind.COMPACT_VALUE);
    }

    public ColumnDefinition(CFMetaData cfm, ByteBuffer name, AbstractType<?> validator, Integer componentIndex, Kind kind)
    {
        this(cfm.ksName,
             cfm.cfName,
             new ColumnIdentifier(name, cfm.getComponentComparator(componentIndex, kind)),
             validator,
             null,
             null,
             null,
             componentIndex,
             kind);
    }

    @VisibleForTesting
    public ColumnDefinition(String ksName,
                            String cfName,
                            ColumnIdentifier name,
                            AbstractType<?> validator,
                            IndexType indexType,
                            Map<String, String> indexOptions,
                            String indexName,
                            Integer componentIndex,
                            Kind kind)
    {
        super(ksName, cfName, name, validator);
        assert name != null && validator != null;
        this.kind = kind;
        this.indexName = indexName;
        this.componentIndex = componentIndex;
        this.setIndexType(indexType, indexOptions);
    }

    public ColumnDefinition copy()
    {
        return new ColumnDefinition(ksName, cfName, name, type, indexType, indexOptions, indexName, componentIndex, kind);
    }

    public ColumnDefinition withNewName(ColumnIdentifier newName)
    {
        return new ColumnDefinition(ksName, cfName, newName, type, indexType, indexOptions, indexName, componentIndex, kind);
    }

    public ColumnDefinition withNewType(AbstractType<?> newType)
    {
        return new ColumnDefinition(ksName, cfName, name, newType, indexType, indexOptions, indexName, componentIndex, kind);
    }

    public boolean isOnAllComponents()
    {
        return componentIndex == null;
    }

    // The componentIndex. This never return null however for convenience sake:
    // if componentIndex == null, this return 0. So caller should first check
    // isOnAllComponents() to distinguish if that's a possibility.
    public int position()
    {
        return componentIndex == null ? 0 : componentIndex;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;

        if (!(o instanceof ColumnDefinition))
            return false;

        ColumnDefinition cd = (ColumnDefinition) o;

        return Objects.equal(ksName, cd.ksName)
            && Objects.equal(cfName, cd.cfName)
            && Objects.equal(name, cd.name)
            && Objects.equal(type, cd.type)
            && Objects.equal(kind, cd.kind)
            && Objects.equal(componentIndex, cd.componentIndex)
            && Objects.equal(indexName, cd.indexName)
            && Objects.equal(indexType, cd.indexType)
            && Objects.equal(indexOptions, cd.indexOptions);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(ksName, cfName, name, type, kind, componentIndex, indexName, indexType, indexOptions);
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                      .add("name", name)
                      .add("type", type)
                      .add("kind", kind)
                      .add("componentIndex", componentIndex)
                      .add("indexName", indexName)
                      .add("indexType", indexType)
                      .toString();
    }

    public boolean isThriftCompatible()
    {
        return kind == ColumnDefinition.Kind.REGULAR && componentIndex == null;
    }

    public static List<ColumnDef> toThrift(Map<ByteBuffer, ColumnDefinition> columns)
    {
        List<ColumnDef> thriftDefs = new ArrayList<>(columns.size());
        for (ColumnDefinition def : columns.values())
            if (def.kind == ColumnDefinition.Kind.REGULAR)
                thriftDefs.add(def.toThrift());
        return thriftDefs;
    }

    public ColumnDef toThrift()
    {
        ColumnDef cd = new ColumnDef();

        cd.setName(ByteBufferUtil.clone(name.bytes));
        cd.setValidation_class(type.toString());
        cd.setIndex_type(indexType == null ? null : org.apache.cassandra.thrift.IndexType.valueOf(indexType.name()));
        cd.setIndex_name(indexName == null ? null : indexName);
        cd.setIndex_options(indexOptions == null ? null : Maps.newHashMap(indexOptions));

        return cd;
    }

    public static ColumnDefinition fromThrift(CFMetaData cfm, ColumnDef thriftColumnDef) throws SyntaxException, ConfigurationException
    {
        // For super columns, the componentIndex is 1 because the ColumnDefinition applies to the column component.
        Integer componentIndex = cfm.isSuper() ? 1 : null;
        AbstractType<?> comparator = cfm.getComponentComparator(componentIndex, Kind.REGULAR);
        try
        {
            comparator.validate(thriftColumnDef.name);
        }
        catch (MarshalException e)
        {
            throw new ConfigurationException(String.format("Column name %s is not valid for comparator %s", ByteBufferUtil.bytesToHex(thriftColumnDef.name), comparator));
        }

        ColumnDefinition cd = new ColumnDefinition(cfm,
                                                   ByteBufferUtil.clone(thriftColumnDef.name),
                                                   TypeParser.parse(thriftColumnDef.validation_class),
                                                   componentIndex,
                                                   Kind.REGULAR);

        cd.setIndex(thriftColumnDef.index_name,
                    thriftColumnDef.index_type == null ? null : IndexType.valueOf(thriftColumnDef.index_type.name()),
                    thriftColumnDef.index_options);
        return cd;
    }

    public static Map<ByteBuffer, ColumnDefinition> fromThrift(CFMetaData cfm, List<ColumnDef> thriftDefs) throws SyntaxException, ConfigurationException
    {
        if (thriftDefs == null)
            return new HashMap<>();

        Map<ByteBuffer, ColumnDefinition> cds = new TreeMap<>();
        for (ColumnDef thriftColumnDef : thriftDefs)
        {
            ColumnDefinition def = fromThrift(cfm, thriftColumnDef);
            cds.put(def.name.bytes, def);
        }

        return cds;
    }

    /**
     * Drop specified column from the schema using given row.
     *
     * @param rm         The schema row mutation
     * @param cfName     The name of the parent ColumnFamily
     * @param timestamp  The timestamp to use for column modification
     */
    public void deleteFromSchema(RowMutation rm, long timestamp)
    {
        ColumnFamily cf = rm.addOrGet(CFMetaData.SchemaColumnsCf);
        int ldt = (int) (System.currentTimeMillis() / 1000);

        ColumnNameBuilder builder = CFMetaData.SchemaColumnsCf.getColumnNameBuilder();
        // Note: the following is necessary for backward compatibility. For CQL3, BBU.bytes(name.toString()) == name
        ByteBuffer nameBytes = ByteBufferUtil.bytes(name.toString());
        builder.add(ByteBufferUtil.bytes(cfName)).add(nameBytes);
        cf.addAtom(new RangeTombstone(builder.build(), builder.buildAsEndOfRange(), timestamp, ldt));
    }

    public void toSchema(RowMutation rm, long timestamp)
    {
        ColumnFamily cf = rm.addOrGet(CFMetaData.SchemaColumnsCf);
        int ldt = (int) (System.currentTimeMillis() / 1000);

        cf.addColumn(Column.create("", timestamp, cfName, name.toString(), ""));
        cf.addColumn(Column.create(type.toString(), timestamp, cfName, name.toString(), TYPE));
        cf.addColumn(indexType == null ? DeletedColumn.create(ldt, timestamp, cfName, name.toString(), INDEX_TYPE)
                                       : Column.create(indexType.toString(), timestamp, cfName, name.toString(), INDEX_TYPE));
        cf.addColumn(indexOptions == null ? DeletedColumn.create(ldt, timestamp, cfName, name.toString(), INDEX_OPTIONS)
                                          : Column.create(json(indexOptions), timestamp, cfName, name.toString(), INDEX_OPTIONS));
        cf.addColumn(indexName == null ? DeletedColumn.create(ldt, timestamp, cfName, name.toString(), INDEX_NAME)
                                       : Column.create(indexName, timestamp, cfName, name.toString(), INDEX_NAME));
        cf.addColumn(componentIndex == null ? DeletedColumn.create(ldt, timestamp, cfName, name.toString(), COMPONENT_INDEX)
                                            : Column.create(componentIndex, timestamp, cfName, name.toString(), COMPONENT_INDEX));
        cf.addColumn(Column.create(kind.serialize(), timestamp, cfName, name.toString(), KIND));
    }

    public ColumnDefinition apply(ColumnDefinition def)  throws ConfigurationException
    {
        assert kind == def.kind && Objects.equal(componentIndex, def.componentIndex);

        if (getIndexType() != null && def.getIndexType() != null)
        {
            // If an index is set (and not drop by this update), the validator shouldn't be change to a non-compatible one
            // (and we want true comparator compatibility, not just value one, since the validator is used by LocalPartitioner to order index rows)
            if (!def.type.isCompatibleWith(type))
                throw new ConfigurationException(String.format("Cannot modify validator to a non-order-compatible one for column %s since an index is set", name));

            assert getIndexName() != null;
            if (!getIndexName().equals(def.getIndexName()))
                throw new ConfigurationException("Cannot modify index name");
        }

        return new ColumnDefinition(ksName,
                                    cfName,
                                    name,
                                    def.type,
                                    def.getIndexType(),
                                    def.getIndexOptions(),
                                    def.getIndexName(),
                                    componentIndex,
                                    kind);
    }

    /**
     * Deserialize columns from storage-level representation
     *
     * @param serializedColumns storage-level partition containing the column definitions
     * @return the list of processed ColumnDefinitions
     */
    public static List<ColumnDefinition> fromSchema(Row serializedColumns, CFMetaData cfm)
    {
        List<ColumnDefinition> cds = new ArrayList<>();

        String query = String.format("SELECT * FROM %s.%s", Keyspace.SYSTEM_KS, SystemKeyspace.SCHEMA_COLUMNS_CF);
        for (UntypedResultSet.Row row : QueryProcessor.resultify(query, serializedColumns))
        {
            Kind kind = row.has(KIND)
                      ? Kind.deserialize(row.getString(KIND))
                      : Kind.REGULAR;

            Integer componentIndex = null;
            if (row.has(COMPONENT_INDEX))
                componentIndex = row.getInt(COMPONENT_INDEX);
            else if (kind == Kind.CLUSTERING_COLUMN && cfm.isSuper())
                componentIndex = 1; // A ColumnDefinition for super columns applies to the column component

            // Note: we save the column name as string, but we should not assume that it is an UTF8 name, we
            // we need to use the comparator fromString method
            AbstractType<?> comparator = cfm.getComponentComparator(componentIndex, kind);
            ColumnIdentifier name = new ColumnIdentifier(comparator.fromString(row.getString(COLUMN_NAME)), comparator);

            AbstractType<?> validator;
            try
            {
                validator = TypeParser.parse(row.getString(TYPE));
            }
            catch (RequestValidationException e)
            {
                throw new RuntimeException(e);
            }

            IndexType indexType = null;
            if (row.has(INDEX_TYPE))
                indexType = IndexType.valueOf(row.getString(INDEX_TYPE));

            Map<String, String> indexOptions = null;
            if (row.has(INDEX_OPTIONS))
                indexOptions = FBUtilities.fromJsonMap(row.getString(INDEX_OPTIONS));

            String indexName = null;
            if (row.has(INDEX_NAME))
                indexName = row.getString(INDEX_NAME);

            cds.add(new ColumnDefinition(cfm.ksName, cfm.cfName, name, validator, indexType, indexOptions, indexName, componentIndex, kind));
        }

        return cds;
    }

    public String getIndexName()
    {
        return indexName;
    }

    public ColumnDefinition setIndexName(String indexName)
    {
        this.indexName = indexName;
        return this;
    }

    public ColumnDefinition setIndexType(IndexType indexType, Map<String,String> indexOptions)
    {
        this.indexType = indexType;
        this.indexOptions = indexOptions;
        return this;
    }

    public ColumnDefinition setIndex(String indexName, IndexType indexType, Map<String,String> indexOptions)
    {
        return setIndexName(indexName).setIndexType(indexType, indexOptions);
    }

    public boolean isIndexed()
    {
        return indexType != null;
    }

    public IndexType getIndexType()
    {
        return indexType;
    }

    public Map<String,String> getIndexOptions()
    {
        return indexOptions;
    }
}
