package com.gigaspaces.internal.query.explainplan.model;

import com.gigaspaces.internal.query.explainplan.IndexInfo;
import com.gigaspaces.internal.query.explainplan.QueryOperator;
import com.gigaspaces.metadata.index.SpaceIndexType;

/**
 * Single index choice detail
 *
 * @author Mishel Liberman
 * @since 16.0
 */
public class IndexInfoDetail {
    private Integer id;
    private String name;
    private Object value;
    private QueryOperator operator;
    private Integer size;
    private SpaceIndexType type;


    public IndexInfoDetail(Integer id, IndexInfo option) {
        this.id = id;
        name = option.getName();
        value = option.getValue();
        operator = option.getOperator();
        size = option.getSize();
        type = option.getType();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public QueryOperator getOperator() {
        return operator;
    }

    public void setOperator(QueryOperator operator) {
        this.operator = operator;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public SpaceIndexType getType() {
        return type;
    }

    public void setType(SpaceIndexType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return getString(true);
    }

    public String toStringNotVerbose(Integer min, Integer max) {
        if (min.equals(max)) {
            return getString(false);
        }

        return String.format("- (%s %s %s), size=[min=%s, max=%s]"
                , getName(), getOperator().getOperatorString()
                , getValue(), min, max); //TODO mishel - move to another method in my code instead of here.
    }

    private String getString(boolean verbose) {
        if (verbose) {
            return String.format("[#%s] (%s %s %s), size=%s, type=%s"
                    , getId(), getName(), getOperator().getOperatorString()
                    , getValue(), getSizeDesc(), getType());
        } else {
            return String.format("- (%s %s %s), size=%s"
                    , getName(), getOperator().getOperatorString()
                    , getValue(), getSizeDesc());
        }
    }
    private String getSizeDesc() {
        return size == null || size == -1 ? "unknown" : String.valueOf(size);
    }
}
